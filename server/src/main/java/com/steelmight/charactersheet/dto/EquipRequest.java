package com.steelmight.charactersheet.dto;

/**
 * POST /actions/equip and /actions/unequip (M5-B).
 *
 * @param tier disambiguates when the character carries the item at several tiers
 */
public record EquipRequest(
        String itemId,
        Integer tier
) {}
