package com.steelmight.charactersheet.engine;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Timing {
    START_OF_TURN("startOfTurn"),
    END_OF_TURN("endOfTurn");

    private final String key;

    Timing(String key) { this.key = key; }

    @JsonValue
    public String getKey() { return key; }

    @JsonCreator
    public static Timing fromKey(String key) {
        for (var v : values()) if (v.key.equals(key)) return v;
        throw new IllegalArgumentException("Unknown Timing: " + key);
    }
}
