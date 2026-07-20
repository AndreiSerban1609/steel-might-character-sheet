package com.steelmight.charactersheet.dto;

import java.util.List;

/**
 * A player's deck customization: a net change to Stat cards, their own extra cards, and
 * room Encounter cards they've opted out of — matched by card NAME (case-insensitive),
 * so a GM reshaping the room deck can't silently shift which card is disabled.
 */
public record PlayerDeckConfig(int statAdjust, List<DeckCard> extraCards, List<String> disabledEncounters) {

    public PlayerDeckConfig {
        disabledEncounters = disabledEncounters != null ? disabledEncounters : List.of();
    }

    public PlayerDeckConfig(int statAdjust, List<DeckCard> extraCards) {
        this(statAdjust, extraCards, List.of());
    }
}
