package com.steelmight.charactersheet.engine;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TriggerAction {
    APPLY_EFFECT("applyEffect"),
    DEAL_DAMAGE("dealDamage"),
    SET_HP_TO_1("setHpTo1"),
    HEAL_PERCENT("healPercent"),
    REMOVE_EFFECT("removeEffect"),
    GRANT_ENRAGED("grantEnraged");

    private final String key;

    TriggerAction(String key) { this.key = key; }

    @JsonValue
    public String getKey() { return key; }

    @JsonCreator
    public static TriggerAction fromKey(String key) {
        for (var v : values()) if (v.key.equals(key)) return v;
        throw new IllegalArgumentException("Unknown TriggerAction: " + key);
    }
}
