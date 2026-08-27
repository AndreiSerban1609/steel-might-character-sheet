package com.steelmight.charactersheet.service;

import com.steelmight.charactersheet.dto.ActionResponse;
import com.steelmight.charactersheet.dto.ApplyEffectRequest;
import com.steelmight.charactersheet.dto.CombatSnapshot;
import com.steelmight.charactersheet.dto.CombatantView;
import com.steelmight.charactersheet.dto.DamageRequest;
import com.steelmight.charactersheet.dto.HealRequest;
import com.steelmight.charactersheet.dto.MonsterView;
import com.steelmight.charactersheet.model.MonsterInstance;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The one place a combatant id is parsed (ADR-001 §4 "CombatantResolver"): {@code monster:{id}}
 * goes to {@link MonsterService}, anything else is a playerId and goes to
 * {@link CharacterService}. Both run the same pipelines underneath; this class only picks
 * the entity and wraps the kind-specific snapshot into a {@link CombatantView}.
 */
@Service
@Transactional
public class CombatantActionService {

    private final CharacterService characters;
    private final MonsterService monsters;

    public CombatantActionService(CharacterService characters, MonsterService monsters) {
        this.characters = characters;
        this.monsters = monsters;
    }

    @Transactional(readOnly = true)
    public CombatantView get(String room, String combatantId) {
        if (MonsterInstance.isMonsterId(combatantId)) {
            return CombatantView.ofMonster(monsters.get(room, monsterId(combatantId)));
        }
        return CombatantView.ofPlayer(combatantId, characters.getCombatSnapshot(combatantId));
    }

    public ActionResponse<CombatantView> damage(String room, String combatantId, DamageRequest req) {
        if (MonsterInstance.isMonsterId(combatantId)) {
            return monster(monsters.damage(room, monsterId(combatantId), req));
        }
        return player(combatantId, characters.damage(combatantId, req));
    }

    public ActionResponse<CombatantView> heal(String room, String combatantId, HealRequest req) {
        if (MonsterInstance.isMonsterId(combatantId)) {
            return monster(monsters.heal(room, monsterId(combatantId), req));
        }
        return player(combatantId, characters.heal(combatantId, req));
    }

    public ActionResponse<CombatantView> applyEffect(String room, String combatantId, ApplyEffectRequest req) {
        if (MonsterInstance.isMonsterId(combatantId)) {
            return monster(monsters.applyEffect(room, monsterId(combatantId), req));
        }
        return player(combatantId, characters.applyEffect(combatantId, req));
    }

    public ActionResponse<CombatantView> removeEffect(String room, String combatantId, String effectId) {
        if (MonsterInstance.isMonsterId(combatantId)) {
            return monster(monsters.removeEffect(room, monsterId(combatantId), effectId));
        }
        return player(combatantId, characters.removeEffect(combatantId, effectId));
    }

    public ActionResponse<CombatantView> turnStart(String room, String combatantId) {
        if (MonsterInstance.isMonsterId(combatantId)) {
            return monster(monsters.turnStart(room, monsterId(combatantId)));
        }
        return player(combatantId, characters.turnStart(combatantId));
    }

    public ActionResponse<CombatantView> turnEnd(String room, String combatantId) {
        if (MonsterInstance.isMonsterId(combatantId)) {
            return monster(monsters.turnEnd(room, monsterId(combatantId)));
        }
        return player(combatantId, characters.turnEnd(combatantId));
    }

    // ---- internals ----

    private static Long monsterId(String combatantId) {
        Long id = MonsterInstance.parseId(combatantId);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "malformed monster id: " + combatantId);
        }
        return id;
    }

    private static ActionResponse<CombatantView> monster(ActionResponse<MonsterView> r) {
        return new ActionResponse<>(r.resolution(), CombatantView.ofMonster(r.snapshot()));
    }

    private static ActionResponse<CombatantView> player(String playerId, ActionResponse<CombatSnapshot> r) {
        return new ActionResponse<>(r.resolution(), CombatantView.ofPlayer(playerId, r.snapshot()));
    }
}
