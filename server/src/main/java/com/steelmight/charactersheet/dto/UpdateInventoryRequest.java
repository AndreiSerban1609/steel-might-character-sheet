package com.steelmight.charactersheet.dto;

import java.util.List;

/** Full-replace of a character's carried items and money (one generic gold currency). */
public record UpdateInventoryRequest(
        List<ItemInput> items,
        Integer gold
) {
    /** spellId: scrolls only — the spell written on the scroll (DM grant path). */
    public record ItemInput(String itemId, Integer quantity, Integer upgradeTier, Boolean equipped,
                            String spellId) {}
}
