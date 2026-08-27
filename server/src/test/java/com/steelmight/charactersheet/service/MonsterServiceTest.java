package com.steelmight.charactersheet.service;

import com.steelmight.charactersheet.dto.ApplyEffectRequest;
import com.steelmight.charactersheet.dto.CombatantView;
import com.steelmight.charactersheet.dto.DamageRequest;
import com.steelmight.charactersheet.dto.HealRequest;
import com.steelmight.charactersheet.dto.MonsterTemplateRequest;
import com.steelmight.charactersheet.dto.MonsterTemplateView;
import com.steelmight.charactersheet.dto.MonsterView;
import com.steelmight.charactersheet.dto.SpawnMonstersRequest;
import com.steelmight.charactersheet.model.AbilityScore;
import com.steelmight.charactersheet.model.ActionPoints;
import com.steelmight.charactersheet.model.DamageType;
import com.steelmight.charactersheet.model.GameCharacter;
import com.steelmight.charactersheet.model.HitPoints;
import com.steelmight.charactersheet.model.ManaPool;
import com.steelmight.charactersheet.model.Stats;
import com.steelmight.charactersheet.repository.CharacterRepository;
import com.steelmight.charactersheet.repository.MonsterInstanceRepository;
import com.steelmight.charactersheet.repository.MonsterTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Epic 2 Story 2.2 (ADR-001): monsters are Combatants — they take damage through the same
 * resist → armor → HP → death pipeline as players, tick DoTs, and answer the combatant
 * action routes next to players. Rulings E1/E2/E4/E7/E9 each get a case.
 */
@SpringBootTest
class MonsterServiceTest {

    private static final String ROOM = "monster-test-room";

    @Autowired private MonsterService monsters;
    @Autowired private CombatantActionService combatants;
    @Autowired private MonsterTemplateRepository templateRepo;
    @Autowired private MonsterInstanceRepository instanceRepo;
    @Autowired private CharacterRepository characterRepo;

    @BeforeEach
    void clean() {
        instanceRepo.deleteAll();
        templateRepo.deleteAll();
        characterRepo.deleteAll();
    }

    /** Level 4 (default threshold ceil(4/2) = 2), half fire, poison-immune, WILL 8 (mod -1). */
    private static MonsterTemplateRequest goblin(Integer stackThreshold, int will) {
        return new MonsterTemplateRequest("Goblin", 4, 40, 13, 2, 0, 30, 3, 1,
                Map.of(AbilityScore.DEX, 14, AbilityScore.WILL, will),
                List.of(AbilityScore.DEX),
                Map.of(DamageType.FIRE, 0.5, DamageType.POISON, 0.0),
                "Nasty bite: 1d6+2 piercing.", stackThreshold);
    }

    private MonsterView spawnOne(MonsterTemplateRequest req) {
        var t = monsters.createTemplate(ROOM, req);
        return monsters.spawn(ROOM, new SpawnMonstersRequest(t.id(), 1)).get(0);
    }

    private static DamageRequest dmg(int value, DamageType type) {
        return new DamageRequest(value, type, null, false, null, false);
    }

    // ---- Templates & spawning (E9) ----

    @Test
    void spawnCopiesTheBlockAndAutoNumbersNames() {
        var t = monsters.createTemplate(ROOM, goblin(null, 8));
        assertThat(t.id()).isNotNull();
        assertThat(t.stats()).containsEntry(AbilityScore.DEX, 14).containsEntry(AbilityScore.STR, 10);

        var first = monsters.spawn(ROOM, new SpawnMonstersRequest(t.id(), null));
        assertThat(first).hasSize(1);
        assertThat(first.get(0).name()).isEqualTo("Goblin");
        assertThat(first.get(0).combatantId()).isEqualTo("monster:" + first.get(0).id());
        assertThat(first.get(0).hp().current()).isEqualTo(40);
        assertThat(first.get(0).hp().max()).isEqualTo(40);
        assertThat(first.get(0).ac()).isEqualTo(13);
        assertThat(first.get(0).stackThreshold()).isEqualTo(2);
        assertThat(first.get(0).abilitiesText()).contains("Nasty bite");

        var more = monsters.spawn(ROOM, new SpawnMonstersRequest(t.id(), 2));
        assertThat(more).extracting(MonsterView::name).containsExactly("Goblin 2", "Goblin 3");
        assertThat(monsters.list(ROOM)).hasSize(3);

        // Editing the template afterwards never touches the live fight.
        monsters.updateTemplate(ROOM, t.id(), new MonsterTemplateRequest("Goblin", 4, 99, 13, 2, 0, 30, 3, 1,
                null, null, null, null, null));
        assertThat(monsters.get(ROOM, first.get(0).id()).hp().max()).isEqualTo(40);
    }

