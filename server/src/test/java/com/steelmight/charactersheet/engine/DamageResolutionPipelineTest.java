package com.steelmight.charactersheet.engine;

import com.steelmight.charactersheet.TestCharacterFactory;
import com.steelmight.charactersheet.model.DamageType;
import com.steelmight.charactersheet.model.GameCharacter;
import com.steelmight.charactersheet.model.InventoryEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M0-B core criteria + M2-A protection criteria (renamed from DamagePipelineCoreTest
 * per the M2-A test plan). Level-5 bard with medium armor: PA 10, MA 5.
 */
@SpringBootTest
class DamageResolutionPipelineTest {

    @Autowired
    private DamageResolutionPipeline pipeline;

    @Autowired
    private EffectApplicationEngine effectEngine;

    @Autowired
    private com.steelmight.charactersheet.gamedata.GameDataProvider gameData;

    private GameCharacter character;

    @BeforeEach
    void setUp() {
        character = TestCharacterFactory.level5Bard();
        character.addItem(new InventoryEntry("medium-armor", 1, 0, true));
    }

    private DamageEvent event(int value, DamageType type, String... tags) {
        return new DamageEvent(value, type, gameData.getDamageCategory(type),
                tags.length > 0 ? List.of(tags) : List.of("directAttack"));
    }

    private void applyEffect(String id, Integer stacks, Integer value) {
        effectEngine.apply(character, new EffectApplication(id, "test", stacks, value, null));
    }

    // ── M0-B core (unchanged behavior) ──

    @Nested
    class ArmorMath {
        @Test
        void physicalReducedByPA() {
            pipeline.resolve(event(20, DamageType.SLASHING), character);
            assertThat(character.getHp().getCurrent()).isEqualTo(90);
        }

        @Test
        void crushingIgnoresPA() {
            pipeline.resolve(event(20, DamageType.CRUSHING), character);
            assertThat(character.getHp().getCurrent()).isEqualTo(80);
        }

        @Test
        void magicalReducedByMA() {
            pipeline.resolve(event(20, DamageType.FIRE), character);
            assertThat(character.getHp().getCurrent()).isEqualTo(85);
        }

        @Test
        void trueDamageIgnoresAllArmor() {
            pipeline.resolve(event(20, DamageType.TRUE), character);
            assertThat(character.getHp().getCurrent()).isEqualTo(80);
        }

        @Test
        void dotTaggedSkipsArmor() {
            pipeline.resolve(event(20, DamageType.SLASHING, "dot"), character);
            assertThat(character.getHp().getCurrent()).isEqualTo(80);
        }
    }

    @Nested
    class Downed {
        @Test
        void damageExceedingHpFloorsAtZeroAndTriggersDowned() {
            character.getHp().setCurrent(10);
            var result = pipeline.resolve(event(50, DamageType.FIRE), character);

            assertThat(character.getHp().getCurrent()).isZero();
            assertThat(result.getEffectsTriggered()).contains("downed");
        }
    }

    // ── M2-A criterion 3 — the ARCHITECTURE.md showcase ──

    @Nested
    class ArchitectureShowcase {
        @Test
        void fireResistanceThenArmorThenTempHpInExactlyThreeSteps() {
            // Level-6 Nyxari (racial fire ×0.5), medium armor at L6 → MA 6, temp HP 15.
            character = TestCharacterFactory.character(6, "musician", "bard");
            character.setRaceId("nyxari");
            character.addItem(new InventoryEntry("medium-armor", 1, 0, true));
            applyEffect("temporary-hp", 1, 15);

            var result = pipeline.resolve(event(35, DamageType.FIRE), character);

            // 35 ×0.5 → 17, MA 6 → 11, temp HP absorbs 11 → 0 to HP.
            assertThat(result.getSteps()).hasSize(3);
            assertThat(result.getSteps().get(0).rule()).isEqualTo("resistance");
            assertThat(result.getSteps().get(1).rule()).isEqualTo("armor");
            assertThat(result.getSteps().get(2).rule()).isEqualTo("temp-hp");
            assertThat(character.getHp().getCurrent()).isEqualTo(100); // untouched
            assertThat(character.getHp().getTemp()).isEqualTo(4); // 15 - 11
        }
    }

    // ── M2-A criterion 4 — damage immunity ──

