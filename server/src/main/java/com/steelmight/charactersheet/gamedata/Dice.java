package com.steelmight.charactersheet.gamedata;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * A dice term. The data carries two spellings — "2d10" strings in most files,
 * {"count": 2, "sides": 10} objects throughout spells-disciple.json.
 */
public record Dice(int count, int sides) {

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static Dice from(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isObject()) {
            return new Dice(node.path("count").asInt(), node.path("sides").asInt());
        }
        String text = node.asText().trim().toLowerCase();
        int d = text.indexOf('d');
        if (d < 1) {
            throw new IllegalArgumentException("Unparseable dice term: '" + text + "'");
        }
        return new Dice(Integer.parseInt(text.substring(0, d)),
                Integer.parseInt(text.substring(d + 1)));
    }

    @Override
    public String toString() {
        return count + "d" + sides;
    }
}
