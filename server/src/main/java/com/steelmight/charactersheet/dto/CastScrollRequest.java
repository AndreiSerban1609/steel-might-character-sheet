package com.steelmight.charactersheet.dto;

/**
 * POST /actions/cast-scroll (Shops p.17: "Allows any character to cast the
 * scroll's mentioned spell without having to be a caster or consume mana").
 * The spell was written on the scroll at purchase (Game Owner 2026-07-07) —
 * the cast reads the entry's stored spell.
 *
 * @param tier              disambiguates when several entries of the scroll are carried
 * @param spellId           disambiguates scrolls carrying different spells (must match
 *                          the stored spell when given)
 * @param targetPlayerId    legacy alias of {@code targetCombatantId} (players only)
 * @param targetCombatantId who receives the spell's effects: a playerId or {@code monster:{id}}
 */
public record CastScrollRequest(
        String itemId,
        Integer tier,
        String spellId,
        Boolean applyEffectsToSelf,
        String targetPlayerId,
        String targetCombatantId
) {
    public CastScrollRequest(String itemId, Integer tier, String spellId,
                             Boolean applyEffectsToSelf, String targetPlayerId) {
        this(itemId, tier, spellId, applyEffectsToSelf, targetPlayerId, null);
    }

    public String effectsTargetId() {
        return targetCombatantId != null && !targetCombatantId.isBlank() ? targetCombatantId : targetPlayerId;
    }
}
