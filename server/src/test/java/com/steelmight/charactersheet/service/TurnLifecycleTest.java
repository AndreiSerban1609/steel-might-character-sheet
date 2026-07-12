package com.steelmight.charactersheet.service;

import com.steelmight.charactersheet.dto.ApplyEffectRequest;
import com.steelmight.charactersheet.model.*;
import com.steelmight.charactersheet.repository.CharacterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M0-C turn lifecycle + M2-B ticks (renamed from TurnLifecycleCoreTest).
 * Level-5 character → stack threshold 3, base AP recovery 6, max HP derived 125.
 */
@SpringBootTest
class TurnLifecycleTest {

    @Autowired
    private CharacterService service;

    @Autowired
    private CharacterRepository repo;

    @BeforeEach
    void setUp() {
        repo.deleteAll();
        var c = new GameCharacter("p1");
        c.setName("Test");
        c.setLevel(5);
        c.setPathId("musician");
        c.setClassId("bard");
        c.setStats(new Stats(10, 10, 10, 10, 10, 10, 10));
        c.setHp(new HitPoints(125, 125, 0));
        c.setMana(new ManaPool(0, 0));
        c.setAp(new ActionPoints(2, 6, 10));
        c.setSpeed(30);
        repo.save(c);
    }

    private void setAp(int current) {
        var c = repo.findById("p1").orElseThrow();
        c.getAp().setCurrent(current);
        repo.save(c);
    }

    private void applyEffect(String id, Integer stacks, Integer value, Integer duration) {
        service.applyEffect("p1", new ApplyEffectRequest(id, stacks, value, duration, "test", false, false, false, null));
    }

    // ── M0-C core ──

    @Nested
    class ApRecovery {
        @Test
        void turnStartRecoversApCappedAtMax() {
            var snap = service.turnStart("p1").snapshot();
            assertThat(snap.ap().current()).isEqualTo(8);

            setAp(7);
            snap = service.turnStart("p1").snapshot();
            assertThat(snap.ap().current()).isEqualTo(10);
        }

        @Test
        void dazedHalvesRecoveryOnTurnStart() {
            applyEffect("dazed", 3, null, null);
            var snap = service.turnStart("p1").snapshot();
            assertThat(snap.ap().current()).isEqualTo(5);
        }

        @Test
        void stunnedBlocksRecoveryOnTurnStart() {
            applyEffect("stunned", 3, null, null);
            var response = service.turnStart("p1");
            assertThat(response.snapshot().ap().current()).isEqualTo(2);
        }
    }

    @Nested
    class Durations {
        @Test
        void oneRoundEffectExpiresAfterOneTurnEnd() {
            applyEffect("haste", 1, null, 1);
            var snap = service.turnEnd("p1").snapshot();
            assertThat(snap.activeEffects()).noneMatch(e -> e.id().equals("haste"));
        }

        @Test
        void untilDispelledEffectNeverExpires() {
            applyEffect("haste", 1, null, null);
            service.turnEnd("p1");
            service.turnEnd("p1");
            assertThat(service.getCombatSnapshot("p1").activeEffects())
                    .anyMatch(e -> e.id().equals("haste"));
        }
    }

    // ── M2-B criterion 2 — DoT ticks before AP recovery ──

    @Nested
    class DotTicks {
        @Test
        void burningAndEnvenomedTickIndependentlyThenApRecovers() {
            applyEffect("burning", 3, null, null);
            applyEffect("envenomed", 2, null, null);

            var response = service.turnStart("p1");

            assertThat(response.snapshot().hp().current()).isEqualTo(120); // -3 fire, -2 poison
            var rules = response.resolution().getSteps().stream().map(s -> s.rule()).toList();
            // Q13: all DoT steps resolve before AP recovery.
            int apIdx = rules.indexOf("ap-recovery");
            assertThat(apIdx).isGreaterThan(0);
            assertThat(rules.subList(0, apIdx)).anyMatch(r -> r.startsWith("burning:"));
            assertThat(rules.subList(0, apIdx)).anyMatch(r -> r.startsWith("envenomed:"));
            assertThat(rules.subList(apIdx + 1, rules.size()))
                    .noneMatch(r -> r.startsWith("burning:") || r.startsWith("envenomed:"));
            assertThat(response.snapshot().ap().current()).isEqualTo(8); // recovery still happened
        }
    }

    // ── M2-B criterion 3 — DoT routes through the full pipeline ──

    @Nested
    class DotPipelineRouting {
        @Test
        void fireResistantCharacterTakesHalvedBurning() {
            var c = repo.findById("p1").orElseThrow();
            c.setRaceId("nyxari"); // racial fire ×0.5
            repo.save(c);
            applyEffect("burning", 3, null, null);

            var response = service.turnStart("p1");

            // floor(3 × 0.5) = 1 damage; armor never applies to dots (Q06).
            assertThat(response.snapshot().hp().current()).isEqualTo(124);
            assertThat(response.resolution().getSteps())
                    .anyMatch(s -> s.rule().equals("burning:resistance"));
        }
    }

    // ── M2-B criterion 4 — HoT through the healing pipeline ──

    @Nested
    class HotTicks {
        @Test
        void regeneratingHealsAtTurnEnd() {
            var c = repo.findById("p1").orElseThrow();
            c.getHp().setCurrent(50);
            repo.save(c);
            applyEffect("regenerating", 5, 5, null);

            var snap = service.turnEnd("p1").snapshot();
            assertThat(snap.hp().current()).isEqualTo(55);
        }

        @Test
        void decayingTurnsRegenerationIntoDamage() {
            var c = repo.findById("p1").orElseThrow();
            c.getHp().setCurrent(50);
            repo.save(c);
            applyEffect("regenerating", 5, 5, null);
            applyEffect("decaying", 3, null, null);

            var response = service.turnEnd("p1");
            assertThat(response.snapshot().hp().current()).isEqualTo(45); // 5 true damage instead
            assertThat(response.resolution().getSteps())
                    .anyMatch(s -> s.rule().startsWith("regenerating:decaying"));
        }
    }

    // ── M2-B criterion 5 — suffocating → exhaustion ladder ──

    @Nested
    class Exhaustion {
        @Test
        void suffocatingAppliesExhaustionEachTurnEndAndTierTwoHalvesSpeed() {
            applyEffect("suffocating", 1, null, null);

            var snap = service.turnEnd("p1").snapshot();
            assertThat(snap.activeEffects()).anyMatch(e -> e.id().equals("exhaustion") && e.stacks() == 1);
            assertThat(snap.speed()).isEqualTo(30); // tier 1: no speed change

            snap = service.turnEnd("p1").snapshot();
            assertThat(snap.activeEffects()).anyMatch(e -> e.id().equals("exhaustion") && e.stacks() == 2);
            assertThat(snap.speed()).isEqualTo(15); // tier 2: speed halved
        }
    }
}
