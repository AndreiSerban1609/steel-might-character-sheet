package com.steelmight.charactersheet.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * stacks: null defaults to 1; explicit 0/negative is rejected (400) by the engine.
 * duration: explicit active window in rounds — a direct application that bypasses
 * threshold accumulation (M0-A R3b).
 * duringOwnTurn: player cleanse window (N9) — the active window survives the current
 * end-of-turn decrement and expires at the end of the next turn.
 * bypassImmunity: skip immunity/warded/shield-exclusivity (system/self-inflicted effects, M2-A).
 * replaceExistingShield: destroy the active shield instead of rejecting the incoming one (Q08).
 * durationType: explicit expiry model (M3) — UNTIL_LONG_REST effects clear on the next rest.
 */
public record ApplyEffectRequest(
        @NotBlank String effectId,
        Integer stacks,
        Integer value,
        Integer duration,
        String source,
        boolean duringOwnTurn,
        boolean bypassImmunity,
        boolean replaceExistingShield,
        com.steelmight.charactersheet.model.DurationType durationType
) {}
