package com.steelmight.charactersheet.dto;

import java.util.List;

/**
 * POST /actions/cast (M4-A; effects targeting M4-C; components M4-D).
 *
 * @param spellId             id from spells-*.json
 * @param castAtLevel         optional upcast level; defaults to the spell's own level
 * @param applyEffectsToSelf  apply the spell's effects[] to the caster (M4-C)
 * @param targetPlayerId      legacy alias of {@code targetCombatantId} (players only) —
 *                            kept for one deploy cycle so an older sheet keeps working
 * @param targetCombatantId   apply the spell's effects[] to this combatant (a playerId or
 *                            {@code monster:{id}}, Story 2.3) — caster pays the costs, the
 *                            target receives the effects in the same transaction
 * @param componentsAvailable which components the caster can currently provide
 *                            (M4-D); null means all of them
 */
public record CastRequest(
        String spellId,
        Integer castAtLevel,
        Boolean applyEffectsToSelf,
        String targetPlayerId,
        String targetCombatantId,
        List<String> componentsAvailable
) {
    public CastRequest(String spellId, Integer castAtLevel) {
        this(spellId, castAtLevel, null, null, null, null);
    }
    public CastRequest(String spellId, Integer castAtLevel, Boolean applyEffectsToSelf,
                       String targetPlayerId) {
        this(spellId, castAtLevel, applyEffectsToSelf, targetPlayerId, null, null);
    }
    public CastRequest(String spellId, Integer castAtLevel, Boolean applyEffectsToSelf,
                       String targetPlayerId, List<String> componentsAvailable) {
        this(spellId, castAtLevel, applyEffectsToSelf, targetPlayerId, null, componentsAvailable);
    }

    /** The effects target: the new combatant id wins, else the legacy player id. */
    public String effectsTargetId() {
        return targetCombatantId != null && !targetCombatantId.isBlank() ? targetCombatantId : targetPlayerId;
    }
}
