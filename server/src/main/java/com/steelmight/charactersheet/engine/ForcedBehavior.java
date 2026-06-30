package com.steelmight.charactersheet.engine;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ForcedBehavior {
    MUST_TARGET_SOURCE("mustTargetSource"),
    FLEE_FROM_SOURCE("fleeFromSource"),
    TREAT_ALL_AS_ENEMIES("treatAllAsEnemies"),
    TREAT_SOURCE_AS_ALLY("treatSourceAsAlly"),
    PUSHED_AWAY("pushedAway");

    private final String key;

    ForcedBehavior(String key) { this.key = key; }

    @JsonValue
    public String getKey() { return key; }

    @JsonCreator
    public static ForcedBehavior fromKey(String key) {
        for (var v : values()) if (v.key.equals(key)) return v;
        throw new IllegalArgumentException("Unknown ForcedBehavior: " + key);
    }
}
