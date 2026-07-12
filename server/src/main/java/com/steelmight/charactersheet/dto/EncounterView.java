package com.steelmight.charactersheet.dto;

import java.util.List;

/**
 * A room's turn-order state. {@code active} false → no encounter running (other
 * fields empty/zero). {@code currentPlayerId} is whose turn it is; {@code turnStarted}
 * tracks the strict start → end alternation.
 */
public record EncounterView(
        boolean active,
        int round,
        String currentPlayerId,
        boolean turnStarted,
        List<Entry> entries
) {
    public record Entry(String playerId, String name, int initiative, String status) {}

    public static EncounterView inactive() {
        return new EncounterView(false, 0, null, false, List.of());
    }
}
