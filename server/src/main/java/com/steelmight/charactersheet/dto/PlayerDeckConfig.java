package com.steelmight.charactersheet.dto;

import java.util.List;

/** A player's deck customization: a net change to Stat cards, plus their own extra cards. */
public record PlayerDeckConfig(int statAdjust, List<DeckCard> extraCards) {}
