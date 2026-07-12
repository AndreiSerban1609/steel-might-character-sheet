package com.steelmight.charactersheet.service;

import com.steelmight.charactersheet.dto.ApplyEffectRequest;
import com.steelmight.charactersheet.dto.DamageRequest;
import com.steelmight.charactersheet.dto.HealRequest;
import com.steelmight.charactersheet.dto.RestRequest;
import com.steelmight.charactersheet.dto.ReviveRequest;
import com.steelmight.charactersheet.model.*;
import com.steelmight.charactersheet.repository.CharacterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M2-D acceptance criteria. Level-5 character, WILL 14 → modifier +2 → a 2-round
 * downed window; revive DC baseline 3 + ceil(5/2) = 6.
 */
@SpringBootTest
class DeathAndDyingTest {

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
        c.setStats(new Stats(10, 10, 10, 10, 10, 14, 10)); // WILL 14 → +2
        c.setHp(new HitPoints(125, 125, 0));
        c.setMana(new ManaPool(0, 0));
        c.setAp(new ActionPoints(6, 6, 10));
        repo.save(c);
    }

    private void damage(int value) {
        service.damage("p1", new DamageRequest(value, DamageType.TRUE, null, false, null, false));
    }

    private void setWill(int will) {
        var c = repo.findById("p1").orElseThrow();
        c.setStats(new Stats(10, 10, 10, 10, 10, will, 10));
        repo.save(c);
    }

    private void applyEffect(String id) {
        service.applyEffect("p1", new ApplyEffectRequest(id, 1, null, null, "test", false, false, false, null));
    }

    // Criterion 1 — perseverance

    @Nested
    class Perseverance {
        @Test
        void holdsHpAtOneOnceThenTheNextHitDowns() {
            applyEffect("perseverance");

            var first = service.damage("p1",
                    new DamageRequest(999, DamageType.TRUE, null, false, null, false));
            assertThat(first.snapshot().hp().current()).isEqualTo(1);
            assertThat(first.snapshot().status()).isEqualTo("ALIVE");
            assertThat(first.snapshot().activeEffects())
                    .noneMatch(e -> e.id().equals("perseverance")); // consumed

            var second = service.damage("p1",
                    new DamageRequest(999, DamageType.TRUE, null, false, null, false));
            assertThat(second.snapshot().hp().current()).isZero();
            assertThat(second.snapshot().status()).isEqualTo("DOWNED");
        }
    }

    // Criterion 2 — death-resist only during the character's own turn

    @Nested
    class DeathResist {
        @Test
        void floorsAtOneDuringOwnTurnOnly() {
            applyEffect("death-resist");

            var ownTurn = service.damage("p1",
                    new DamageRequest(999, DamageType.TRUE, null, false, null, true));
            assertThat(ownTurn.snapshot().hp().current()).isEqualTo(1);
            assertThat(ownTurn.snapshot().status()).isEqualTo("ALIVE");

            var offTurn = service.damage("p1",
                    new DamageRequest(999, DamageType.TRUE, null, false, null, false));
            assertThat(offTurn.snapshot().hp().current()).isZero();
            assertThat(offTurn.snapshot().status()).isEqualTo("DOWNED");
        }
    }

    // Criterion 3 — downed countdown and outright death

    @Nested
    class DownedCountdown {
        @Test
        void willModTwoGivesTwoRoundsThenDeath() {
            damage(200);
            var snap = service.getCombatSnapshot("p1");
            assertThat(snap.status()).isEqualTo("DOWNED");
            assertThat(snap.downedRoundsRemaining()).isEqualTo(2);

            snap = service.turnEnd("p1").snapshot();
            assertThat(snap.downedRoundsRemaining()).isEqualTo(1);
            assertThat(snap.status()).isEqualTo("DOWNED");

            var last = service.turnEnd("p1");
            assertThat(last.snapshot().status()).isEqualTo("DEAD");
            assertThat(last.snapshot().pendingDeathFight()).isTrue();
            assertThat(last.snapshot().downedRoundsRemaining()).isNull();
            assertThat(last.resolution().getEffectsTriggered()).contains("death");
        }

        @Test
        void willModZeroDiesOutright() {
            setWill(10); // modifier 0 → no window (N18)
            var response = service.damage("p1",
                    new DamageRequest(200, DamageType.TRUE, null, false, null, false));

            assertThat(response.snapshot().status()).isEqualTo("DEAD");
            assertThat(response.snapshot().pendingDeathFight()).isTrue();
            assertThat(response.resolution().getEffectsTriggered()).contains("death");
        }
    }

    // Criterion 4 — Q11 no-heal, revive paths, death stacks

    @Nested
    class ReviveAndDeathStacks {
        @Test
        void healingADownedCharacterDoesNothing() {
            damage(200);
            var heal = service.heal("p1", new HealRequest(50));

            assertThat(heal.snapshot().hp().current()).isZero();
            assertThat(heal.snapshot().status()).isEqualTo("DOWNED");
            assertThat(heal.resolution().getSteps())
                    .anyMatch(s -> s.rule().equals("downed-no-heal"));
        }

        @Test
        void reviveBringsBackAtOneHp() {
            damage(200);
            var response = service.revive("p1", new ReviveRequest(null, false, false));

            assertThat(response.snapshot().status()).isEqualTo("ALIVE");
            assertThat(response.snapshot().hp().current()).isEqualTo(1);
        }

        @Test
        void criticalFailKills() {
            damage(200);
            var response = service.revive("p1", new ReviveRequest(null, false, true));

            assertThat(response.snapshot().status()).isEqualTo("DEAD");
            assertThat(response.snapshot().pendingDeathFight()).isTrue();
        }

        @Test
        void deathFightVictoryGrantsAPersistentDeathStack() {
            setWill(10);
            damage(200); // outright death
            var back = service.revive("p1", new ReviveRequest(125, true, false));

            assertThat(back.snapshot().status()).isEqualTo("ALIVE");
            assertThat(back.snapshot().deathStacks()).isEqualTo(1);
            assertThat(back.snapshot().pendingDeathFight()).isFalse();
            assertThat(back.snapshot().hp().current()).isEqualTo(125);

            // deathStacks persist through a rest (M3 tiered rest).
            var rested = service.rest("p1", new RestRequest(100));
            assertThat(rested.snapshot().deathStacks()).isEqualTo(1);
        }

        @Test
        void reviveOnAliveCharacterRejected() {
            assertThatThrownBy(() -> service.revive("p1", new ReviveRequest(null, false, false)))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("not downed or dead");
        }
    }

    // Criterion 5 — escalating revive DC, reset at combat start

    @Nested
    class ReviveDcEscalation {
        @Test
        void dcEscalatesPerDownAndResetsAtCombatStart() {
            damage(200); // first down
            assertThat(service.getCombatSnapshot("p1").reviveDC()).isEqualTo(6); // 3 + ceil(5/2)

            service.revive("p1", new ReviveRequest(50, false, false));
            damage(200); // second down, same combat
            assertThat(service.getCombatSnapshot("p1").reviveDC()).isEqualTo(8); // +2

            service.revive("p1", new ReviveRequest(50, false, false));
            service.combatStart("p1"); // counter resets
            damage(200);
            assertThat(service.getCombatSnapshot("p1").reviveDC()).isEqualTo(6);
        }

        @Test
        void reviveDcHiddenWhileAlive() {
            assertThat(service.getCombatSnapshot("p1").reviveDC()).isNull();
            assertThat(service.getCombatSnapshot("p1").status()).isEqualTo("ALIVE");
        }
    }
}
