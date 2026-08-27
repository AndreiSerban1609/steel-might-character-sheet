package com.steelmight.charactersheet.service;

import com.steelmight.charactersheet.dto.ApplyEffectRequest;
import com.steelmight.charactersheet.dto.CastRequest;
import com.steelmight.charactersheet.dto.MonsterTemplateRequest;
import com.steelmight.charactersheet.dto.MonsterView;
import com.steelmight.charactersheet.dto.SpawnMonstersRequest;
import com.steelmight.charactersheet.dto.UseAbilityRequest;
import com.steelmight.charactersheet.dto.WeaponAttackRequest;
import com.steelmight.charactersheet.engine.RandomSource;
import com.steelmight.charactersheet.model.AbilityScore;
import com.steelmight.charactersheet.model.ActionPoints;
import com.steelmight.charactersheet.model.DamageType;
import com.steelmight.charactersheet.model.GameCharacter;
import com.steelmight.charactersheet.model.HitPoints;
import com.steelmight.charactersheet.model.InventoryEntry;
import com.steelmight.charactersheet.model.ManaPool;
import com.steelmight.charactersheet.model.Stats;
import com.steelmight.charactersheet.repository.CharacterRepository;
import com.steelmight.charactersheet.repository.MonsterInstanceRepository;
import com.steelmight.charactersheet.repository.MonsterTemplateRepository;
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

/**
 * Story 2.3 "last mile": a named target turns printed numbers into outcomes — the attack
 * roll meets the target's real AC, saves roll on the target (E6), and damage / healing /
 * effects land through the TARGET's pipeline, attributed to the attacker.
 */
@SpringBootTest
@Import(ResolveOntoTargetTest.FixedRandom.class)
class ResolveOntoTargetTest {

    @TestConfiguration
    static class FixedRandom {
        /** nextInt(bound) returns min(next, bound - 1); each die lands on next + 1 (capped by its faces). */
        static int next = 4;

        @Bean
        @Primary
        RandomSource fixedRandomSource() {
            return bound -> Math.min(next, bound - 1);
        }
    }

    private static final String ROOM = "rot-room";

    @Autowired private CharacterService service;
    @Autowired private MonsterService monsters;
    @Autowired private CharacterRepository characterRepo;
    @Autowired private MonsterTemplateRepository templateRepo;
    @Autowired private MonsterInstanceRepository instanceRepo;

    @BeforeEach
    void setUp() {
        instanceRepo.deleteAll();
        templateRepo.deleteAll();
        characterRepo.deleteAll();
        FixedRandom.next = 4;
    }

    /** Level-5 barbarian, STR 16 (+3), proficiency 3, greataxe (3 AP, 1d12 + 7 + STR). */
    private GameCharacter barbarian() {
        var c = new GameCharacter("barb");
        c.setName("Barb");
        c.setRoomName(ROOM);
        c.setLevel(5);
        c.setPathId("warrior");
        c.setClassId("barbarian");
        c.setStats(new Stats(16, 10, 14, 8, 10, 10, 10));
        c.setHp(new HitPoints(100, 100, 0));
        c.setMana(new ManaPool(0, 0));
        c.setAp(new ActionPoints(6, 6, 10));
        c.addItem(new InventoryEntry("greataxe", 1, 1, true));
        return characterRepo.save(c);
    }

    /** Level-5 sorcerer, INT 16 (+3): magic-bolt (attack, pure), nether-zone (DEX save, shadow). */
    private GameCharacter sorcerer() {
        var c = new GameCharacter("sorc");
        c.setName("Sorc");
        c.setRoomName(ROOM);
        c.setLevel(5);
        c.setPathId("wizard");
        c.setClassId("sorcerer");
        c.setStats(new Stats(10, 10, 10, 16, 10, 14, 10));
        c.setHp(new HitPoints(100, 100, 0));
        c.setMana(new ManaPool(100, 100));
        c.setAp(new ActionPoints(6, 6, 10));
        c.getKnownSpells().addAll(List.of("magic-bolt", "nether-zone"));
        return characterRepo.save(c);
    }

