package com.steelmight.charactersheet.engine;

import com.steelmight.charactersheet.TestCharacterFactory;
import com.steelmight.charactersheet.model.ActiveEffect;
import com.steelmight.charactersheet.model.GameCharacter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M0-A acceptance criteria, one nested group per criterion.
 * Level-5 character → stack threshold ceil(5/2) = 3.
 */
@SpringBootTest
class EffectApplicationEngineTest {

    @Autowired
    private EffectApplicationEngine engine;

    @Autowired
    private StatDerivationEngine statEngine;

    @Autowired
    private com.steelmight.charactersheet.gamedata.GameDataProvider gameData;

    private GameCharacter character;

    @BeforeEach
    void setUp() {
        character = TestCharacterFactory.level5Bard();
    }

    private EffectApplication app(String effectId, Integer stacks) {
        return new EffectApplication(effectId, "test", stacks, null, null);
    }

    private List<ActiveEffect> instances(String effectId) {
        return character.getActiveEffects().stream()
                .filter(e -> e.getEffectId().equals(effectId))
                .toList();
    }

    // Criterion 1 — multiInstance vs refresh

    @Nested
    class InstanceSemantics {
        @Test
        void burningTwiceCreatesTwoIndependentInstances() {
            engine.apply(character, new EffectApplication("burning", "torch", 2, null, 3));
            engine.apply(character, new EffectApplication("burning", "fireball", 4, null, 2));

            var burning = instances("burning");
            assertThat(burning).hasSize(2);
            assertThat(burning.get(0).getStacks()).isEqualTo(2);
            assertThat(burning.get(1).getStacks()).isEqualTo(4);
        }

        @Test
        void tauntedTwiceKeepsOneInstanceWithLatestSource() {
            engine.apply(character, new EffectApplication("taunted", "goblin", 1, null, 2));
            engine.apply(character, new EffectApplication("taunted", "ogre", 1, null, 3));

            var taunted = instances("taunted");
            assertThat(taunted).hasSize(1);
            assertThat(taunted.get(0).getSource()).isEqualTo("ogre");
            assertThat(taunted.get(0).getRemainingRounds()).isEqualTo(3);
        }
    }

    // Criterion 2 — threshold accumulation: dormant → active → consumed at turn-end

    @Nested
    class ThresholdAccumulation {
        @Test
        void dormantBelowThresholdActiveAtThresholdRevertsAfterTurnEnd() {
            // dazed halves AP recovery (6 → 3) — observable through the stat engine.
            engine.apply(character, app("dazed", 2));
            assertThat(instances("dazed").get(0).getStacks()).isEqualTo(2);
            assertThat(statEngine.computeAPRecovery(character)).isEqualTo(6); // dormant → inert

            engine.apply(character, app("dazed", 2)); // 4 total ≥ threshold 3
            assertThat(statEngine.computeAPRecovery(character)).isEqualTo(3); // active

            var result = engine.tickTurnEnd(character); // N9: consume 3 stacks
            assertThat(instances("dazed").get(0).getStacks()).isEqualTo(1);
            assertThat(statEngine.computeAPRecovery(character)).isEqualTo(6); // dormant again
            assertThat(result.getSteps()).anyMatch(s -> s.rule().equals("stack-consumption"));
        }

        @Test
        void impairedAccumulatesDormantBelowThreshold() {
            engine.apply(character, app("impaired", 2));
            var impaired = instances("impaired").get(0);
            var def = gameData.getEffect("impaired");
            assertThat(impaired.getStacks()).isEqualTo(2);
            assertThat(impaired.getRemainingRounds()).isNull();
            assertThat(EffectActivity.isActive(def, impaired, 3)).isFalse(); // dormant

            engine.apply(character, app("impaired", 1)); // 3 ≥ threshold
            assertThat(EffectActivity.isActive(def, impaired, 3)).isTrue(); // fires
        }
    }

    // Criterion 3 — 8 stacks at once: 2 active rounds, then dormant with 2 left

    @Nested
    class BulkStacks {
        @Test
        void eightStacksGiveTwoActiveRoundsThenDormantWithTwo() {
            engine.apply(character, app("dazed", 8));
            assertThat(statEngine.computeAPRecovery(character)).isEqualTo(3); // active

            engine.tickTurnEnd(character); // 8 → 5
            assertThat(instances("dazed").get(0).getStacks()).isEqualTo(5);
            assertThat(statEngine.computeAPRecovery(character)).isEqualTo(3); // still active

            engine.tickTurnEnd(character); // 5 → 2
            assertThat(instances("dazed").get(0).getStacks()).isEqualTo(2);
            assertThat(statEngine.computeAPRecovery(character)).isEqualTo(6); // dormant

            engine.tickTurnEnd(character); // dormant: nothing consumed
            assertThat(instances("dazed").get(0).getStacks()).isEqualTo(2);
        }
    }

