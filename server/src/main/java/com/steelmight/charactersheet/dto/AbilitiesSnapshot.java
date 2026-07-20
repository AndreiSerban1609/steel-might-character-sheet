package com.steelmight.charactersheet.dto;

import java.util.List;

/**
 * A character's usable abilities (Story 1.3). `known` is the effective set:
 * group-null abilities (class-granted) are implicit; `picked` holds only the
 * choice-group picks the player edited (the free-form picker ruling).
 * The ability catalog itself is bundled data — the frontend joins by id.
 */
public record AbilitiesSnapshot(
        String classId,
        List<String> known,
        List<String> picked,
        // Budget status for limit-bearing known abilities (per-rest/per-turn) — the server
        // computes maxUses formulas (flat or stat-mod-with-min); the frontend only displays.
        List<AbilityUseView> uses,
        // Player-written free-text abilities pending official rulings (2026-07-20) —
        // the table adjudicates costs and outcomes; the sheet stores and prints them.
        List<CustomAbilityView> custom
) {
    /** The perRest / perTurn pair is null when the ability has no limit of that kind. */
    public record AbilityUseView(
            String abilityId,
            Integer perRestRemaining,
            Integer perRestMax,
            Integer perTurnRemaining,
            Integer perTurnMax
    ) {}

    public record CustomAbilityView(String name, String text) {}
}
