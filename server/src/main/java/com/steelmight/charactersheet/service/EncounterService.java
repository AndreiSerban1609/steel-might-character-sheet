package com.steelmight.charactersheet.service;

import com.steelmight.charactersheet.dto.EncounterView;
import com.steelmight.charactersheet.dto.SetInitiativeRequest;
import com.steelmight.charactersheet.dto.StartEncounterRequest;
import com.steelmight.charactersheet.engine.RandomSource;
import com.steelmight.charactersheet.model.AbilityScore;
import com.steelmight.charactersheet.model.Combatant;
import com.steelmight.charactersheet.model.CombatantType;
import com.steelmight.charactersheet.model.EncounterEntry;
import com.steelmight.charactersheet.model.GameCharacter;
import com.steelmight.charactersheet.model.LifeStatus;
import com.steelmight.charactersheet.model.MonsterInstance;
import com.steelmight.charactersheet.model.RoomEncounter;
import com.steelmight.charactersheet.repository.CharacterRepository;
import com.steelmight.charactersheet.repository.MonsterInstanceRepository;
import com.steelmight.charactersheet.repository.RoomEncounterRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Room-level initiative and turn order over COMBATANTS (players and monsters, ADR-001).
 * Initiative = d20 + DEX mod + initiative bonus (races.json bonus for players, the
 * authored bonus for monsters — ruling E8). Turn gating is server-enforced: only the
 * current entry may start a turn, a turn must be started before it can be ended, and
 * ending advances the order (DEAD participants are skipped). The DM can force-advance
 * (AFK player), override an initiative value mid-combat, and monsters that spawn or die
 * mid-fight join / leave the order in place.
 */
@Service
@Transactional
public class EncounterService {

    private final RoomEncounterRepository repo;
    private final CharacterRepository characters;
    private final MonsterInstanceRepository monsters;
    private final CombatantLookup lookup;
    private final RandomSource random;

    public EncounterService(RoomEncounterRepository repo, CharacterRepository characters,
                            MonsterInstanceRepository monsters, CombatantLookup lookup,
                            RandomSource random) {
        this.repo = repo;
        this.characters = characters;
        this.monsters = monsters;
        this.lookup = lookup;
        this.random = random;
    }

    // ---- Lifecycle (DM) ----

    /** Every listed (or all) player in the room plus every living monster spawned into it. */
    public EncounterView start(String room, StartEncounterRequest req) {
        List<Combatant> participants = new ArrayList<>(resolvePlayers(room, req));
        monsters.findByRoomNameOrderByIdAsc(room).stream()
                .filter(m -> m.getLifeStatus() != LifeStatus.DEAD)
                .forEach(participants::add);
        if (participants.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "no characters in room '" + room + "'");
        }

        record Rolled(Combatant c, int initiative) {}
        var rolled = new ArrayList<Rolled>();
        for (var c : participants) rolled.add(new Rolled(c, rollInitiative(c)));
        rolled.sort(Comparator
                .comparingInt(Rolled::initiative).reversed()
                .thenComparing((Rolled r) -> -r.c().getStats().modifier(AbilityScore.DEX))
                .thenComparing(r -> r.c().getName()));