    // Criterion 4 — player cleanse window (duringOwnTurn)

    @Nested
    class CleanseWindow {
        @Test
        void duringOwnTurnWindowSurvivesCurrentTurnEndAndExpiresNext() {
            engine.apply(character, new EffectApplication("dazed", "trap", 3, null, null, true));
            assertThat(statEngine.computeAPRecovery(character)).isEqualTo(3); // active

            engine.tickTurnEnd(character); // stacks 3→0, window 2→1 → still active
            assertThat(statEngine.computeAPRecovery(character)).isEqualTo(3);

            engine.tickTurnEnd(character); // window 1→0, stacks 0 → row removed
            assertThat(instances("dazed")).isEmpty();
            assertThat(statEngine.computeAPRecovery(character)).isEqualTo(6);
        }
    }

    // Criterion 5 — direct application bypasses accumulation

    @Nested
    class DirectApplication {
        @Test
        void explicitDurationOpensWindowWithoutStackMath() {
            engine.apply(character, app("dazed", 2)); // dormant stacks
            engine.apply(character, new EffectApplication("dazed", "spell", null, null, 1));

            var dazed = instances("dazed").get(0);
            assertThat(dazed.getStacks()).isEqualTo(2); // untouched
            assertThat(dazed.getRemainingRounds()).isEqualTo(1);
            assertThat(statEngine.computeAPRecovery(character)).isEqualTo(3); // active via window

            engine.tickTurnEnd(character); // 2 < 3: no consumption; window closes
            assertThat(instances("dazed").get(0).getStacks()).isEqualTo(2); // stacks linger
            assertThat(statEngine.computeAPRecovery(character)).isEqualTo(6); // dormant
        }
    }

    // Criterion 6 — prone increments

    @Nested
    class Prone {
        @Test
        void proneTwiceIncrementsStacks() {
            engine.apply(character, app("prone", 1));
            engine.apply(character, app("prone", 1));

            var prone = instances("prone");
            assertThat(prone).hasSize(1);
            assertThat(prone.get(0).getStacks()).isEqualTo(2);
        }
    }

    // Criterion 7 — temporary HP keep-higher + hp.temp mirror

    @Nested
    class TemporaryHp {
        @Test
        void higherValueReplacesLowerIsIgnoredMirrorTracks() {
            engine.apply(character, new EffectApplication("temporary-hp", "spell", 1, 10, null));
            assertThat(character.getHp().getTemp()).isEqualTo(10);

            engine.apply(character, new EffectApplication("temporary-hp", "spell", 1, 6, null));
            assertThat(instances("temporary-hp").get(0).getValue()).isEqualTo(10); // kept higher
            assertThat(character.getHp().getTemp()).isEqualTo(10);

            engine.apply(character, new EffectApplication("temporary-hp", "spell", 1, 15, null));
            assertThat(instances("temporary-hp").get(0).getValue()).isEqualTo(15);
            assertThat(character.getHp().getTemp()).isEqualTo(15);

            engine.remove(character, "temporary-hp");
            assertThat(character.getHp().getTemp()).isZero();
        }
    }

    // Criterion 8 — validation

    @Nested
    class Validation {
        @Test
        void unknownEffectIdRejected() {
            assertThatThrownBy(() -> engine.apply(character, app("not-a-real-effect", 1)))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("unknown effect");
        }

        @Test
        void zeroStacksRejected() {
            assertThatThrownBy(() -> engine.apply(character, app("burning", 0)))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("stacks");
        }

        @Test
        void hasValueEffectWithoutValueRejected() {
            assertThatThrownBy(() -> engine.apply(character, app("warded", 1)))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("value");
        }
    }

    // Criterion 9 — removal

    @Nested
    class Removal {
        @Test
        void removesAllInstancesOfMultiInstanceEffect() {
            engine.apply(character, new EffectApplication("burning", "a", 2, null, 3));
            engine.apply(character, new EffectApplication("burning", "b", 3, null, 2));

            var result = engine.remove(character, "burning");
            assertThat(instances("burning")).isEmpty();
            assertThat(result.getSteps()).hasSize(2);
        }

        @Test
        void removalClearsDormantStacksAndWindow() {
            engine.apply(character, app("dazed", 2));
            engine.apply(character, new EffectApplication("dazed", "spell", null, null, 2));

            engine.remove(character, "dazed");
            assertThat(instances("dazed")).isEmpty();
        }

        @Test
        void removingAbsentEffectIsIdempotent() {
            var result = engine.remove(character, "burning");
            assertThat(result.getSteps()).hasSize(1);
            assertThat(result.getSteps().get(0).note()).contains("not present");
            assertThat(character.getActiveEffects()).isEmpty();
        }
    }

