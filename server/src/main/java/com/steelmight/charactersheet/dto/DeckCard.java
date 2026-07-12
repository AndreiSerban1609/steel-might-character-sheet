package com.steelmight.charactersheet.dto;

/**
 * A configurable template card. Room rows (neutral/encounter) use only name/modifier/description;
 * player extra (CLASS) cards may also carry a skill restriction ({@code checkType}), a redraw bonus
 * ({@code redrawModifier} — the card is passed, its bonus accumulates, the draw continues), and a
 * removal mechanic ({@code removal}: "consume" until rest / "burn" permanently; {@code consumed}
 * marks a consume card currently spent).
 */
public record DeckCard(
        String name,
        int modifier,
        String description,
        String checkType,
        Integer redrawModifier,
        String removal,
        Boolean consumed
) {
    public DeckCard(String name, int modifier, String description) {
        this(name, modifier, description, null, null, null, null);
    }
}