    @Test
    void templatesAreRoomScopedAndRoundTripThroughImport() {
        var t = monsters.createTemplate(ROOM, goblin(3, 8));
        assertThatThrownBy(() -> monsters.spawn("other-room", new SpawnMonstersRequest(t.id(), 1)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");

        MonsterTemplateView exported = monsters.listTemplates(ROOM).get(0);
        var imported = monsters.importTemplates("other-room", List.of(new MonsterTemplateRequest(
                exported.name(), exported.level(), exported.maxHp(), exported.ac(), exported.pa(), exported.ma(),
                exported.speed(), exported.might(), exported.initiativeBonus(), exported.stats(),
                exported.savingThrowProficiencies(), exported.damageTaken(), exported.abilitiesText(),
                exported.stackThreshold())));
        assertThat(imported).hasSize(1);
        var copy = monsters.listTemplates("other-room").get(0);
        assertThat(copy.roomName()).isEqualTo("other-room");
        assertThat(copy.damageTaken()).containsEntry(DamageType.FIRE, 0.5).containsEntry(DamageType.POISON, 0.0);
        assertThat(copy.stackThreshold()).isEqualTo(3);
        assertThat(copy.savingThrowProficiencies()).containsExactly(AbilityScore.DEX);
    }

    // ---- The same damage pipeline as players ----

    @Test
    void damageRunsThroughInnateResistanceArmorAndHp() {
        var g = spawnOne(goblin(null, 8));

        // 20 fire: innate ×0.5 → 10; magical damage meets MA 0 → 10 HP lost.
        var fire = monsters.damage(ROOM, g.id(), dmg(20, DamageType.FIRE));
        assertThat(fire.snapshot().hp().current()).isEqualTo(30);
        assertThat(fire.resolution().getSteps())
                .anySatisfy(s -> {
                    assertThat(s.rule()).isEqualTo("resistance");
                    assertThat(s.note()).contains("monster: Goblin");
                });

        // 10 slashing: physical → PA 2 soaks → 8 HP lost.
        var slash = monsters.damage(ROOM, g.id(), dmg(10, DamageType.SLASHING));
        assertThat(slash.snapshot().hp().current()).isEqualTo(22);
        assertThat(slash.resolution().getSteps()).anySatisfy(s -> assertThat(s.rule()).isEqualTo("armor"));

        // Poison: authored immunity (×0) → nothing. 22/40 = 55% → no condition yet.
        var poison = monsters.damage(ROOM, g.id(), dmg(15, DamageType.POISON));
        assertThat(poison.snapshot().hp().current()).isEqualTo(22);
        assertThat(poison.snapshot().conditions()).isEmpty();

        // 17/40 < 50% → injured, derived from the same condition terms as players.
        var more = monsters.damage(ROOM, g.id(), dmg(5, DamageType.TRUE));
        assertThat(more.snapshot().hp().current()).isEqualTo(17);
        assertThat(more.snapshot().conditions()).contains("injured");
    }

    @Test
    void monsterDiesAtZeroHpWithoutADownedWindow_E4() {
        var g = spawnOne(goblin(null, 14)); // WILL 14 would down a player
        var hit = monsters.damage(ROOM, g.id(), dmg(100, DamageType.TRUE));
        assertThat(hit.snapshot().status()).isEqualTo("DEAD");
        assertThat(hit.resolution().getSteps())
                .anySatisfy(s -> {
                    assertThat(s.rule()).isEqualTo("death");
                    assertThat(s.note()).contains("is slain");
                });
        assertThat(hit.resolution().getEffectsTriggered()).contains("death");
        assertThat(instanceRepo.findById(g.id()).orElseThrow().isPendingDeathFight()).isFalse();

        // Dead monsters can't be healed back (same heal-block rule as players).
        var heal = monsters.heal(ROOM, g.id(), new HealRequest(20));
        assertThat(heal.snapshot().hp().current()).isZero();
        assertThat(heal.resolution().getSteps()).anySatisfy(s -> assertThat(s.rule()).isEqualTo("downed-no-heal"));
    }

    @Test
    void healingAMonsterUsesTheFullPipeline_E7() {
        var g = spawnOne(goblin(null, 8));
        monsters.damage(ROOM, g.id(), dmg(30, DamageType.TRUE));
        var healed = monsters.heal(ROOM, g.id(), new HealRequest(50));
        assertThat(healed.snapshot().hp().current()).isEqualTo(40); // capped at authored max
    }

    // ---- Effects, thresholds (E2) and turn ticks (E1: no AP) ----

    @Test
    void authoredStackThresholdReplacesTheFormula_E2() {
        var poisoned = new ApplyEffectRequest("poisoned", 3, null, null, "test", false, false, false, null);

        // Level 4 default is ceil(4/2) = 2 stacks → 3 poisoned stacks fire.
        var grunt = spawnOne(goblin(null, 8));
        var onGrunt = monsters.applyEffect(ROOM, grunt.id(), poisoned).snapshot().activeEffects().stream()
                .filter(e -> e.id().equals("poisoned")).findFirst().orElseThrow();
        assertThat(onGrunt.active()).isTrue();
        assertThat(onGrunt.threshold()).isEqualTo(2);

        // This boss needs 5 — the same 3 stacks stay dormant.
        var boss = spawnOne(goblin(5, 8));
        assertThat(boss.stackThreshold()).isEqualTo(5);
        var onBoss = monsters.applyEffect(ROOM, boss.id(), poisoned).snapshot().activeEffects().stream()
                .filter(e -> e.id().equals("poisoned")).findFirst().orElseThrow();
        assertThat(onBoss.stacks()).isEqualTo(3);
        assertThat(onBoss.active()).isFalse();
        assertThat(onBoss.threshold()).isEqualTo(5);
    }

    @Test
    void turnStartTicksDotsButHasNoApStep_E1() {
        var g = spawnOne(goblin(null, 8));
        // Burning is multi-instance (never threshold-gated): 4 stacks = 4 fire at turn start,
        // which the goblin's innate ×0.5 halves to 2 — DoTs skip armor but not resistance.
        monsters.applyEffect(ROOM, g.id(),
                new ApplyEffectRequest("burning", 4, null, null, "test", false, false, false, null));

        var start = monsters.turnStart(ROOM, g.id());
        assertThat(start.snapshot().hp().current()).isEqualTo(38);
        assertThat(start.resolution().getSteps())
                .anySatisfy(s -> assertThat(s.rule()).startsWith("burning:"))
                .noneSatisfy(s -> assertThat(s.rule()).isEqualTo("ap-recovery"));

        // Turn end ticks cleanly too (HoTs / expiry) — still no AP economy anywhere in the log.
        var end = monsters.turnEnd(ROOM, g.id());
        assertThat(end.resolution().getSteps()).noneSatisfy(s -> assertThat(s.rule()).isEqualTo("ap-recovery"));
        assertThat(end.snapshot().hp().current()).isEqualTo(38);
    }

    // ---- Combatant routes: one namespace for players and monsters ----

    @Test
    void combatantActionsDispatchByIdPrefix() {
        var player = new GameCharacter("p1");
        player.setRoomName(ROOM);
        player.setName("Hero");
        player.setLevel(5);
        player.setPathId("musician");
        player.setClassId("bard");
        player.setStats(new Stats(10, 10, 10, 10, 10, 14, 10));
        player.setHp(new HitPoints(125, 125, 0));
        player.setMana(new ManaPool(0, 0));
        player.setAp(new ActionPoints(6, 6, 10));
        characterRepo.save(player);
        var g = spawnOne(goblin(null, 8));

        var onPlayer = combatants.damage(ROOM, "p1", dmg(10, DamageType.TRUE));
        assertThat(onPlayer.snapshot().type()).isEqualTo(CombatantView.PLAYER);
        assertThat(onPlayer.snapshot().character().hp().current()).isEqualTo(115);
        assertThat(onPlayer.snapshot().monster()).isNull();

        var onMonster = combatants.damage(ROOM, g.combatantId(), dmg(10, DamageType.TRUE));
        assertThat(onMonster.snapshot().type()).isEqualTo(CombatantView.MONSTER);
        assertThat(onMonster.snapshot().monster().hp().current()).isEqualTo(30);
        assertThat(onMonster.snapshot().character()).isNull();

        assertThat(combatants.get(ROOM, g.combatantId()).name()).isEqualTo("Goblin");
        assertThatThrownBy(() -> combatants.damage(ROOM, "monster:nope", dmg(1, DamageType.TRUE)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
        assertThatThrownBy(() -> combatants.damage("elsewhere", g.combatantId(), dmg(1, DamageType.TRUE)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
    }

    // ---- Attacker context (Story 2.4): a named monster attacker fills in might + source ----

    @Test
    void namedMonsterAttackerAutoFillsConcentrationDcAndSource() {
        var caster = new GameCharacter("caster");
        caster.setRoomName(ROOM);
        caster.setName("Caster");
        caster.setLevel(5);
        caster.setPathId("wizard");
        caster.setClassId("sorcerer");
        caster.setStats(new Stats(10, 10, 10, 16, 10, 14, 10));
        caster.setHp(new HitPoints(100, 100, 0));
        caster.setMana(new ManaPool(100, 100));
        caster.setAp(new ActionPoints(6, 6, 10));
        characterRepo.save(caster);
        combatants.applyEffect(ROOM, "caster",
                new ApplyEffectRequest("concentrating", 1, null, null, "caster:test-spell", false, true, false, null));
        var g = spawnOne(goblin(null, 8)); // might 3

        var hit = combatants.damage(ROOM, "caster",
                new DamageRequest(10, DamageType.SLASHING, null, false, null, false, null, g.combatantId()));
        // Might 3 → DC 8, rolled server-side instead of the "resolve manually" note.
        assertThat(hit.resolution().getSteps())
                .anySatisfy(s -> {
                    assertThat(s.rule()).isEqualTo("concentration-check");
                    assertThat(s.note()).contains("vs DC 8");
                });

        // An explicit might still wins over the attacker's authored one.
        var override = combatants.damage(ROOM, "caster",
                new DamageRequest(1, DamageType.TRUE, null, false, null, false, 10, g.combatantId()));
        assertThat(override.resolution().getSteps())
                .filteredOn(s -> s.rule().equals("concentration-check"))
                .allSatisfy(s -> assertThat(s.note()).containsAnyOf("vs DC 15", "resolve the WILL save manually"));

        assertThatThrownBy(() -> combatants.damage(ROOM, "caster",
                new DamageRequest(1, DamageType.TRUE, null, false, null, false, null, "monster:999")))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("unknown attacker");
    }

    @Test
    void deletingAMonsterRemovesItsEffectRows() {
        var g = spawnOne(goblin(null, 8));
        monsters.applyEffect(ROOM, g.id(),
                new ApplyEffectRequest("burning", 1, null, null, "test", false, false, false, null));
        monsters.delete(ROOM, g.id());
        assertThat(monsters.list(ROOM)).isEmpty();
        assertThatThrownBy(() -> monsters.get(ROOM, g.id()))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
    }
}