    // Positive stack-counters (warded/block)

    @Nested
    class PositiveCounters {
        @Test
        void wardedReapplyAddsStacks() {
            engine.apply(character, new EffectApplication("warded", "cleric", 2, 1, null));
            engine.apply(character, new EffectApplication("warded", "cleric", 1, 1, null));

            var warded = instances("warded");
            assertThat(warded).hasSize(1);
            assertThat(warded.get(0).getStacks()).isEqualTo(3);
        }
    }

    // M2-A criterion 1 — warded negations

    @Nested
    class Warded {
        @Test
        void wardedNegatesNegativeApplicationsOneStackEachThenDepletes() {
            // (Spec example says "stacks 2" negating three applications — arithmetic is off
            // by one; the rule itself is 1 stack per negation, removed at 0. Tested with 3.)
            engine.apply(character, new EffectApplication("warded", "cleric", 3, 1, null));

            engine.apply(character, new EffectApplication("poisoned", "trap", 2, null, null));
            engine.apply(character, new EffectApplication("poisoned", "trap", 1, null, null));
            var third = engine.apply(character, new EffectApplication("burning", "torch", 2, null, 3));

            assertThat(instances("poisoned")).isEmpty(); // negated — no dormant stacks either
            assertThat(instances("burning")).isEmpty();
            assertThat(instances("warded")).isEmpty(); // depleted after the third
            assertThat(third.getSteps()).anyMatch(s -> s.rule().equals("warded"));

            engine.apply(character, new EffectApplication("taunted", "ogre", 1, null, 2));
            assertThat(instances("taunted")).hasSize(1); // fourth negative lands
        }

        @Test
        void wardedIgnoresPositiveApplications() {
            engine.apply(character, new EffectApplication("warded", "cleric", 2, 1, null));
            engine.apply(character, new EffectApplication("haste", "spell", 1, null, 3));

            assertThat(instances("haste")).hasSize(1);
            assertThat(instances("warded").get(0).getStacks()).isEqualTo(2);
        }
    }

    // M2-A criterion 2 — hard immunity beats warded

    @Nested
    class HardImmunity {
        @Test
        void frozenCharacterReceivesNoEffectsAndWardedIsNotConsumed() {
            engine.apply(character, new EffectApplication("warded", "cleric", 2, 1, null));
            // System-apply frozen (bypass, else warded would negate it).
            engine.apply(character, new EffectApplication(
                    "frozen", "spell", 1, null, 2, false, true, false));

            var result = engine.apply(character, new EffectApplication("poisoned", "trap", 3, null, null));

            assertThat(result.getSteps()).anyMatch(s -> s.rule().equals("immunity")
                    && s.note().contains("frozen"));
            assertThat(instances("poisoned")).isEmpty();
            assertThat(instances("warded").get(0).getStacks()).isEqualTo(2); // untouched
        }

        @Test
        void frozenBlocksPositiveEffectsToo() {
            engine.apply(character, new EffectApplication(
                    "frozen", "spell", 1, null, 2, false, true, false));
            engine.apply(character, new EffectApplication("haste", "spell", 1, null, 3));
            assertThat(instances("haste")).isEmpty(); // immunity to "effects" covers all
        }

        @Test
        void bypassImmunitySkipsAllProtections() {
            engine.apply(character, new EffectApplication(
                    "frozen", "spell", 1, null, 2, false, true, false));
            engine.apply(character, new EffectApplication(
                    "poisoned", "system", 3, null, null, false, true, false));
            assertThat(instances("poisoned")).hasSize(1);
        }
    }

    // M2-A — unified shield rule (Q08)

    @Nested
    class ShieldExclusivity {
        @Test
        void newShieldFromDifferentSourceIsRejected() {
            engine.apply(character, new EffectApplication("temporary-hp", "cleric-a", 1, 10, null));
            var result = engine.apply(character, new EffectApplication("block", "fighter-b", 2, 2, null));

            assertThat(result.getSteps()).anyMatch(s -> s.rule().equals("shieldExclusivity"));
            assertThat(instances("block")).isEmpty();
            assertThat(instances("temporary-hp").get(0).getValue()).isEqualTo(10);
        }

        @Test
        void replaceExistingShieldDestroysTheActiveOne() {
            engine.apply(character, new EffectApplication("temporary-hp", "cleric-a", 1, 10, null));
            engine.apply(character, new EffectApplication(
                    "block", "fighter-b", 2, 2, null, false, false, true));

            assertThat(instances("temporary-hp")).isEmpty();
            assertThat(character.getHp().getTemp()).isZero(); // mirror cleared
            assertThat(instances("block")).hasSize(1);
        }

