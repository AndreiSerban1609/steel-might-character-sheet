package com.steelmight.charactersheet.dto;

import java.util.List;

public record InventorySnapshot(
        List<ItemView> items,
        /** ONE generic currency (Game Owner 2026-07-06) — all shop prices are in it. */
        int gold,
        double carriedSpace,
        int carryCapacity
) {
    public record ItemView(String itemId, int quantity, int upgradeTier, boolean equipped,
                           boolean silvered, double space, Integer chargesRemaining,
                           /** scrolls: the spell written on this scroll */
                           String spellId) {}
}
