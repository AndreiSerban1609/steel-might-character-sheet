package com.steelmight.charactersheet.dto;

/**
 * POST /actions/use-consumable (M5-C).
 *
 * @param tier disambiguates when the character carries the item at several levels
 */
public record UseConsumableRequest(
        String itemId,
        Integer tier
) {}
