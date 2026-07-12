package com.steelmight.charactersheet.service;

import com.steelmight.charactersheet.dto.SetInitiativeRequest;
import com.steelmight.charactersheet.dto.StartEncounterRequest;
import com.steelmight.charactersheet.model.*;
import com.steelmight.charactersheet.repository.CharacterRepository;
import com.steelmight.charactersheet.repository.RoomEncounterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class EncounterServiceTest {

    private static final String ROOM = "enc-room";

    @Autowired
    private EncounterService encounters;

    @Autowired
    private CharacterService characterService;

    @Autowired
    private CharacterRepository repo;

    @Autowired
    private RoomEncounterRepository encounterRepo;

    @BeforeEach
    void setUp() {
        encounterRepo.deleteAll();
        repo.deleteAll();
        save("e1", "Alpha", 14, 0);  // DEX 14 → +2
        save("e2", "Bravo", 10, 5);  // human-style +5 initiative bonus
        save("e3", "Charlie", 18, 0); // DEX 18 → +4
    }

    private GameCharacter save(String id, String name, int dex, int bonusInitiative) {
        var c = new GameCharacter(id);
        c.setName(name);
        c.setRoomName(ROOM);
        c.setLevel(5);
        c.setPathId("warrior");
        c.setClassId("barbarian");
        c.setStats(new Stats(10, dex, 12, 10, 10, 10, 10));
        c.setHp(new HitPoints(100, 100, 0));
        c.setMana(new ManaPool(0, 0));
        c.setAp(new ActionPoints(6, 6, 10));
        c.setBonusInitiative(bonusInitiative);
        return repo.save(c);
    }

    @Test
    void startRollsInitiativeWithinBoundsAndSortsDescending() {
        var view = encounters.start(ROOM, null);
        assertThat(view.active()).isTrue();
        assertThat(view.round()).isEqualTo(1);
        assertThat(view.entries()).hasSize(3);
        assertThat(view.currentPlayerId()).isEqualTo(view.entries().get(0).playerId());
        assertThat(view.turnStarted()).isFalse();

        for (var e : view.entries()) {
            var c = repo.findById(e.playerId()).orElseThrow();
            int mod = c.getStats().modifier(AbilityScore.DEX) + c.getBonusInitiative();
            assertThat(e.initiative()).isBetween(1 + mod, 20 + mod); // d20 + DEX mod + bonus
        }
        for (int i = 1; i < view.entries().size(); i++) {
            assertThat(view.entries().get(i - 1).initiative())
                    .isGreaterThanOrEqualTo(view.entries().get(i).initiative());
        }
    }

    @Test
    void turnGatingEnforcesOrderAndAlternation() {
        var view = encounters.start(ROOM, null);
        String first = view.currentPlayerId();
        String second = view.entries().get(1).playerId();

        // someone else cannot start
        assertThatThrownBy(() -> characterService.turnStart(second))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not your turn");

        // current cannot end before starting
        assertThatThrownBy(() -> characterService.turnEnd(first))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("start your turn");

        characterService.turnStart(first);

        // no double start
        assertThatThrownBy(() -> characterService.turnStart(first))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already started");

        var ended = characterService.turnEnd(first);
        assertThat(ended.resolution().getSteps())
                .anyMatch(s -> s.rule().equals("turn-order"));

        // order advanced to the second character
        assertThat(encounters.get(ROOM).currentPlayerId()).isEqualTo(second);
        assertThat(encounters.get(ROOM).turnStarted()).isFalse();
    }

    @Test
    void wrappingPastTheLastEntryIncrementsTheRound() {
        var view = encounters.start(ROOM, null);
        for (var entry : view.entries()) {
            characterService.turnStart(entry.playerId());
            characterService.turnEnd(entry.playerId());
        }
        var after = encounters.get(ROOM);
        assertThat(after.round()).isEqualTo(2);
        assertThat(after.currentPlayerId()).isEqualTo(view.entries().get(0).playerId());
    }

    @Test
    void deadParticipantsAreSkipped() {
        var view = encounters.start(ROOM, null);
        String second = view.entries().get(1).playerId();
        var dead = repo.findById(second).orElseThrow();
        dead.setLifeStatus(LifeStatus.DEAD);
        repo.save(dead);

        String first = view.currentPlayerId();
        characterService.turnStart(first);
        characterService.turnEnd(first);

        assertThat(encounters.get(ROOM).currentPlayerId())
                .isEqualTo(view.entries().get(2).playerId());
    }

    @Test
    void dmCanForceNextAndOverrideInitiative() {
        var view = encounters.start(ROOM, null);
        String first = view.currentPlayerId();
        String second = view.entries().get(1).playerId();

        // skip an AFK player without any turn actions
        encounters.forceNext(ROOM);
        assertThat(encounters.get(ROOM).currentPlayerId()).isEqualTo(second);

        // pushing the first character to the bottom keeps the current character current
        encounters.setInitiative(ROOM, new SetInitiativeRequest(first, -5));
        var after = encounters.get(ROOM);
        assertThat(after.currentPlayerId()).isEqualTo(second);
        assertThat(after.entries().get(after.entries().size() - 1).playerId()).isEqualTo(first);
    }

    @Test
    void charactersOutsideTheEncounterTickFreely() {
        encounters.start(ROOM, new StartEncounterRequest(List.of("e1", "e2")));
        // e3 is not a participant — free-form turn ticking still works
        characterService.turnStart("e3");
        characterService.turnEnd("e3");
        // and the encounter did not advance
        assertThat(encounters.get(ROOM).turnStarted()).isFalse();
    }

    @Test
    void endingTheEncounterLiftsAllGates() {
        var view = encounters.start(ROOM, null);
        String second = view.entries().get(1).playerId();
        encounters.end(ROOM);
        assertThat(encounters.get(ROOM).active()).isFalse();
        characterService.turnStart(second); // no gate anymore
        characterService.turnEnd(second);
    }
}
