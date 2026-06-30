package com.steelmight.charactersheet.dto;

import java.util.List;

/** The GM's base deck for a room: neutral cards, a count of Stat cards, and encounter cards. */
public record DeckTemplate(List<DeckCard> neutralCards, int statCount, List<DeckCard> encounterCards) {}
