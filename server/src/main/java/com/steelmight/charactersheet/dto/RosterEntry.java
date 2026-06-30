package com.steelmight.charactersheet.dto;

/** Compact per-character row for the GM roster view. */
public record RosterEntry(
        String playerId,
        String roomName,
        String email,
        String name,
        int level,
        String pathId,
        String classId,
        int currentHp,
        int maxHp,
        int ac
) {}
