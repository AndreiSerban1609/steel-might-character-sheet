package com.steelmight.charactersheet.service;

import com.steelmight.charactersheet.dto.EncounterView;
import com.steelmight.charactersheet.dto.SetInitiativeRequest;
import com.steelmight.charactersheet.dto.StartEncounterRequest;
import com.steelmight.charactersheet.engine.RandomSource;
import com.steelmight.charactersheet.model.AbilityScore;
import com.steelmight.charactersheet.model.EncounterEntry;
import com.steelmight.charactersheet.model.GameCharacter;
import com.steelmight.charactersheet.model.LifeStatus;
import com.steelmight.charactersheet.model.RoomEncounter;
import com.steelmight.charactersheet.repository.CharacterRepository;
import com.steelmight.charactersheet.repository.RoomEncounterRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Room-level initiative and turn order. Initiative = d20 + DEX mod + bonusInitiative
 * (races.json initiativeBonus, e.g. human +5). Turn gating is server-enforced:
 * only the current entry may start a turn, a turn must be started before it can be
 * ended, and ending advances the order (DEAD participants are skipped). The DM can
 * force-advance (AFK player) or override an initiative value mid-combat.
 */
@Service
@Transactional
public class EncounterService {

    private final RoomEncounterRepository repo;
    private final CharacterRepository characters;
    private final RandomSource random;

    public EncounterService(RoomEncounterRepository repo, CharacterRepository characters,
                            RandomSource random) {
        this.repo = repo;
        this.characters = characters;
        this.random = random;
    }

    // ---- Lifecycle (DM) ----

    public EncounterView start(String room, StartEncounterRequest req) {
        List<GameCharacter> participants = resolveParticipants(room, req);
        if (participants.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "no characters in room '" + room + "'");
        }

        record Rolled(GameCharacter c, int initiative) {}
        var rolled = new ArrayList<Rolled>();
        for (var c : participants) {
            int roll = random.nextInt(20) + 1;
            rolled.add(new Rolled(c, roll + c.getStats().modifier(AbilityScore.DEX) + c.getBonusInitiative()));
        }
        rolled.sort(Comparator
                .comparingInt(Rolled::initiative).reversed()
                .thenComparing((Rolled r) -> -r.c().getStats().modifier(AbilityScore.DEX))
                .thenComparing(r -> r.c().getName()));

