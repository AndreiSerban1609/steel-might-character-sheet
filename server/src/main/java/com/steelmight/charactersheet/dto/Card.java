package com.steelmight.charactersheet.dto;

/**
 * A single deck card as drawn. {@code modifier} is null for criticals and Stat cards (resolved at
 * draw time). CLASS cards may carry {@code checkType} (skill restriction), {@code redrawModifier}
 * (pass + accumulate bonus), and {@code classCardIndex} (position in the player's extraCards list,
 * used to apply consume/burn on accept).
 */
public record Card(
        CardType type,
        String name,
        Integer modifier,
        String description,
        String checkType,
        Integer redrawModifier,
        Integer classCardIndex,
        String removal
) {
    public Card(CardType type, String name, Integer modifier, String description) {
        this(type, name, modifier, description, null, null, null, null);
    }
}
