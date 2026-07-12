package com.steelmight.charactersheet.dto;

/**
 * POST /actions/cast-scroll (Shops p.17: "Allows any character to cast the
 * scroll's mentioned spell without having to be a caster or consume mana").
 * The spell was written on the scroll at purchase (Game Owner 2026-07-07) —
 * the cast reads the entry's stored spell.
 *
 * @param tier    disambiguates when several entries of the scroll are carried
 * @param spellId disambiguates scrolls carrying different spells (must match
 *                the stored spell when given)
 */
public record CastScrollRequest(
        String itemId,
        Integer tier,
        String spellId,
        Boolean applyEffectsToSelf,
        String targetPlayerId
) {}