        var surprised = req != null && req.surprisedPlayerIds() != null
                ? req.surprisedPlayerIds() : List.<String>of();
        for (var id : surprised) {
            if (participants.stream().noneMatch(p -> p.getPlayerId().equals(id))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "surprised character is not a participant: " + id);
            }
        }

        var enc = repo.findById(room).orElseGet(() -> new RoomEncounter(room));
        enc.getEntries().clear();
        for (var r : rolled) {
            var entry = new EncounterEntry(r.c().getPlayerId(), r.c().getName(), r.initiative());
            entry.setSurprised(surprised.contains(r.c().getPlayerId()));
            enc.getEntries().add(entry);
        }
        // Surprise round = round 0: the full order stands, but surprised entries are
        // auto-skipped (as if at 0 AP) until the order wraps into round 1.
        enc.setRoundNumber(surprised.isEmpty() ? 1 : 0);
        enc.setCurrentIndex(0);
        enc.setTurnStarted(false);
        skipInactive(enc);
        return toView(repo.save(enc));
    }

    public EncounterView get(String room) {
        return repo.findById(room).map(this::toView).orElseGet(EncounterView::inactive);
    }

    public EncounterView end(String room) {
        repo.deleteById(room);
        return EncounterView.inactive();
    }

    /** DM override: skip the current turn (started or not) and advance. */
    public EncounterView forceNext(String room) {
        var enc = repo.findById(room).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "no encounter running in '" + room + "'"));
        advance(enc);
        return toView(repo.save(enc));
    }

    /** DM override: change a participant's initiative; the current character stays current. */
    public EncounterView setInitiative(String room, SetInitiativeRequest req) {
        var enc = repo.findById(room).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "no encounter running in '" + room + "'"));
        var entry = enc.getEntries().stream()
                .filter(e -> e.getPlayerId().equals(req.playerId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        req.playerId() + " is not in this encounter"));
        entry.setInitiative(req.initiative());

        String currentId = enc.current() != null ? enc.current().getPlayerId() : null;
        enc.getEntries().sort(Comparator.comparingInt(EncounterEntry::getInitiative).reversed()
                .thenComparing(EncounterEntry::getName));
        if (currentId != null) {
            for (int i = 0; i < enc.getEntries().size(); i++) {
                if (enc.getEntries().get(i).getPlayerId().equals(currentId)) {
                    enc.setCurrentIndex(i);
                    break;
                }
            }
        }
        return toView(repo.save(enc));
    }

    // ---- Turn gating (called from CharacterService turn actions) ----

    /**
     * Gate + mark a turn start. No-op when the room has no encounter or the
     * character isn't a participant (free-form ticking stays possible).
     *
     * @return whether AP recovery applies to this turn — false only on a participant's
     *         FIRST turn of the combat (2026-07-16 ruling: everyone opens on starting AP).
     */
    public boolean validateAndMarkTurnStart(GameCharacter c) {
        var enc = encounterFor(c);
        if (enc == null) return true;
        var current = enc.current();
        if (!current.getPlayerId().equals(c.getPlayerId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "not your turn — waiting for " + current.getName() + " (round " + enc.getRoundNumber() + ")");
        }
        if (enc.isTurnStarted()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "turn already started — end it to pass to the next character");
        }
        boolean firstTurn = !current.hasTookTurn();
        current.setTookTurn(true);
        enc.setTurnStarted(true);
        repo.save(enc);
        return !firstTurn;
    }

    /** Gate a turn end (must be the current character, turn must be started). */
    public void validateTurnEnd(GameCharacter c) {
        var enc = encounterFor(c);
        if (enc == null) return;
        var current = enc.current();
        if (!current.getPlayerId().equals(c.getPlayerId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "not your turn — waiting for " + current.getName() + " (round " + enc.getRoundNumber() + ")");
        }
        if (!enc.isTurnStarted()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "start your turn before ending it");
        }
    }

    /** Advance after a successful turn end. Returns the new current entry for the log/auto-start, or null. */
    public NextTurn completeTurn(GameCharacter c) {
        var enc = encounterFor(c);
        if (enc == null) return null;
        advance(enc);
        repo.save(enc);
        var next = enc.current();
        return next == null ? null : new NextTurn(next.getPlayerId(), next.getName(), enc.getRoundNumber());
    }

    /** The entry whose turn begins after an advance. */
    public record NextTurn(String playerId, String name, int round) {}

    // ---- internals ----

    private RoomEncounter encounterFor(GameCharacter c) {
        if (c.getRoomName() == null) return null;
        var enc = repo.findById(c.getRoomName()).orElse(null);
        if (enc == null || enc.getEntries().isEmpty()) return null;
        boolean participant = enc.getEntries().stream()
                .anyMatch(e -> e.getPlayerId().equals(c.getPlayerId()));
        return participant ? enc : null;
    }

    /** Move to the next actionable participant; wrapping increments the round. */
    private void advance(RoomEncounter enc) {
        enc.setTurnStarted(false);
        step(enc);
        skipInactive(enc);
    }

    /**
     * Skip entries that cannot act where the order currently stands: the DEAD, and —
     * during the surprise round only (round 0) — the surprised. Wrapping past the end
     * increments the round, so surprised entries stop being skipped from round 1 on.
     */
    private void skipInactive(RoomEncounter enc) {
        int size = enc.getEntries().size();
        for (int guard = 0; guard < size; guard++) {
            var current = enc.getEntries().get(enc.getCurrentIndex());
            boolean surprisedNow = enc.getRoundNumber() == 0 && current.isSurprised();
            if (!surprisedNow && !isDead(current.getPlayerId())) return;
            step(enc);
        }
        // everyone is dead — leave the index where it landed; the DM ends the encounter
    }

    private void step(RoomEncounter enc) {
        int next = enc.getCurrentIndex() + 1;
        if (next >= enc.getEntries().size()) {
            next = 0;
            enc.setRoundNumber(enc.getRoundNumber() + 1);
        }
        enc.setCurrentIndex(next);
    }

    private boolean isDead(String playerId) {
        return characters.findById(playerId)
                .map(ch -> ch.getLifeStatus() == LifeStatus.DEAD)
                .orElse(false);
    }

    private List<GameCharacter> resolveParticipants(String room, StartEncounterRequest req) {
        if (req != null && req.playerIds() != null && !req.playerIds().isEmpty()) {
            var list = new ArrayList<GameCharacter>();
            for (var id : req.playerIds()) {
                var c = characters.findById(id).orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "unknown character: " + id));
                if (!room.equals(c.getRoomName())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            c.getName() + " is not in room '" + room + "'");
                }
                list.add(c);
            }
            return list;
        }
        return characters.findAll().stream()
                .filter(c -> room.equals(c.getRoomName()))
                .toList();
    }

    private EncounterView toView(RoomEncounter enc) {
        var entries = enc.getEntries().stream()
                .map(e -> new EncounterView.Entry(e.getPlayerId(), e.getName(), e.getInitiative(),
                        characters.findById(e.getPlayerId())
                                .map(ch -> ch.getLifeStatus().name()).orElse(null),
                        e.isSurprised()))
                .toList();
        var current = enc.current();
        return new EncounterView(true, enc.getRoundNumber(),
                current != null ? current.getPlayerId() : null,
                enc.isTurnStarted(), entries);
    }
}
