package com.steelmight.charactersheet.dto;

import java.util.List;

/**
 * Participants for a new encounter; null/empty → every character in the room.
 * surprisedPlayerIds marks the ambushed side: the encounter opens on a surprise
 * round (round 0) in which their turns are auto-skipped; round 1+ is normal.
 */
public record StartEncounterRequest(List<String> playerIds, List<String> surprisedPlayerIds) {

    public StartEncounterRequest(List<String> playerIds) {
        this(playerIds, List.of());
    }
}