        @Test
        void sameSourceSameAbilityRefreshesPerItsOwnRules() {
            engine.apply(character, new EffectApplication("temporary-hp", "cleric-a", 1, 10, null));
            engine.apply(character, new EffectApplication("temporary-hp", "cleric-a", 1, 15, null));

            assertThat(instances("temporary-hp").get(0).getValue()).isEqualTo(15); // keep-higher
            assertThat(character.getHp().getTemp()).isEqualTo(15);
        }

        @Test
        void nonShieldPositiveEffectsAreUnaffected() {
            engine.apply(character, new EffectApplication("temporary-hp", "cleric-a", 1, 10, null));
            engine.apply(character, new EffectApplication("damage-cap", "monk", 1, 5, null));
            assertThat(instances("damage-cap")).hasSize(1); // cap is not a shield
        }
    }

    // M2-C — composites expand in mechanic queries, without extra rows

    @Nested
    class Composites {
        @Test
        void stunnedCountsAsExposedAndPoisonedWithoutExtraRows() {
            engine.apply(character, app("stunned", 3)); // 3 ≥ threshold → active

            var advantage = ActiveMechanics.collect(character, gameData, 3, MechanicType.ADVANTAGE);
            assertThat(advantage).anyMatch(h -> h.def().id().equals("exposed")
                    && h.mechanic().on() == AdvantageTarget.INCOMING_ATTACKS);

            var disadvantage = ActiveMechanics.collect(character, gameData, 3, MechanicType.DISADVANTAGE);
            assertThat(disadvantage).anyMatch(h -> h.def().id().equals("poisoned")
                    && h.mechanic().on() == AdvantageTarget.SAVING_THROWS);

            // No exposed/poisoned rows were created — the composite expands at query time.
            assertThat(character.getActiveEffects()).hasSize(1);
        }

        @Test
        void dormantStunnedContributesNoCompositeMechanics() {
            engine.apply(character, app("stunned", 2)); // below threshold → inert
            var advantage = ActiveMechanics.collect(character, gameData, 3, MechanicType.ADVANTAGE);
            assertThat(advantage).isEmpty();
        }

        @Test
        void removingParalyzedLeavesIndependentPoisonedInPlace() {
            engine.apply(character, app("poisoned", 3));
            engine.apply(character, app("paralyzed", 3));

            engine.remove(character, "paralyzed");

            assertThat(instances("paralyzed")).isEmpty();
            assertThat(instances("poisoned")).hasSize(1); // untouched
        }
    }

    // M2-D — DEATH mechanic (exhaustion tier 6)

    @Nested
    class ExhaustionDeath {
        @Test
        void exhaustionTierSixKillsOutright() {
            engine.apply(character, app("exhaustion", 5));
            assertThat(character.getLifeStatus()).isEqualTo(com.steelmight.charactersheet.model.LifeStatus.ALIVE);

            var result = engine.apply(character, app("exhaustion", 1)); // tier 6 = death

            assertThat(character.getLifeStatus()).isEqualTo(com.steelmight.charactersheet.model.LifeStatus.DEAD);
            assertThat(character.isPendingDeathFight()).isTrue();
            assertThat(result.getSteps()).anyMatch(s -> s.rule().equals("death"));
        }
    }

    // M2-C — Corroded application ladder

    @Nested
    class Corroded {
        @Test
        void applicationsClimbTheLadderCapAtThreeAndRefreshDuration() {
            engine.apply(character, app("corroded", 1));
            engine.apply(character, app("corroded", 1));
            engine.apply(character, app("corroded", 1));

            var corroded = instances("corroded").get(0);
            assertThat(corroded.getStacks()).isEqualTo(3);
            assertThat(corroded.getRemainingRounds()).isEqualTo(1); // "1 round" from data

            var fourth = engine.apply(character, app("corroded", 1));
            assertThat(corroded.getStacks()).isEqualTo(3); // capped
            assertThat(corroded.getRemainingRounds()).isEqualTo(1); // refreshed
            assertThat(fourth.getSteps().get(0).note()).contains("duration refreshed");
        }

        @Test
        void corrodedExpiresAtTurnEndWithoutReapplication() {
            engine.apply(character, app("corroded", 3));
            engine.tickTurnEnd(character);
            assertThat(instances("corroded")).isEmpty();
        }
    }

    // Threshold derivation sanity

    @Nested
    class Threshold {
        @Test
        void thresholdIsCeilOfHalfLevel() {
            assertThat(statEngine.computeStackThreshold(character)).isEqualTo(3); // level 5
            character.setLevel(1);
            assertThat(statEngine.computeStackThreshold(character)).isEqualTo(1);
            character.setLevel(10);
            assertThat(statEngine.computeStackThreshold(character)).isEqualTo(5);
        }
    }
}
