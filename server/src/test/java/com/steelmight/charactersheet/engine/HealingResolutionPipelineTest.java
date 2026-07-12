package com.steelmight.charactersheet.engine;

import com.steelmight.charactersheet.TestCharacterFactory;
import com.steelmight.charactersheet.model.GameCharacter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M0-B healing core + M2-B healing modifiers (renamed from HealingPipelineCoreTest).
 * Level-5 bard (CON 12) → derived max HP 140; stack threshold 3.
 */
@SpringBootTest
class HealingResolutionPipelineTest {

    @Autowired
    private HealingResolutionPipeline pipeline;

    @Autowired
    private EffectApplicationEngine effectEngine;

    private GameCharacter character;

    @BeforeEach
    void setUp() {
        character = TestCharacterFactory.level5Bard();
    }

    private void applyEffect(String id, int stacks) {
        effectEngine.apply(character, new EffectApplication(id, "test", stacks, null, null));
    }

    // ── M0-B core ──

    @Nested
    class Core {
        @Test
        void healingAppliesFullyWhenBelowMax() {
            character.getHp().setCurrent(90);
            var result = pipeline.resolve(new HealEvent(20), character);

            assertThat(character.getHp().getCurrent()).isEqualTo(110);
            assertThat(result.getSteps()).hasSize(1);
        }

        @Test
        void overhealIsDiscardedAndNoted() {
            character.getHp().setCurrent(140);
            var result = pipeline.resolve(new HealEvent(20), character);

            assertThat(character.getHp().getCurrent()).isEqualTo(140);
            assertThat(result.getSteps().get(0).note()).contains("20 discarded");
        }

        @Test
        void healingNeverRestoresTempHp() {
            character.getHp().setCurrent(50);
            pipeline.resolve(new HealEvent(20), character);
            assertThat(character.getHp().getTemp()).isZero();
        }
    }

    // ── M2-B criterion 1 — healing modifiers ──

    @Nested
    class HealingModifiers {
        @Test
        void maimedHalvesFlooring() {
            character.getHp().setCurrent(50);
            applyEffect("maimed", 3);
            var result = pipeline.resolve(new HealEvent(21), character);

            assertThat(character.getHp().getCurrent()).isEqualTo(60); // +10
            assertThat(result.getSteps()).anyMatch(s -> s.rule().equals("healing-modifier"));
        }

        @Test
        void dormantMaimedDoesNothing() {
            character.getHp().setCurrent(50);
            applyEffect("maimed", 2); // below threshold 3 → inert
            pipeline.resolve(new HealEvent(21), character);
            assertThat(character.getHp().getCurrent()).isEqualTo(71);
        }

        @Test
        void cursedBlocksAllHealingEvenWithDecayingPresent() {
            character.getHp().setCurrent(50);
            applyEffect("cursed", 3);
            applyEffect("decaying", 3);
            var result = pipeline.resolve(new HealEvent(20), character);

            assertThat(character.getHp().getCurrent()).isEqualTo(50); // no heal, NO damage
            assertThat(result.getSteps()).anyMatch(s -> s.rule().equals("healing-blocked"));
            assertThat(result.getSteps()).noneMatch(s -> s.rule().startsWith("decaying"));
        }

        @Test
        void decayingConvertsHealingToTrueDamage() {
            character.getHp().setCurrent(50);
            applyEffect("decaying", 3);
            var result = pipeline.resolve(new HealEvent(20), character);

            assertThat(character.getHp().getCurrent()).isEqualTo(30); // -20 true
            assertThat(result.getSteps()).anyMatch(s -> s.rule().equals("decaying"));
            assertThat(result.getSteps()).anyMatch(s -> s.rule().equals("decaying:hp-reduction"));
        }

        @Test
        void decayingDamageCanDownTheCharacter() {
            character.getHp().setCurrent(10);
            applyEffect("decaying", 3);
            var result = pipeline.resolve(new HealEvent(20), character);

            assertThat(character.getHp().getCurrent()).isZero();
            assertThat(result.getEffectsTriggered()).contains("downed");
        }

        @Test
        void maimedThenDecayingConvertsTheHalvedValue() {
            character.getHp().setCurrent(50);
            applyEffect("maimed", 3);
            applyEffect("decaying", 3);
            pipeline.resolve(new HealEvent(21), character);

            // 21 halved → 10, converted → 10 true damage.
            assertThat(character.getHp().getCurrent()).isEqualTo(40);
        }
    }
}
