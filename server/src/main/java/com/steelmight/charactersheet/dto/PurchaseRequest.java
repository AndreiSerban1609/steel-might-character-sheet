package com.steelmight.charactersheet.dto;

/**
 * POST /actions/purchase (M5-A).
 *
 * @param quantity default 1
 * @param tier     item level for tier-priced items (weapons/armor/potions), 1..20; default 1.
 *                 Caster weapons carry their level in the id — tier is rejected there.
 * @param silvered weapons only — ×5 price (pricing.json silveringMultiplier)
 * @param spellId  scrolls only, REQUIRED there: the spell written on the scroll
 *                 ("Scroll of Magic Bolt") — must match the scroll's level and caster tier
 */
public record PurchaseRequest(
        String itemId,
        Integer quantity,
        Integer tier,
        Boolean silvered,
        String spellId
) {
    public PurchaseRequest(String itemId, Integer quantity, Integer tier, Boolean silvered) {
        this(itemId, quantity, tier, silvered, null);
    }
}