    /** Level-5 assassin: unseen-blade-scar is class-granted, free, and maims for ceil(5/2) = 3 stacks. */
    private GameCharacter assassin() {
        var c = new GameCharacter("sassin");
        c.setName("Sassin");
        c.setRoomName(ROOM);
        c.setLevel(5);
        c.setPathId("rogue");
        c.setClassId("assassin");
        c.setStats(new Stats(10, 16, 10, 10, 10, 10, 10));
        c.setHp(new HitPoints(100, 100, 0));
        c.setMana(new ManaPool(0, 0));
        c.setAp(new ActionPoints(6, 6, 10));
        return characterRepo.save(c);
    }

    /**
     * Level-5 exorcist, WILL 16 (+3): spear-of-illumination (STR save, light, flagged
     * halfDamageOnSave — "Half damage on success, no push").
     */
    private GameCharacter exorcist() {
        var c = new GameCharacter("exo");
        c.setName("Exo");
        c.setRoomName(ROOM);
        c.setLevel(5);
        c.setPathId("disciple");
        c.setClassId("exorcist");
        c.setStats(new Stats(10, 10, 10, 10, 10, 16, 10));
        c.setHp(new HitPoints(100, 100, 0));
        c.setMana(new ManaPool(100, 100));
        c.setAp(new ActionPoints(6, 6, 10));
        c.getKnownSpells().add("spear-of-illumination");
        return characterRepo.save(c);
    }

    /** Level 4, 40 HP, AC 13, PA 2, MA 0. DEX as given (+10 at 30 makes any save succeed). */
    private MonsterView goblin(int dex) {
        return goblin(Map.of(AbilityScore.DEX, dex, AbilityScore.WILL, 8));
    }

    /** Same goblin with any stat block (a +10 stat makes that save succeed against any level-5 DC). */
    private MonsterView goblin(Map<AbilityScore, Integer> stats) {
        var t = monsters.createTemplate(ROOM, new MonsterTemplateRequest("Goblin", 4, 40, 13, 2, 0, 30, 3, 0,
                stats, List.of(), Map.of(), "Bites.", null));
        return monsters.spawn(ROOM, new SpawnMonstersRequest(t.id(), 1)).get(0);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o) {
        return (Map<String, Object>) o;
    }

    // ---- Weapon attacks ----

    @Test
    void weaponAttackMissesAgainstTheTargetsRealAc() {
        barbarian();
        var g = goblin(14);
        FixedRandom.next = 4; // d20 → 5, + 6 = 11 < AC 13

        var r = service.weaponAttack("barb", new WeaponAttackRequest(null, g.combatantId()));
        var roll = map(r.resolution().getPayload().get("attackRoll"));
        assertThat(roll.get("targetAC")).isEqualTo(13);
        assertThat(roll.get("hit")).isEqualTo(false);
        assertThat(r.resolution().getSteps()).anySatisfy(s -> {
            assertThat(s.rule()).isEqualTo("attack-roll");
            assertThat(s.note()).contains("vs Goblin's AC 13 — MISS");
        });
        assertThat(monsters.get(ROOM, g.id()).hp().current()).isEqualTo(40);
        assertThat(r.snapshot().ap().current()).isEqualTo(3); // the swing still cost AP
    }

    @Test
    void weaponAttackHitLandsThroughTheTargetsPipeline() {
        barbarian();
        var g = goblin(14);
        FixedRandom.next = 9; // d20 → 10, + 6 = 16 ≥ 13; d12 → 10

        var r = service.weaponAttack("barb", new WeaponAttackRequest(null, g.combatantId()));
        assertThat(map(r.resolution().getPayload().get("attackRoll")).get("hit")).isEqualTo(true);
        // 10 (die) + 7 (flat) + 3 (STR) = 20 slashing → PA 2 → 18 HP lost.
        assertThat(map(r.resolution().getPayload().get("damage")).get("total")).isEqualTo(20);
        var target = map(r.resolution().getPayload().get("target"));
        assertThat(target.get("name")).isEqualTo("Goblin");
        assertThat(target.get("hpAfter")).isEqualTo(22);
        assertThat(r.resolution().getSteps()).anySatisfy(s -> assertThat(s.rule()).isEqualTo("Goblin:armor"));
        assertThat(monsters.get(ROOM, g.id()).hp().current()).isEqualTo(22);
    }

