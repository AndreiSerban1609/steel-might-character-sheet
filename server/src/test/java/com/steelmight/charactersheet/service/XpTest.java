package com.steelmight.charactersheet.service;

import com.steelmight.charactersheet.dto.DamageRequest;
import com.steelmight.charactersheet.dto.GainXpRequest;
import com.steelmight.charactersheet.dto.MonsterTemplateRequest;
import com.steelmight.charactersheet.dto.MonsterView;
import com.steelmight.charactersheet.dto.SpawnMonstersRequest;
import com.steelmight.charactersheet.engine.XpRules;
import com.steelmight.charactersheet.model.*;
import com.steelmight.charactersheet.repository.CharacterRepository;
import com.steelmight.charactersheet.repository.MonsterInstanceRepository;
import com.steelmight.charactersheet.repository.MonsterTemplateRepository;
import com.steelmight.charactersheet.repository.RoomEncounterRepository;
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
 * Experience (Game Owner ruling 2026-08-27, xp-table.json): kills bank their might-row XP
 * into the running encounter; ending the combat splits the pool evenly among the players in
 * the order; XP outside combat is typed in; thresholds only FLAG a level-up.
 */
@SpringBootTest
class XpTest {

    private static final String ROOM = "xp-room";

    @Autowired private CharacterService characters;
    @Autowired private EncounterService encounters;
    @Autowired private MonsterService monsters;
    @Autowired private XpRules rules;
    @Autowired private CharacterRepository characterRepo;
    @Autowired private MonsterTemplateRepository templateRepo;
    @Autowired private MonsterInstanceRepository instanceRepo;
    @Autowired private RoomEncounterRepository encounterRepo;

    @BeforeEach
    void setUp() {
        encounterRepo.deleteAll();
        instanceRepo.deleteAll();
        templateRepo.deleteAll();
        characterRepo.deleteAll();
        player("x1", "Alpha");
        player("x2", "Bravo");
    }

    private GameCharacter player(String id, String name) {
        var c = new GameCharacter(id);
        c.setName(name);
        c.setRoomName(ROOM);
        c.setLevel(1);
        c.setPathId("warrior");
        c.setClassId("barbarian");
        c.setStats(new Stats(10, 10, 12, 10, 10, 10, 10));
        c.setHp(new HitPoints(50, 50, 0));
        c.setMana(new ManaPool(0, 0));
        c.setAp(new ActionPoints(6, 6, 10));
        return characterRepo.save(c);
    }

    /** 10 HP, AC 13, no armor; might as given (null → the level-3 row). */
    private MonsterView monster(Integer might) {
        var t = monsters.createTemplate(ROOM, new MonsterTemplateRequest("Goblin", 3, 10, 13, 0, 0, 30, might, 0,
                Map.of(), List.of(), Map.of(), "Bites.", null));
        return monsters.spawn(ROOM, new SpawnMonstersRequest(t.id(), 1)).get(0);
    }

    // ---- The table ----

    @Test
    void tableReadsAsRuled() {
        assertThat(rules.loaded()).isTrue();
        assertThat(rules.monsterXp(1)).isEqualTo(100);
        assertThat(rules.monsterXp(5)).isEqualTo(750);
        assertThat(rules.monsterXp(20)).isEqualTo(13000);
        assertThat(rules.monsterXp(99)).isEqualTo(13000); // clamped to the cap row
        assertThat(rules.xpToNext(1)).isEqualTo(200);
        assertThat(rules.xpToNext(19)).isEqualTo(290000);
        assertThat(rules.xpToNext(20)).isNull();
        // cumulative reading: 700 total = level 3 exactly; 699 is still level 2
        assertThat(rules.levelFor(0)).isEqualTo(1);
        assertThat(rules.levelFor(200)).isEqualTo(2);
        assertThat(rules.levelFor(699)).isEqualTo(2);
        assertThat(rules.levelFor(700)).isEqualTo(3);
        assertThat(rules.levelFor(9_999_999)).isEqualTo(20);
    }

    // ---- Kills bank into the encounter; End Combat splits ----