    @Nested
    class DamageImmunity {
        @Test
        void frozenBlocksEvenTrueDamageWithASingleStep() {
            applyEffect("frozen", 1, null);
            var result = pipeline.resolve(event(100, DamageType.TRUE), character);

            assertThat(character.getHp().getCurrent()).isEqualTo(100);
            assertThat(result.getSteps()).hasSize(1);
            assertThat(result.getSteps().get(0).rule()).isEqualTo("immunity");
            assertThat(result.getEffectsTriggered()).isEmpty();
        }

        @Test
        void petrifiedExceptsTrueDamageSoTheFullPipelineRuns() {
            applyEffect("petrified", 1, null);
            var result = pipeline.resolve(event(100, DamageType.TRUE), character);

            assertThat(character.getHp().getCurrent()).isZero();
            assertThat(result.getSteps()).noneMatch(s -> s.rule().equals("immunity"));
            assertThat(result.getEffectsTriggered()).contains("downed");
        }
    }

    // ── M2-A criterion 5 — resistance / vulnerability ──

    @Nested
    class ResistanceVulnerability {
        @Test
        void etherealHalvesPhysicalBeforeArmor() {
            applyEffect("ethereal", 1, null);
            var result = pipeline.resolve(event(20, DamageType.SLASHING), character);

            // 20 ×0.5 → 10, then PA 10 → 0 (fully absorbed by armor).
            var resStep = result.getSteps().get(0);
            assertThat(resStep.rule()).isEqualTo("resistance");
            assertThat(resStep.valueAfter()).isEqualTo(10);
        }

        @Test
        void etherealDoublesMagicalBeforeArmor() {
            applyEffect("ethereal", 1, null);
            var result = pipeline.resolve(event(20, DamageType.FIRE), character);

            var vulnStep = result.getSteps().get(0);
            assertThat(vulnStep.rule()).isEqualTo("vulnerability");
            assertThat(vulnStep.valueAfter()).isEqualTo(40);
            // 40 - MA 5 = 35 lost
            assertThat(character.getHp().getCurrent()).isEqualTo(65);
        }

        @Test
        void ignoreResistanceFlagSkipsTheRule() {
            applyEffect("ethereal", 1, null);
            var ev = new DamageEvent(20, DamageType.SLASHING,
                    gameData.getDamageCategory(DamageType.SLASHING), List.of("directAttack"), true);
            var result = pipeline.resolve(ev, character);

            assertThat(result.getSteps()).noneMatch(s -> s.rule().equals("resistance"));
            assertThat(character.getHp().getCurrent()).isEqualTo(90); // 20 - PA 10
        }
    }

    // ── M2-A criterion 6 — wounded before armor (Q07) ──

    @Nested
    class WoundedBeforeArmor {
        @Test
        void woundedRaisesDamageBeforeArmorReducesIt() {
            // PA 5 via light armor at level 5.
            character = TestCharacterFactory.level5Bard();
            character.addItem(new InventoryEntry("light-armor", 1, 0, true));
            applyEffect("wounded", 3, null);
            applyEffect("wounded", 3, null); // second independent instance

            var result = pipeline.resolve(event(10, DamageType.SLASHING), character);

            // 10 + 3 + 3 = 16, then PA 5 → 11 lost.
            assertThat(character.getHp().getCurrent()).isEqualTo(89);
            var rules = result.getSteps().stream().map(ResolutionStep::rule).toList();
            assertThat(rules.indexOf("flat-taken-modifier")).isLessThan(rules.indexOf("armor"));
        }

        @Test
        void woundedIgnoresNonDirectAttacks() {
            applyEffect("wounded", 3, null);
            var result = pipeline.resolve(event(10, DamageType.SLASHING, "dot"), character);
            assertThat(result.getSteps()).noneMatch(s -> s.rule().equals("flat-taken-modifier"));
        }
    }

    // ── M2-A criterion 7 — block ──

    @Nested
    class Block {
        @Test
        void blockNegatesWholeInstancesAndDepletes() {
            applyEffect("block", 2, 2);

            pipeline.resolve(event(20, DamageType.SLASHING), character);
            assertThat(character.getHp().getCurrent()).isEqualTo(100); // negated, 1 left

            pipeline.resolve(event(20, DamageType.FIRE), character);
            assertThat(character.getHp().getCurrent()).isEqualTo(100); // negated, removed

            assertThat(character.getActiveEffects()).noneMatch(e -> e.getEffectId().equals("block"));

            pipeline.resolve(event(20, DamageType.SLASHING), character);
            assertThat(character.getHp().getCurrent()).isEqualTo(90); // third lands (PA 10)
        }
    }

