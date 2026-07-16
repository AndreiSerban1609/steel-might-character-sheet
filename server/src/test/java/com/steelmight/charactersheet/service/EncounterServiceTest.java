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

        // order advanced to the second character, whose turn began automatically —
        // their start ticks land in the ender's log, name-prefixed
        var secondName = repo.findById(second).orElseThrow().getName();
        assertThat(encounters.get(ROOM).currentPlayerId()).isEqualTo(second);
        assertThat(encounters.get(ROOM).turnStarted()).isTrue();
        assertThat(ended.resolution().getSteps())
                .anyMatch(s -> s.rule().startsWith(secondName + ":"));

        // and the second character can only END their (auto-started) turn
        assertThatThrownBy(() -> characterService.turnStart(second))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already started");
        characterService.turnEnd(second);
    }

    @Test
    void wrappingPastTheLastEntryIncrementsTheRound() {
        var view = encounters.start(ROOM, null);
        // first turn starts manually here (the controller does it on encounter start);
        // every later turn begins automatically when the previous one ends
        characterService.turnStart(view.entries().get(0).playerId());
        for (var entry : view.entries()) {
            characterService.turnEnd(entry.playerId());
        }
        var after = encounters.get(ROOM);
        assertThat(after.round()).isEqualTo(2);
        assertThat(after.currentPlayerId()).isEqualTo(view.entries().get(0).playerId());
        assertThat(after.turnStarted()).isTrue(); // round 2's first turn is already running
    }

    @Test
    void firstTurnOfCombatGetsNoApRecovery() {
        var view = encounters.start(ROOM, null);
        String first = view.currentPlayerId();
        String second = view.entries().get(1).playerId();
        String third = view.entries().get(2).playerId();

        // everyone opens on starting AP; a first turn adds nothing
        var started = characterService.turnStart(first);
        assertThat(started.snapshot().ap().current()).isEqualTo(6);
        assertThat(started.resolution().getSteps())
                .anyMatch(s -> s.rule().equals("ap-recovery") && s.note().contains("First turn"));

        // auto-started round-1 turns are first turns too — still no recovery
        characterService.turnEnd(first);
        assertThat(repo.findById(second).orElseThrow().getAp().getCurrent()).isEqualTo(6);

        characterService.turnEnd(second);
        characterService.turnEnd(third);

        // round 2 wraps back to the first character — their SECOND turn recovers
        assertThat(encounters.get(ROOM).round()).isEqualTo(2);
        assertThat(repo.findById(first).orElseThrow().getAp().getCurrent()).isEqualTo(10); // 6+6 capped
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

        // the dead second entry is skipped and the third's turn auto-starts
        assertThat(encounters.get(ROOM).currentPlayerId())
                .isEqualTo(view.entries().get(2).playerId());
        assertThat(encounters.get(ROOM).turnStarted()).isTrue();
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
    void surpriseRoundSkipsTheAmbushedThenRoundOneIsNormal() {
        var view = encounters.start(ROOM, new StartEncounterRequest(null, List.of("e2")));
        assertThat(view.round()).isEqualTo(0); // surprise round
        assertThat(view.currentPlayerId()).isNotEqualTo("e2");
        assertThat(view.entries()).anyMatch(e -> e.playerId().equals("e2") && e.surprised());

        // play the surprise round: only the un-surprised act, in initiative order
        characterService.turnStart(view.currentPlayerId());
        var order = view.entries().stream().map(e -> e.playerId()).toList();
        for (var id : order) {
            if (id.equals("e2")) continue;
            assertThat(encounters.get(ROOM).currentPlayerId()).isEqualTo(id);
            characterService.turnEnd(id);
        }

        // the wrap lands on round 1 at the top of the FULL order — e2 acts normally now
        var roundOne = encounters.get(ROOM);
        assertThat(roundOne.round()).isEqualTo(1);
        assertThat(roundOne.currentPlayerId()).isEqualTo(order.get(0));

        // AP: surprisers already took their first (no-recovery) turn in round 0, so
        // their round-1 turn recovers (6+6 capped at 10); e2's round-1 turn is its
        // FIRST — no recovery, still 6.
        var cur = roundOne;
        while (!cur.currentPlayerId().equals("e2")) {
            String id = cur.currentPlayerId();
            assertThat(repo.findById(id).orElseThrow().getAp().getCurrent()).isEqualTo(10);
            characterService.turnEnd(id);
            cur = encounters.get(ROOM);
        }
        assertThat(repo.findById("e2").orElseThrow().getAp().getCurrent()).isEqualTo(6);
    }

    @Test
    void allSurprisedSkipsStraightToRoundOne() {
        var view = encounters.start(ROOM, new StartEncounterRequest(null, List.of("e1", "e2", "e3")));
        assertThat(view.round()).isEqualTo(1); // no one can act in round 0 — skip it
        assertThat(view.currentPlayerId()).isEqualTo(view.entries().get(0).playerId());
    }

    @Test
    void rejectsSurprisedNonParticipant() {
        assertThatThrownBy(() -> encounters.start(ROOM,
                new StartEncounterRequest(List.of("e1", "e2"), List.of("e3"))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not a participant");
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
