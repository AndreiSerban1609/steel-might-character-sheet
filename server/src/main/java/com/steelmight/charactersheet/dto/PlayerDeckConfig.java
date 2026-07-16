package com.steelmight.charactersheet.dto;

import java.util.List;

/**
 * A player's deck customization: a net change to Stat cards, their own extra cards, and
 * room Encounter cards they've opted out of (by index into the room template's list —
 * indices go stale if the GM edits the room deck, trusted-table risk like classCardIndex).
 */
public record PlayerDeckConfig(int statAdjust, List<DeckCard> extraCards, List<Integer> disabledEncounters) {

    public PlayerDeckConfig {
        disabledEncounters = disabledEncounters != null ? disabledEncounters : List.of();
    }

    public PlayerDeckConfig(int statAdjust, List<DeckCard> extraCards) {
        this(statAdjust, extraCards, List.of());
    }
}
