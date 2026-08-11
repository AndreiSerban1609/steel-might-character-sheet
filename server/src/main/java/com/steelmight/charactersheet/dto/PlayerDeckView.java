package com.steelmight.charactersheet.dto;

/** What a player sees when editing their deck: the GM's room base, their config, and the
 *  resulting deck — cards in build order (draws shuffle; consumed/burned cards excluded). */
public record PlayerDeckView(DeckTemplate room, PlayerDeckConfig config, int deckSize,
                             java.util.List<Card> cards) {}