    @Test
    void criticalWeaponAttackAlwaysHitsAndCanKill() {
        barbarian();
        var g = goblin(14);
        FixedRandom.next = 19; // natural 20; d12 → 12: (12 + 7 + 3) × 2 = 44 → PA 2 → 42 ≥ 40 HP

        var r = service.weaponAttack("barb", new WeaponAttackRequest(null, g.combatantId()));
        var target = map(r.resolution().getPayload().get("target"));
        assertThat(target.get("hpAfter")).isEqualTo(0);
        assertThat(target.get("status")).isEqualTo("DEAD");
        assertThat(r.resolution().getEffectsTriggered()).contains("death");
    }

    @Test
    void tauntRefusesTheAttackBeforeAnythingIsSpent() {
        barbarian();
        var g1 = goblin(14);
        var g2 = goblin(14);
        service.applyEffect("barb",
                new ApplyEffectRequest("taunted", 1, null, 3, g2.combatantId(), false, false, false, null));

        // (each goblin() call makes its own template, so both instances are plain "Goblin")
        assertThatThrownBy(() -> service.weaponAttack("barb", new WeaponAttackRequest(null, g1.combatantId())))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Barb is taunted by Goblin");
        assertThat(service.getCombatSnapshot("barb").ap().current()).isEqualTo(6);
        assertThat(monsters.get(ROOM, g1.id()).hp().current()).isEqualTo(40);
    }

    // ---- Spells ----

    @Test
    void spellAttackHitsTheTargetAcAndLandsDamage() {
        sorcerer();
        var g = goblin(14);
        FixedRandom.next = 9; // d20 → 10 + bonus ≥ 13; 2d10 → 10 + 10

        var r = service.cast("sorc", new CastRequest("magic-bolt", null, null, null, g.combatantId(), null));
        assertThat(map(r.resolution().getPayload().get("attackRoll")).get("hit")).isEqualTo(true);
        // 20 (dice) + 9 (flat) + 3 (INT) = 32 pure → MA 0 → 32 HP lost.
        assertThat(map(r.resolution().getPayload().get("damage")).get("total")).isEqualTo(32);
        assertThat(map(r.resolution().getPayload().get("target")).get("hpAfter")).isEqualTo(8);
        assertThat(monsters.get(ROOM, g.id()).hp().current()).isEqualTo(8);
    }

    @Test
    void spellAttackMissLeavesTheTargetUntouchedButCostsTheCaster() {
        sorcerer();
        var g = goblin(14);
        FixedRandom.next = 0; // natural 1 — automatic miss

        var r = service.cast("sorc", new CastRequest("magic-bolt", null, null, null, g.combatantId(), null));
        assertThat(map(r.resolution().getPayload().get("attackRoll")).get("hit")).isEqualTo(false);
        assertThat(r.resolution().getSteps()).anySatisfy(s -> assertThat(s.note()).contains("unharmed"));
        assertThat(monsters.get(ROOM, g.id()).hp().current()).isEqualTo(40);
        assertThat(r.snapshot().mana().current()).isLessThan(100);
    }

