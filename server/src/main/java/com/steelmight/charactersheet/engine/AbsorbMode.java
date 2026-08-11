package com.steelmight.charactersheet.engine;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum AbsorbMode {
    INSTANCES("instances"),
    TEMP_HP("tempHp"),
    MAGIC_SHIELD("magicShield"),
    PHYSICAL_SHIELD("physicalShield"),
    PREVENT_LETHAL("preventLethal"),
    PERCENT_CAP("percentCap"),
    PERCENT_CHANCE("percentChance");

    private final String key;

    AbsorbMode(String key) { this.key = key; }

    @JsonValue
    public String getKey() { return key; }

    @JsonCreator
    public static AbsorbMode fromKey(String key) {
        for (var v : values()) if (v.key.equals(key)) return v;
        throw new IllegalArgumentException("Unknown AbsorbMode: " + key);
    }
}
