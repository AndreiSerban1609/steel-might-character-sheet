package com.steelmight.charactersheet.dto;

import java.util.List;

/**
 * Result of a skill-check draw. {@code total} and {@code effectiveModifier} are null when the draw is a
 * critical (the GM decides). {@code effectiveModifier} is the ability modifier for a Stat card, else the
 * card's flat modifier. Proficiency grants proficiency-bonus-many redraws per check (Deck of Fates
 * gamble: the current card is forfeited, the next one is drawn from the remaining deck, and the d10
 * stays the same). {@code redrawsRemaining} counts down; 0 for non-proficient checks.
 *
 * {@code passedCards} are the cards auto-passed on THIS draw/redraw (wrong-check skips and
 * redraw-bonus cards); {@code redrawBonuses} accumulate across the whole check and their sum
 * ({@code bonusTotal}) is included in {@code total}.
 *
 * {@code advantage} ("advantage"/"disadvantage", null = normal) is chosen before the draw:
 * two d10s are rolled ({@code d10Rolls}) and {@code d10} is the higher/lower of the pair.
 * The pick is settled once per check — redraws keep it, like the die itself.
 */
public record SkillCheckResult(
        String skillId,
        String ability,
        Card card,
        int d10,
        List<Integer> d10Rolls,
        String advantage,
        Integer effectiveModifier,
        Integer total,
        boolean critical,
        boolean proficient,
        int redrawsUsed,
        int redrawsRemaining,
        List<PassedCard> passedCards,
        List<RedrawBonus> redrawBonuses,
        int bonusTotal
) {}
