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
        List<Entry> entries,
        /** XP banked from kills so far, split among the players when the combat ends (2026-08-27). */
        int xpPool
) {
    /**
     * {@code playerId} is a combatant id. Monster entries carry their vitals inline
     * (ADR-001 §6: monster HP rides inside the encounter mirror slice, no extra
     * metadata keys); {@code hp}/{@code maxHp} are null for players, whose vitals live
     * in their own mirrored sheet slices.
     */
    /** {@code prepared}: the notes of a player's readied reactions (2026-08-27); empty for monsters. */
    public record Entry(String playerId, String name, int initiative, String status, boolean surprised,
                        CombatantType combatantType, Integer hp, Integer maxHp, List<String> prepared) {}

    public static EncounterView inactive() {
        return new EncounterView(false, 0, null, false, List.of(), 0);
    }
}
