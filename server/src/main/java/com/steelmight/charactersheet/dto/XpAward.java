package com.steelmight.charactersheet.dto;

import java.util.List;

/**
 * What a finished combat paid out (ruling 2026-08-27): the banked pool, split evenly
 * (floored) among every player who was in the turn order. {@code awarded} carries each
 * recipient's new total and whether it unlocked a level (the level-up flow itself stays
 * manual — it has choices to make).
 */
public record XpAward(
        int total,
        int recipients,
        int perPlayer,
        List<Recipient> awarded
) {
    public record Recipient(String playerId, String name, int gained, int xp, int level,
                            Integer xpToNext, boolean levelAvailable) {}

    public static XpAward none() {
        return new XpAward(0, 0, 0, List.of());
    }
}
