package com.steelmight.charactersheet.service;

import com.steelmight.charactersheet.dto.DamageRequest;
import com.steelmight.charactersheet.dto.HealRequest;
import com.steelmight.charactersheet.model.*;
import com.steelmight.charactersheet.repository.CharacterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M0-B service-level criteria: conditions derivation (R4) and validation (criterion 6).
 * Seeded level-5 bard, all stats 10 → derived max HP = 25 × 5 = 125.
 */
@SpringBootTest
class DamageHealActionsTest {

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
        // WILL 14 (+2 mod) → 0 HP downs rather than kills outright (M2-D).
        c.setStats(new Stats(10, 10, 10, 10, 10, 14, 10));
        c.setHp(new HitPoints(125, 125, 0));
        c.setMana(new ManaPool(0, 0));
        c.setAp(new ActionPoints(6, 6, 10));
        repo.save(c);
    }

    private DamageRequest trueDamage(int value) {
        return new DamageRequest(value, DamageType.TRUE, null, false, null, false);
    }

    // Criterion 5 — conditions derivation

    @Test
    void conditionsDeriveFromHpThresholds() {
        // full HP → no conditions
        assertThat(service.getCombatSnapshot("p1").conditions()).isEmpty();

        // 125 → 60: below 50% (62.5) → injured
        var snap = service.damage("p1", trueDamage(65)).snapshot();
        assertThat(snap.conditions()).containsExactlyInAnyOrder("injured");

        // 60 → 10: below 10% (12.5) → injured + severelyInjured
        snap = service.damage("p1", trueDamage(50)).snapshot();
        assertThat(snap.conditions()).containsExactlyInAnyOrder("injured", "severelyInjured");

        // 10 → 0: downed as well
        snap = service.damage("p1", trueDamage(30)).snapshot();
        assertThat(snap.conditions()).containsExactlyInAnyOrder("downed", "injured", "severelyInjured");
    }

    @Test
    void downedTriggerSurfacesInTheActionResponse() {
        var response = service.damage("p1", trueDamage(200));
        assertThat(response.resolution().getEffectsTriggered()).contains("downed");
        assertThat(response.snapshot().hp().current()).isZero();
    }

    // Criterion 6 — validation

    @Test
    void nonPositiveDamageRejected() {
        assertThatThrownBy(() -> service.damage("p1", trueDamage(0)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void nonPositiveHealRejected() {
        assertThatThrownBy(() -> service.heal("p1", new HealRequest(0)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void tagsDefaultToDirectAttack() {
        // No tags supplied → treated as a direct attack; armor applies (none equipped → full damage).
        var response = service.damage("p1", new DamageRequest(10, DamageType.SLASHING, null, false, null, false));
        assertThat(response.snapshot().hp().current()).isEqualTo(115);
    }

    @Test
    void healRestoresThroughThePipeline() {
        service.damage("p1", trueDamage(65));
        var response = service.heal("p1", new HealRequest(30));
        assertThat(response.snapshot().hp().current()).isEqualTo(90);
    }
}
