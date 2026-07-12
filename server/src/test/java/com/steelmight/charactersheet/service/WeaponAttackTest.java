package com.steelmight.charactersheet.service;

import com.steelmight.charactersheet.dto.ApplyEffectRequest;
import com.steelmight.charactersheet.dto.WeaponAttackRequest;
import com.steelmight.charactersheet.model.*;
import com.steelmight.charactersheet.repository.CharacterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Self-describing weapon attacks (Guide 4.2/4.3 + crit-doubling ruling). */
@SpringBootTest
@Import(WeaponAttackTest.FixedRandom.class)
class WeaponAttackTest {

    @TestConfiguration
    static class FixedRandom {
        /** nextInt(bound) returns min(next, bound - 1); each die lands on next + 1. */
        static int next = 4;

        @Bean
        @Primary
        com.steelmight.charactersheet.engine.RandomSource fixedRandomSource() {
            return bound -> Math.min(next, bound - 1);
        }
    }

    @Autowired
    private CharacterService service;

    @Autowired
    private CharacterRepository repo;

    @BeforeEach
    void setUp() {
        repo.deleteAll();
        FixedRandom.next = 4;
    }

    /** Level-5 barbarian: proficiency 3. */
    private GameCharacter barbarian(Stats stats, String... equippedWeapons) {
        var c = new GameCharacter("barb");
        c.setName("barb");
        c.setLevel(5);
        c.setPathId("warrior");
        c.setClassId("barbarian");
        c.setStats(stats);
        c.setHp(new HitPoints(100, 100, 0));
        c.setMana(new ManaPool(0, 0));
        c.setAp(new ActionPoints(6, 6, 10));
        for (var id : equippedWeapons) {
            c.addItem(new InventoryEntry(id, 1, 1, true));
        }
        return repo.save(c);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> attack(String itemId) {
        var response = service.weaponAttack("barb", new WeaponAttackRequest(itemId));
        return (Map<String, Object>) response.resolution().getPayload().get("attackRoll");
    }

    // Guide 4.2 (proficient): d20 + prof + stat; damage dice + flat + stat mod
    @Test
    void proficientAttackAddsProficiencyAndStat() {
        barbarian(new Stats(16, 10, 14, 8, 10, 10, 10), "greataxe"); // STR +3, prof 3

        var response = service.weaponAttack("barb", null);
        assertThat(response.snapshot().ap().current()).isEqualTo(3); // greataxe costs 3 AP

        var payload = response.resolution().getPayload();
        @SuppressWarnings("unchecked")
        var attackRoll = (Map<String, Object>) payload.get("attackRoll");
        assertThat(attackRoll.get("roll")).isEqualTo(5);
        assertThat(attackRoll.get("bonus")).isEqualTo(6);
        assertThat(attackRoll.get("total")).isEqualTo(11);

        @SuppressWarnings("unchecked")
        var damage = (Map<String, Object>) payload.get("damage");
        assertThat(damage.get("rolls")).isEqualTo(List.of(5)); // 1d12
        assertThat(damage.get("flat")).isEqualTo(7);
        assertThat(damage.get("modifier")).isEqualTo(3);
        assertThat(damage.get("total")).isEqualTo(15);

        assertThat(payload.get("damageType")).isEqualTo("slashing");
        assertThat((List<String>) payload.get("properties")).contains("two-handed");
    }

    // Guide 4.2 (not proficient): bare d20, no stat on damage, no properties
    @Test
    void nonProficientAttackGetsNothing() {
        var c = barbarian(new Stats(16, 10, 14, 8, 10, 10, 10), "dagger"); // barbarian ∉ dagger classes
        var response = service.weaponAttack("barb", null);
        var payload = response.resolution().getPayload();

        @SuppressWarnings("unchecked")
        var attackRoll = (Map<String, Object>) payload.get("attackRoll");
        assertThat(attackRoll.get("bonus")).isEqualTo(0);
        @SuppressWarnings("unchecked")
        var damage = (Map<String, Object>) payload.get("damage");
        assertThat(damage.get("modifier")).isEqualTo(0);
        assertThat(payload).doesNotContainKey("properties");
        assertThat(response.resolution().getSteps())
                .anyMatch(s -> s.rule().equals("proficiency") && s.note().contains("Not proficient"));
    }

    /** Upgraded weapons add their per-level scaling to the flat damage. */
    @Test
    void upgradedWeaponScalesDamage() {
        var c = barbarian(new Stats(16, 10, 14, 8, 10, 10, 10));
        var axe = new InventoryEntry("greataxe", 1, 3, true); // level 3: 7 + 4×2 = 15 flat
        c.addItem(axe);
        repo.save(c);

        var response = service.weaponAttack("barb", null);
        @SuppressWarnings("unchecked")
        var damage = (Map<String, Object>) response.resolution().getPayload().get("damage");
        assertThat(damage.get("flat")).isEqualTo(15);
        assertThat(damage.get("total")).isEqualTo(23); // 5 + 15 + 3
    }

    /** Crits double the whole damage (Game Owner ruling). */
    @Test
    void criticalHitDoublesDamage() {
        barbarian(new Stats(16, 10, 14, 8, 10, 10, 10), "greataxe");
        FixedRandom.next = 19; // d20 = 20; d12 = 12

        var response = service.weaponAttack("barb", null);
        var payload = response.resolution().getPayload();
        @SuppressWarnings("unchecked")
        var attackRoll = (Map<String, Object>) payload.get("attackRoll");
        assertThat(attackRoll.get("critical")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        var damage = (Map<String, Object>) payload.get("damage");
        assertThat(damage.get("critMultiplier")).isEqualTo(2);
        assertThat(damage.get("total")).isEqualTo(44); // (12 + 7 + 3) × 2
    }

    /** Finesse weapons use the better of their listed stats. */
    @Test
    void finesseWeaponTakesTheBetterStat() {
        barbarian(new Stats(10, 16, 14, 8, 10, 10, 10), "longsword"); // DEX +3 beats STR +0

        var response = service.weaponAttack("barb", null);
        var payload = response.resolution().getPayload();
        @SuppressWarnings("unchecked")
        var attackRoll = (Map<String, Object>) payload.get("attackRoll");
        assertThat(attackRoll.get("bonus")).isEqualTo(6); // prof 3 + DEX 3
        @SuppressWarnings("unchecked")
        var damage = (Map<String, Object>) payload.get("damage");
        assertThat(damage.get("total")).isEqualTo(13); // 1d8(5) + 5 + 3
    }

    /** Dual-wielding needs an explicit weapon choice. */
    @Test
    void dualWieldRequiresPickingTheWeapon() {
        barbarian(new Stats(16, 10, 14, 8, 10, 10, 10), "hand-axe", "dagger");

        assertThatThrownBy(() -> service.weaponAttack("barb", null))
                .hasMessageContaining("specify which weapon");

        var response = service.weaponAttack("barb", new WeaponAttackRequest("dagger"));
        @SuppressWarnings("unchecked")
        var weapon = (Map<String, Object>) response.resolution().getPayload().get("weapon");
        assertThat(weapon.get("id")).isEqualTo("dagger");
    }

    /** Advantage rolls twice-take-higher; one advantage cancels one disadvantage,
     *  and single-use riders (disadvantage-next-attack) are consumed by the attack. */
    @Test
    void advantageDisadvantageAndChargedRiders() {
        barbarian(new Stats(16, 10, 14, 8, 10, 10, 10), "greataxe");

        service.applyEffect("barb", new ApplyEffectRequest(
                "sharp", 1, null, 3, "test", false, false, false, null));
        var advantaged = attack(null);
        assertThat(advantaged.get("advantage")).isEqualTo(true);
        assertThat(advantaged).containsKey("rolls");

        // sharp + disadvantage-next-attack cancel → plain single roll; the rider is spent
        service.applyEffect("barb", new ApplyEffectRequest(
                "disadvantage-next-attack", 1, null, 3, "test", false, false, false, null));
        var response = service.weaponAttack("barb", null);
        @SuppressWarnings("unchecked")
        var cancelled = (Map<String, Object>) response.resolution().getPayload().get("attackRoll");
        assertThat(cancelled).doesNotContainKeys("advantage", "disadvantage", "rolls");
        assertThat(response.resolution().getEffectsTriggered())
                .contains("consumed:disadvantage-next-attack");
    }

    /** Stacked disadvantage (Guide 4.3) is an automatic miss — no roll, no damage. */
    @Test
    void stackedDisadvantageAutoMisses() {
        barbarian(new Stats(16, 10, 14, 8, 10, 10, 10), "greataxe");
        service.applyEffect("barb", new ApplyEffectRequest(
                "blinded", 1, null, 3, "test", false, false, false, null));
        service.applyEffect("barb", new ApplyEffectRequest(
                "disadvantage-attacks", 1, null, 3, "test", false, false, false, null));

        var response = service.weaponAttack("barb", null);
        var payload = response.resolution().getPayload();
        @SuppressWarnings("unchecked")
        var attackRoll = (Map<String, Object>) payload.get("attackRoll");
        assertThat(attackRoll.get("autoMiss")).isEqualTo(true);
        assertThat(payload).doesNotContainKey("damage");
    }

    @Test
    void noWeaponAndPreventedAttacksAre400() {
        var c = barbarian(new Stats(16, 10, 14, 8, 10, 10, 10));
        assertThatThrownBy(() -> service.weaponAttack("barb", null))
                .hasMessageContaining("no weapon equipped");

        // the original instance keeps a plain (non-proxy) inventory list
        c.addItem(new InventoryEntry("greataxe", 1, 1, true));
        repo.save(c);
        service.applyEffect("barb", new ApplyEffectRequest(
                "stunned", 1, null, 1, "test", false, false, false, null));
        assertThatThrownBy(() -> service.weaponAttack("barb", null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("cannot attack while stunned");
    }
}
