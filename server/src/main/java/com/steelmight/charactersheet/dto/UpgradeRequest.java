package com.steelmight.charactersheet.dto;

/**
 * POST /actions/upgrade (M5-C).
 *
 * @param tier disambiguates when the character carries the item at several levels
 * @param mode "kit" (cheaper, d20 roll, cost lost on failure — Q31) or
 *             "blacksmith" (price difference + 5% surcharge, guaranteed)
 */
public record UpgradeRequest(
        String itemId,
        Integer tier,
        String mode
) {}
