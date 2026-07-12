package com.steelmight.charactersheet.gamedata;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * An item id resolved across every catalog (M5 shared: findItem).
 *
 * @param priceTier      pricing.json tier key (weapons/armor/caster weapons); null otherwise
 * @param intrinsicLevel the item's own level when the id encodes it (caster weapons:
 *                       arcane-orb-3 → 3) — tier pricing uses it instead of upgradeTier
 * @param price          explicit copper price (consumables/magic shop/scrolls/general/mounts)
 * @param node           the raw catalog entry for kind-specific fields
 */
public record ResolvedItem(
        String id,
        String name,
        ItemKind kind,
        String priceTier,
        Integer intrinsicLevel,
        Integer price,
        JsonNode node
) {}
