package com.steelmight.charactersheet.gamedata;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * An apCost/manaCost from spells-*.json. Usually a plain int, but the data also
 * contains "10%" (paladin auras: percent of the caster's max mana) and free-text
 * AP costs ("reaction" on intercept, "1 or 2" on glyph-of-danger) that only the
 * DM can adjudicate.
 */
public record SpellCost(Integer flat, Integer percentOfMax, String special) {

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static SpellCost from(JsonNode node) {
        if (node == null || node.isNull()) return new SpellCost(0, null, null);
        if (node.isNumber()) return new SpellCost(node.asInt(), null, null);
        String text = node.asText().trim();
        if (text.endsWith("%")) {
            try {
                int percent = Integer.parseInt(text.substring(0, text.length() - 1).trim());
                return new SpellCost(null, percent, null);
            } catch (NumberFormatException ignored) {
                // fall through to special
            }
        }
        return new SpellCost(null, null, text);
    }

    public boolean isSpecial() {
        return special != null;
    }

    /** Flat value, or percentOfMax resolved against the given pool maximum. */
    public int resolve(int poolMax) {
        if (flat != null) return flat;
        if (percentOfMax != null) return poolMax * percentOfMax / 100;
        throw new IllegalStateException("special cost '" + special + "' cannot be resolved");
    }
}
