package com.steelmight.charactersheet.service;

import com.steelmight.charactersheet.dto.*;
import com.steelmight.charactersheet.model.*;
import com.steelmight.charactersheet.repository.CharacterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class CharacterServiceTest {

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
        c.setHp(new HitPoints(100, 100, 0));
        c.setMana(new ManaPool(0, 0));
        c.setAp(new ActionPoints(6, 6, 10));
        repo.save(c);
    }

    private static Map<AbilityScore, Integer> stats(int str, int dex, int con, int intel,
                                                    int wis, int will, int cha) {
        var m = new HashMap<AbilityScore, Integer>();
        m.put(AbilityScore.STR, str);
        m.put(AbilityScore.DEX, dex);
        m.put(AbilityScore.CON, con);
        m.put(AbilityScore.INT, intel);
        m.put(AbilityScore.WIS, wis);
        m.put(AbilityScore.WILL, will);
        m.put(AbilityScore.CHA, cha);
        return m;
    }

    @Test
    void updateStatsSetsScoresAndRederivesModifiersAndHp() {
        var snap = service.updateStats("p1", new UpdateStatsRequest(stats(16, 14, 12, 10, 8, 10, 18)));

        assertThat(snap.stats().get(AbilityScore.STR)).isEqualTo(16);
        assertThat(snap.modifiers().get(AbilityScore.STR)).isEqualTo(3);
        assertThat(snap.modifiers().get(AbilityScore.WIS)).isEqualTo(-1);
        // CON 12 -> +1; bard hpPerLevel 25; maxHP = (25 + 3*1) * 5 = 140
        assertThat(snap.hp().max()).isEqualTo(140);
    }

    @Test
    void updateStatsIsPersisted() {
        service.updateStats("p1", new UpdateStatsRequest(stats(16, 14, 12, 10, 8, 10, 18)));
        assertThat(repo.findById("p1").orElseThrow().getStats().get(AbilityScore.STR)).isEqualTo(16);
    }

    @Test
    void rejectsOutOfRangeStat() {
        assertThatThrownBy(() -> service.updateStats("p1", new UpdateStatsRequest(stats(99, 14, 12, 10, 8, 10, 18))))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsMissingStat() {
        var incomplete = new HashMap<AbilityScore, Integer>();
        incomplete.put(AbilityScore.STR, 12);
        assertThatThrownBy(() -> service.updateStats("p1", new UpdateStatsRequest(incomplete)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void updateVitalsClampsCurrentHpToDerivedMax() {
        // p1: bard L5, CON 10 (+0) -> maxHP = (25 + 0) * 5 = 125
        var snap = service.updateVitals("p1", new VitalsRequest(9999, 7, 3, null));
        assertThat(snap.hp().current()).isEqualTo(125);
        assertThat(snap.hp().temp()).isEqualTo(7);
        assertThat(snap.ap().current()).isEqualTo(3);
    }

    @Test
    void updateVitalsRejectsNegative() {
        assertThatThrownBy(() -> service.updateVitals("p1", new VitalsRequest(-1, null, null, null)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void updateIdentityChangesLevelAndRederivesHp() {
        var snap = service.updateIdentity("p1", new IdentityRequest("Renamed", 3));
        assertThat(snap.name()).isEqualTo("Renamed");
        assertThat(snap.level()).isEqualTo(3);
        // bard hpPerLevel 25, CON 10 (+0), level 3 -> 75
        assertThat(snap.hp().max()).isEqualTo(75);
    }

    @Test
    void updateIdentityRejectsBadLevel() {
        assertThatThrownBy(() -> service.updateIdentity("p1", new IdentityRequest(null, 25)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void getRosterReturnsAllCharacters() {
        var c2 = new GameCharacter("p2");
        c2.setName("Second");
        c2.setLevel(3);
        c2.setPathId("warrior");
        c2.setClassId("barbarian");
        c2.setStats(new Stats(14, 12, 14, 8, 10, 10, 10));
        c2.setHp(new HitPoints(50, 50, 0));
        c2.setMana(new ManaPool(0, 0));
        c2.setAp(new ActionPoints(6, 6, 10));
        repo.save(c2);

        var roster = service.getRoster(null);
        assertThat(roster).hasSize(2);
        assertThat(roster).extracting(RosterEntry::playerId).containsExactlyInAnyOrder("p1", "p2");
    }

    /** Full M6-A payload: level-1 human bard with a valid array/bonus/skills/spell. */
    private static CreateCharacterRequest bardRequest(String room, String email, String name) {
        return new CreateCharacterRequest(room, email, name,
                "human", "musician", "bard", "singer-of-heroism", 1,
                stats(9, 12, 13, 11, 10, 15, 8),
                Map.of(AbilityScore.CHA, 2, AbilityScore.DEX, 2, AbilityScore.CON, 1),
                List.of("persuasion", "performance", "deception"),
                List.of("dissonating-song"), null, null, null);
    }

    @Test
    void createsCharacterWithRoomEmailId() {
        var created = service.createCharacter(bardRequest("Dragon's Lair", "Andrei@Example.com", "Kael"));

        assertThat(created.playerId()).isEqualTo("dragon-s-lair-andrei@example.com");
        assertThat(created.snapshot().name()).isEqualTo("Kael");
        assertThat(created.snapshot().level()).isEqualTo(1);
        // bard hpPerLevel 25, CON 13 + 1 bonus = 14 (+2) -> (25 + 6) * 1 = 31
        assertThat(created.snapshot().hp().max()).isEqualTo(31);
        assertThat(repo.existsById("dragon-s-lair-andrei@example.com")).isTrue();
    }

    @Test
    void rejectsDuplicateCharacter() {
        service.createCharacter(bardRequest("room", "a@b.com", "X"));
        assertThatThrownBy(() -> service.createCharacter(bardRequest("room", "a@b.com", "X")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsClassNotInPath() {
        var req = new CreateCharacterRequest("room", "c@d.com", "X",
                "human", "musician", "barbarian", "berserker", 1,
                stats(15, 12, 13, 11, 10, 9, 8),
                Map.of(AbilityScore.STR, 2, AbilityScore.DEX, 2, AbilityScore.CON, 1),
                List.of("athletics", "intimidation", "survival"),
                List.of(), null, null, null);
        assertThatThrownBy(() -> service.createCharacter(req)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void findByRoomEmailReturnsCreatedThen404() {
        service.createCharacter(bardRequest("room", "e@f.com", "Finder"));
        assertThat(service.findByRoomEmail("room", "e@f.com").snapshot().name()).isEqualTo("Finder");
        assertThatThrownBy(() -> service.findByRoomEmail("room", "missing@x.com"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rosterFiltersByRoom() {
        service.createCharacter(bardRequest("alpha", "1@x.com", "A1"));
        service.createCharacter(bardRequest("beta", "2@x.com", "B1"));
        var alpha = service.getRoster("alpha");
        assertThat(alpha).hasSize(1);
        assertThat(alpha.get(0).name()).isEqualTo("A1");
    }

    @Test
    void updateProficienciesSetsValidSkillsDeduped() {
        var snap = service.updateProficiencies("p1",
                new ProficienciesRequest(List.of("athletics", "stealth", "athletics")));
        assertThat(snap.proficiencies()).containsExactlyInAnyOrder("athletics", "stealth");
    }

    @Test
    void updateProficienciesRejectsUnknownSkill() {
        assertThatThrownBy(() -> service.updateProficiencies("p1",
                new ProficienciesRequest(List.of("flying")))).isInstanceOf(ResponseStatusException.class);
    }
}
