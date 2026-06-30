package com.steelmight.charactersheet.dto;

import java.util.List;

public record InventorySnapshot(
        List<ItemView> items,
        int gold,
        double carriedSpace,
        int carryCapacity
) {
    public record ItemView(String itemId, int quantity, int upgradeTier, boolean equipped, double space) {}
}
