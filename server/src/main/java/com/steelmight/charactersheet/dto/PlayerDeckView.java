package com.steelmight.charactersheet.dto;

/** What a player sees when editing their deck: the GM's room base, their config, and the resulting size. */
public record PlayerDeckView(DeckTemplate room, PlayerDeckConfig config, int deckSize) {}
