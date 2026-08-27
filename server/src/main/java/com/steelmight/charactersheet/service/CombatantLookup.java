package com.steelmight.charactersheet.service;

import com.steelmight.charactersheet.model.Combatant;
import com.steelmight.charactersheet.model.GameCharacter;
import com.steelmight.charactersheet.model.LifeStatus;
import com.steelmight.charactersheet.model.MonsterInstance;
import com.steelmight.charactersheet.repository.CharacterRepository;
import com.steelmight.charactersheet.repository.MonsterInstanceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

/**
 * Resolves a combatant id to its entity and persists either kind (ADR-001 §4
 * "CombatantResolver"). Players are global by playerId; monsters are room-scoped, so a
 * room is required to find one. Depends on repositories only — safe for every service.
 */
@Service
public class CombatantLookup {

    private final CharacterRepository characters;
    private final MonsterInstanceRepository monsters;

    private final XpService xp;

    public CombatantLookup(CharacterRepository characters, MonsterInstanceRepository monsters, XpService xp) {
        this.xp = xp;
        this.characters = characters;
        this.monsters = monsters;
    }

    /** @param room required to resolve monsters; players resolve regardless of room. */
    public Optional<Combatant> find(String room, String combatantId) {
        if (combatantId == null || combatantId.isBlank()) return Optional.empty();
        if (MonsterInstance.isMonsterId(combatantId)) {
            Long id = MonsterInstance.parseId(combatantId);
            if (id == null) return Optional.empty();
            return (room == null ? monsters.findById(id) : monsters.findByIdAndRoomName(id, room))
                    .map(m -> m);
        }
        return characters.findById(combatantId).map(c -> c);
    }

    public Combatant require(String room, String combatantId) {
        return find(room, combatantId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                MonsterInstance.isMonsterId(combatantId)
                        ? "no monster " + combatantId + (room != null ? " in room '" + room + "'" : "")
                        : "Character not found"));
    }

    /** Life status of whatever the id names; empty when the row is gone (deleted mid-combat). */
    public Optional<LifeStatus> status(String combatantId) {
        return find(null, combatantId).map(Combatant::getLifeStatus);
    }

    public void save(Combatant combatant) {
        if (combatant instanceof GameCharacter c) characters.save(c);
        else if (combatant instanceof MonsterInstance m) {
            xp.creditKill(m); // a kill from any path (targeted attack, DoT tick) banks its XP once
            monsters.save(m);
        }
        else throw new IllegalArgumentException("unknown combatant kind: " + combatant.getClass());
    }
}