        var surprised = req != null && req.surprisedPlayerIds() != null
                ? req.surprisedPlayerIds() : List.<String>of();
        for (var id : surprised) {
            if (participants.stream().noneMatch(p -> p.getCombatantId().equals(id))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "surprised character is not a participant: " + id);
            }
        }

        var enc = repo.findById(room).orElseGet(() -> new RoomEncounter(room));
        enc.getEntries().clear();
        for (var r : rolled) {
            var entry = new EncounterEntry(r.c().getCombatantId(), r.c().getName(), r.initiative());
            entry.setSurprised(surprised.contains(r.c().getCombatantId()));
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

    /** DM override: change a participant's initiative; the current combatant stays current. */
    public EncounterView setInitiative(String room, SetInitiativeRequest req) {
        var enc = repo.findById(room).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "no encounter running in '" + room + "'"));
        var entry = enc.getEntries().stream()
                .filter(e -> e.getCombatantId().equals(req.playerId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        req.playerId() + " is not in this encounter"));
        entry.setInitiative(req.initiative());
        String currentId = enc.current() != null ? enc.current().getCombatantId() : null;
        enc.getEntries().sort(Comparator.comparingInt(EncounterEntry::getInitiative).reversed()
                .thenComparing(EncounterEntry::getName));
        if (currentId != null) {
            for (int i = 0; i < enc.getEntries().size(); i++) {
                if (enc.getEntries().get(i).getCombatantId().equals(currentId)) {
                    enc.setCurrentIndex(i);
                    break;
                }
            }
        }
        return toView(repo.save(enc));
    }

    // ---- Mid-fight membership (reinforcements, despawns) ----

    /**
     * A combatant arriving while the order is running rolls initiative and slots in
     * where it belongs; the current turn is untouched. No-op without an encounter.
     */
    public void join(String room, Combatant c) {
        var enc = repo.findById(room).orElse(null);
        if (enc == null || enc.getEntries().isEmpty()) return;
        if (enc.getEntries().stream().anyMatch(e -> e.getCombatantId().equals(c.getCombatantId()))) return;

        int initiative = rollInitiative(c);
        var entry = new EncounterEntry(c.getCombatantId(), c.getName(), initiative);
        int at = enc.getEntries().size();
        for (int i = 0; i < enc.getEntries().size(); i++) {
            if (enc.getEntries().get(i).getInitiative() < initiative) { at = i; break; }
        }
        enc.getEntries().add(at, entry);
        if (at <= enc.getCurrentIndex()) enc.setCurrentIndex(enc.getCurrentIndex() + 1);
        repo.save(enc);
    }

    /** Drop a combatant from the order (despawn). Removing the current one advances. */
    public void leave(String room, String combatantId) {
        var enc = repo.findById(room).orElse(null);
        if (enc == null) return;
        int idx = -1;
        for (int i = 0; i < enc.getEntries().size(); i++) {
            if (enc.getEntries().get(i).getCombatantId().equals(combatantId)) { idx = i; break; }
        }
        if (idx < 0) return;
        enc.getEntries().remove(idx);
        if (enc.getEntries().isEmpty()) {
            enc.setCurrentIndex(0);
            enc.setTurnStarted(false);
        } else if (idx < enc.getCurrentIndex()) {
            enc.setCurrentIndex(enc.getCurrentIndex() - 1);
        } else if (idx == enc.getCurrentIndex()) {
            enc.setTurnStarted(false);
            if (enc.getCurrentIndex() >= enc.getEntries().size()) {
                enc.setCurrentIndex(0);
                enc.setRoundNumber(enc.getRoundNumber() + 1);
            }
            skipInactive(enc);
        }
        repo.save(enc);
    }

    // ---- Turn gating (called from the turn actions of either combatant kind) ----

    /**
     * Gate + mark a turn start. No-op when the room has no encounter or the
     * combatant isn't a participant (free-form ticking stays possible).
     *
     * @return whether AP recovery applies to this turn — false only on a participant's
     *         FIRST turn of the combat (2026-07-16 ruling: everyone opens on starting AP).
     */
    public boolean validateAndMarkTurnStart(Combatant c) {
        var enc = encounterFor(c);
        if (enc == null) return true;
        var current = enc.current();
        if (!current.getCombatantId().equals(c.getCombatantId())) {
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

    /** Gate a turn end (must be the current combatant, turn must be started). */
    public void validateTurnEnd(Combatant c) {
        var enc = encounterFor(c);
        if (enc == null) return;
        var current = enc.current();
        if (!current.getCombatantId().equals(c.getCombatantId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "not your turn — waiting for " + current.getName() + " (round " + enc.getRoundNumber() + ")");
        }
        if (!enc.isTurnStarted()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "start your turn before ending it");
        }
    }

    /** Advance after a successful turn end. Returns the new current entry for the log/auto-start, or null. */
    public NextTurn completeTurn(Combatant c) {
        var enc = encounterFor(c);
        if (enc == null) return null;
        advance(enc);
        repo.save(enc);
        var next = enc.current();
        return next == null ? null
                : new NextTurn(next.getCombatantId(), next.getName(), enc.getRoundNumber(), next.getCombatantType());
    }

    /** The entry whose turn begins after an advance. */
    public record NextTurn(String combatantId, String name, int round, CombatantType type) {
        /** Legacy accessor — the value is a combatant id. */
        public String playerId() { return combatantId; }
    }

    // ---- internals ----

    private int rollInitiative(Combatant c) {
        int roll = random.nextInt(20) + 1;
        return roll + c.getStats().modifier(AbilityScore.DEX) + c.getInitiativeBonus();
    }

    private RoomEncounter encounterFor(Combatant c) {
        if (c.getRoomName() == null) return null;
        var enc = repo.findById(c.getRoomName()).orElse(null);
        if (enc == null || enc.getEntries().isEmpty()) return null;
        boolean participant = enc.getEntries().stream()
                .anyMatch(e -> e.getCombatantId().equals(c.getCombatantId()));
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
            if (!surprisedNow && !isDead(current)) return;
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

    /** DEAD combatants are skipped — and so are entries whose row no longer exists
     *  (deleted mid-combat); landing on one would wedge every turn action. */
    private boolean isDead(EncounterEntry entry) {
        return lookup.status(entry.getCombatantId())
                .map(status -> status == LifeStatus.DEAD)
                .orElse(true);
    }

    private List<GameCharacter> resolvePlayers(String room, StartEncounterRequest req) {
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
                .map(e -> {
                    var combatant = lookup.find(null, e.getCombatantId()).orElse(null);
                    String status = combatant != null ? combatant.getLifeStatus().name() : null;
                    Integer hp = null, maxHp = null;
                    List<String> prepared = List.of();
                    if (combatant instanceof MonsterInstance m) {
                        hp = m.getHp().getCurrent();
                        maxHp = m.getHp().getMax();
                    } else if (combatant instanceof GameCharacter gc) {
                        // Readied reactions ride in the mirror so the GM sees who is set up.
                        prepared = gc.getPreparedReactions().stream().map(r -> r.getNote()).toList();
                    }
                    return new EncounterView.Entry(e.getCombatantId(), e.getName(), e.getInitiative(),
                            status, e.isSurprised(), e.getCombatantType(), hp, maxHp, prepared);
                })
                .toList();
        var current = enc.current();
        return new EncounterView(true, enc.getRoundNumber(),
                current != null ? current.getCombatantId() : null,
                enc.isTurnStarted(), entries);
    }
}