    // ── M2-B criterion 6 — post-damage triggers ──

    @Nested
    class PostDamageTriggers {
        @Test
        void sleepingCharacterWakesFromFullyAbsorbedDamage() {
            applyEffect("sleeping", 1, null);
            applyEffect("block", 1, 1);

            var result = pipeline.resolve(event(20, DamageType.SLASHING), character);

            // Armor 20→10, block negates the rest — absorbed damage still "landed" (Q04).
            assertThat(character.getHp().getCurrent()).isEqualTo(100);
            assertThat(character.getActiveEffects()).noneMatch(e -> e.getEffectId().equals("sleeping"));
            assertThat(result.getEffectsTriggered()).contains("removed:sleeping");
        }

        @Test
        void immunityStoppedDamageDoesNotWakeTheSleeper() {
            applyEffect("sleeping", 1, null);
            applyEffect("frozen", 1, null);

            var result = pipeline.resolve(event(100, DamageType.FIRE), character);

            assertThat(character.getActiveEffects()).anyMatch(e -> e.getEffectId().equals("sleeping"));
            assertThat(result.getEffectsTriggered()).isEmpty();
        }

        @Test
        void charmedRemovedOnlyWhenHarmedByItsSource() {
            effectEngine.apply(character, new EffectApplication("charmed", "vampire-lord", 1, null, null));

            // Damage without a sourceId → charm holds.
            pipeline.resolve(event(5, DamageType.TRUE), character);
            assertThat(character.getActiveEffects()).anyMatch(e -> e.getEffectId().equals("charmed"));

            // Damage attributed to the charmer → charm breaks.
            var attributed = new DamageEvent(5, DamageType.TRUE,
                    gameData.getDamageCategory(DamageType.TRUE),
                    List.of("directAttack"), false, "vampire-lord");
            var result = pipeline.resolve(attributed, character);
            assertThat(character.getActiveEffects()).noneMatch(e -> e.getEffectId().equals("charmed"));
            assertThat(result.getEffectsTriggered()).contains("removed:charmed");
        }
    }

    // ── M2-A criterion 8 — pools, magic-shield scope, cap ──

    @Nested
    class PoolsAndCap {
        @Test
        void tempHpAndEffectValueStayInSyncAfterPartialAbsorption() {
            applyEffect("temporary-hp", 1, 15);
            pipeline.resolve(event(10, DamageType.TRUE), character);

            var tempEffect = character.getActiveEffects().stream()
                    .filter(e -> e.getEffectId().equals("temporary-hp")).findFirst().orElseThrow();
            assertThat(tempEffect.getValue()).isEqualTo(5);
            assertThat(character.getHp().getTemp()).isEqualTo(5);
            assertThat(character.getHp().getCurrent()).isEqualTo(100);
        }

        @Test
        void magicShieldIgnoresPhysicalDamage() {
            applyEffect("magic-shield", 1, 20);

            pipeline.resolve(event(20, DamageType.SLASHING), character);
            assertThat(character.getHp().getCurrent()).isEqualTo(90); // PA 10, shield untouched

            var shield = character.getActiveEffects().stream()
                    .filter(e -> e.getEffectId().equals("magic-shield")).findFirst().orElseThrow();
            assertThat(shield.getValue()).isEqualTo(20);

            pipeline.resolve(event(20, DamageType.FIRE), character);
            // 20 - MA 5 = 15 absorbed by shield → 5 left, HP unchanged.
            assertThat(character.getHp().getCurrent()).isEqualTo(90);
            assertThat(shield.getValue()).isEqualTo(5);
        }

        @Test
        void damageCapCapsTheEvent() {
            applyEffect("damage-cap", 1, 5);
            var result = pipeline.resolve(event(50, DamageType.TRUE), character);

            assertThat(result.getSteps()).anyMatch(s -> s.rule().equals("damage-cap"));
            assertThat(character.getHp().getCurrent()).isEqualTo(95);
        }
    }
}