    /** Ruled 2026-08-27: a successful save means NO damage unless the spell says half. */
    @Test
    void saveSpellRollsTheTargetsSaveAndNegatesDamageOnSuccess() {
        sorcerer();
        var g = goblin(30); // DEX +10: d10 10 + 10 = 20 beats any level-5 DC
        FixedRandom.next = 9; // d10 → 10; 3d8 → 8 + 8 + 8

        var r = service.cast("sorc", new CastRequest("nether-zone", null, null, null, g.combatantId(), null));
        var save = map(r.resolution().getPayload().get("save"));
        assertThat(save.get("stat")).isEqualTo("DEX");
        assertThat(save.get("success")).isEqualTo(true);
        assertThat(save.get("halfDamage")).isEqualTo(false); // nether-zone: "…or take X damage" — no half clause
        // 24 (dice) + 8 (flat) + 3 (INT) = 35 rolled, none of it lands.
        assertThat(map(r.resolution().getPayload().get("damage")).get("total")).isEqualTo(35);
        assertThat(r.resolution().getSteps()).anySatisfy(s -> {
            assertThat(s.rule()).isEqualTo("save");
            assertThat(s.note()).contains("Goblin DEX save").contains("SAVED");
        });
        assertThat(r.resolution().getSteps()).anySatisfy(s -> assertThat(s.note()).contains("saved — no damage"));
        assertThat(r.resolution().getPayload()).doesNotContainKey("target"); // nothing entered the pipeline
        assertThat(monsters.get(ROOM, g.id()).hp().current()).isEqualTo(40);
        assertThat(r.snapshot().mana().current()).isLessThan(100); // the cast is still paid for
    }

    @Test
    void saveSpellFlaggedHalfDamageOnSaveHalvesOnSuccess() {
        exorcist();
        var g = goblin(Map.of(AbilityScore.STR, 30, AbilityScore.WILL, 8)); // STR +10: 20 beats DC 14
        FixedRandom.next = 9; // d10 → 10; 4d12 → 10 + 10 + 10 + 10

        var r = service.cast("exo", new CastRequest("spear-of-illumination", null, null, null, g.combatantId(), null));
        var save = map(r.resolution().getPayload().get("save"));
        assertThat(save.get("stat")).isEqualTo("STR");
        assertThat(save.get("success")).isEqualTo(true);
        assertThat(save.get("halfDamage")).isEqualTo(true);
        // 40 (dice) + 17 (flat) + 3 (WILL) = 60 → halved 30 light → MA 0 → 40 − 30.
        assertThat(map(r.resolution().getPayload().get("damage")).get("total")).isEqualTo(60);
        assertThat(r.resolution().getSteps()).anySatisfy(s -> assertThat(s.note()).contains("halved by the save"));
        assertThat(map(r.resolution().getPayload().get("target")).get("hpAfter")).isEqualTo(10);
        assertThat(monsters.get(ROOM, g.id()).hp().current()).isEqualTo(10);
    }

    @Test
    void failedSaveLandsFullDamageEitherWay() {
        sorcerer();
        var g = goblin(1); // DEX −5: d10 10 − 5 = 5 fails DC 14
        FixedRandom.next = 9;

        var r = service.cast("sorc", new CastRequest("nether-zone", null, null, null, g.combatantId(), null));
        assertThat(map(r.resolution().getPayload().get("save")).get("success")).isEqualTo(false);
        // 35 shadow → MA 0 → 40 − 35.
        assertThat(map(r.resolution().getPayload().get("target")).get("hpAfter")).isEqualTo(5);
    }

    // ---- Abilities ----

    @Test
    void abilityTargetEffectLandsOnTheNamedTarget() {
        assassin();
        var g = goblin(14);

        var printed = service.useAbility("sassin", new UseAbilityRequest("unseen-blade-scar"));
        assertThat(printed.resolution().getSteps())
                .anySatisfy(s -> assertThat(s.note()).contains("(DM applies)"));
        assertThat(monsters.get(ROOM, g.id()).activeEffects()).isEmpty();

        var applied = service.useAbility("sassin", new UseAbilityRequest("unseen-blade-scar", g.combatantId()));
        assertThat(applied.resolution().getPayload().get("effectsAppliedTo")).isEqualTo(g.combatantId());
        var maimed = monsters.get(ROOM, g.id()).activeEffects().stream()
                .filter(e -> e.id().equals("maimed")).findFirst().orElseThrow();
        assertThat(maimed.stacks()).isEqualTo(3);
        assertThat(maimed.active()).isTrue(); // level-4 threshold is 2

        assertThatThrownBy(() -> service.useAbility("sassin", new UseAbilityRequest("unseen-blade-scar", "monster:999")))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
    }
}
