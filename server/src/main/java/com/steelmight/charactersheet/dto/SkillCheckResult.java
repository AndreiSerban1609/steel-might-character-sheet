package com.steelmight.charactersheet.dto;

/**
 * Result of a skill-check draw. {@code total} and {@code effectiveModifier} are null when the draw is a
 * critical (the GM decides). {@code effectiveModifier} is the ability modifier for a Stat card, else the
 * card's flat modifier. {@code proficient} means a redraw is allowed.
 */
public record SkillCheckResult(
        String skillId,
        String ability,
        Card card,
        int d10,
        Integer effectiveModifier,
        Integer total,
        boolean critical,
        boolean proficient
) {}
