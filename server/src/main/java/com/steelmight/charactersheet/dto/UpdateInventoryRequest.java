package com.steelmight.charactersheet.dto;

import java.util.List;

/** Full-replace of a character's carried items and gold. */
public record UpdateInventoryRequest(
        List<ItemInput> items,
        Integer gold
) {
    public record ItemInput(String itemId, Integer quantity, Integer upgradeTier, Boolean equipped) {}
}
