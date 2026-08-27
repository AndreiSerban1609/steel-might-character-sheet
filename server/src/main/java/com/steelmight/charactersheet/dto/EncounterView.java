package com.steelmight.charactersheet.dto;

import com.steelmight.charactersheet.model.CombatantType;

import java.util.List;

/**
 * A room's turn-order state. {@code active} false → no encounter running (other
 * fields empty/zero). {@code currentPlayerId} is whose turn it is — a COMBATANT id
 * (playerId or {@code monster:{id}}); the field keeps its name for existing clients.
 * {@code turnStarted} tracks the strict start → end alternation. {@code round} 0 =
 * surprise round: surprised entries are auto-skipped until round 1.
 */
public record EncounterView(
        boolean active,
        int round,
        String currentPlayerId,
        boolean turnStarted,
        List<Entry> entries
) {
    /**
     * {@code playerId} is a combatant id. Monster entries carry their vitals inline
     * (ADR-001 §6: monster HP rides inside the encounter mirror slice, no extra
     * metadata keys); {@code hp}/{@code maxHp} are null for players, whose vitals live
     * in their own mirrored sheet slices.
     */
    public record Entry(String playerId, String name, int initiative, String status, boolean surprised,
                        CombatantType combatantType, Integer hp, Integer maxHp) {}

    public static EncounterView inactive() {
        return new EncounterView(false, 0, null, false, List.of());
    }
}