    @Test
    void killsBankTheirMightRowAndEndCombatSplitsEvenly() {
        var g1 = monster(2); // 250
        var g2 = monster(4); // 550
        encounters.start(ROOM, null);

        monsters.damage(ROOM, g1.id(), new DamageRequest(50, DamageType.SLASHING, null, false, null, false));
        assertThat(encounters.get(ROOM).xpPool()).isEqualTo(250);
        // A second hit on the corpse must not bank it twice.
        monsters.damage(ROOM, g1.id(), new DamageRequest(50, DamageType.SLASHING, null, false, null, false));
        assertThat(encounters.get(ROOM).xpPool()).isEqualTo(250);

        monsters.damage(ROOM, g2.id(), new DamageRequest(50, DamageType.SLASHING, null, false, null, false));
        assertThat(encounters.get(ROOM).xpPool()).isEqualTo(800);

        var ended = encounters.endAndAward(ROOM);
        assertThat(ended.encounter().active()).isFalse();
        assertThat(ended.xpAward().total()).isEqualTo(800);
        assertThat(ended.xpAward().recipients()).isEqualTo(2);
        assertThat(ended.xpAward().perPlayer()).isEqualTo(400);
        assertThat(ended.xpAward().awarded()).extracting("name").containsExactlyInAnyOrder("Alpha", "Bravo");
        // 400 ≥ 200 (level-1 row): level 2 is available but NOT applied.
        assertThat(ended.xpAward().awarded()).allSatisfy(r -> {
            assertThat(r.gained()).isEqualTo(400);
            assertThat(r.xp()).isEqualTo(400);
            assertThat(r.level()).isEqualTo(1);
            assertThat(r.levelAvailable()).isTrue();
        });
        assertThat(characterRepo.findById("x1").orElseThrow().getXp()).isEqualTo(400);
        assertThat(characterRepo.findById("x1").orElseThrow().getLevel()).isEqualTo(1);

        var snap = characters.getCombatSnapshot("x1");
        assertThat(snap.xp()).isEqualTo(400);
        assertThat(snap.xpToNext()).isEqualTo(200);
        assertThat(snap.levelAvailable()).isTrue();
    }

    @Test
    void mightFallsBackToLevelAndRoundingIsFloored() {
        monster(null); // level 3 → 400
        encounters.start(ROOM, null);
        var g = monsters.list(ROOM).get(0);
        monsters.damage(ROOM, g.id(), new DamageRequest(50, DamageType.SLASHING, null, false, null, false));
        assertThat(encounters.get(ROOM).xpPool()).isEqualTo(400);

        player("x3", "Charlie"); // joins nothing — not in the order, gets nothing
        var ended = encounters.endAndAward(ROOM);
        assertThat(ended.xpAward().perPlayer()).isEqualTo(200);
        assertThat(ended.xpAward().awarded()).hasSize(2);
        assertThat(characterRepo.findById("x3").orElseThrow().getXp()).isZero();
    }

    @Test
    void aKillOutsideACombatIsNotBankedAndAnEmptyPoolAwardsNothing() {
        var g = monster(2);
        monsters.damage(ROOM, g.id(), new DamageRequest(50, DamageType.SLASHING, null, false, null, false));
        assertThat(encounterRepo.findById(ROOM)).isEmpty();

        encounters.start(ROOM, null); // the corpse is skipped by start(); nothing to split
        var ended = encounters.endAndAward(ROOM);
        assertThat(ended.xpAward().total()).isZero();
        assertThat(ended.xpAward().awarded()).isEmpty();
        assertThat(characterRepo.findById("x1").orElseThrow().getXp()).isZero();
    }

    // ---- XP outside combat ----

    @Test
    void manualXpAddsFlagsTheLevelAndFloorsAtZero() {
        var r = characters.gainXp("x1", new GainXpRequest(150, "found the lost shrine"));
        assertThat(r.snapshot().xp()).isEqualTo(150);
        assertThat(r.snapshot().levelAvailable()).isFalse();
        assertThat(r.resolution().getSteps()).anySatisfy(s ->
                assertThat(s.note()).isEqualTo("Gained 150 XP — found the lost shrine"));

        r = characters.gainXp("x1", new GainXpRequest(50, null));
        assertThat(r.snapshot().xp()).isEqualTo(200);
        assertThat(r.snapshot().levelAvailable()).isTrue();
        assertThat(r.resolution().getSteps()).anySatisfy(s -> assertThat(s.rule()).isEqualTo("level-available"));

        r = characters.gainXp("x1", new GainXpRequest(-500, "GM correction"));
        assertThat(r.snapshot().xp()).isZero();

        assertThatThrownBy(() -> characters.gainXp("x1", new GainXpRequest(0, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("amount must not be 0");
    }
}
