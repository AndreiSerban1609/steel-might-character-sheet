package com.steelmight.charactersheet.dto;

import java.util.List;

/**
 * POST /actions/cast (M4-A; effects targeting M4-C; components M4-D).
 *
 * @param spellId             id from spells-*.json
 * @param castAtLevel         optional upcast level; defaults to the spell's own level
 * @param applyEffectsToSelf  apply the spell's effects[] to the caster (M4-C)
 * @param targetPlayerId      apply the spell's effects[] to this character instead —
 *                            caster pays the costs, the target receives the effects in
 *                            the same transaction (party targeting, Game Owner 2026-07-03)
 * @param componentsAvailable which components the caster can currently provide
 *                            (M4-D); null means all of them
 */
public record CastRequest(
        String spellId,
        Integer castAtLevel,
        Boolean applyEffectsToSelf,
        String targetPlayerId,
        List<String> componentsAvailable
) {
    public CastRequest(String spellId, Integer castAtLevel) {
        this(spellId, castAtLevel, null, null, null);
    }

    public CastRequest(String spellId, Integer castAtLevel, Boolean applyEffectsToSelf,
                       String targetPlayerId) {
        this(spellId, castAtLevel, applyEffectsToSelf, targetPlayerId, null);
    }
}
