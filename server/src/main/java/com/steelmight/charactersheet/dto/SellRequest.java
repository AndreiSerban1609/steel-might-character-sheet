package com.steelmight.charactersheet.dto;

/**
 * POST /actions/sell (M5-A) — credits half the item's current price.
 *
 * @param tier    disambiguates when the character carries the item at several tiers
 * @param spellId disambiguates scrolls carrying different spells
 */
public record SellRequest(
        String itemId,
        Integer quantity,
        Integer tier,
        String spellId
) {
    public SellRequest(String itemId, Integer quantity, Integer tier) {
        this(itemId, quantity, tier, null);
    }
}
