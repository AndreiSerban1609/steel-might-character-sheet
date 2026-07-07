package com.steelmight.charactersheet.engine;

import com.fasterxml.jackson.annotation.JsonValue;

public enum MechanicType {
    STAT_MODIFIER("statModifier"),
    DAMAGE_MODIFIER("damageModifier"),
    DAMAGE_ABSORB("damageAbsorb"),
    HEALING_MODIFIER("healingModifier"),
    DOT("dot"),
    HOT("hot"),
    ADVANTAGE("advantage"),
    DISADVANTAGE("disadvantage"),
    PREVENT_ACTION("preventAction"),
    IMMUNITY("immunity"),
    TRIGGER_ON_EVENT("triggerOnEvent"),
    COMPOSITE("composite"),
    REMOVE_ON_EVENT("removeOnEvent"),
    FORCED_BEHAVIOR("forcedBehavior"),
    STAND_UP_COST("standUpCost"),
    GRANT_ABILITY("grantAbility"),
    // Exhaustion ladder tiers 4 and 6 (M2-B / Q15)
    AUTO_HIT_AGAINST("autoHitAgainst"),
    DEATH("death");

    private final String jsonKey;

    MechanicType(String jsonKey) {
        this.jsonKey = jsonKey;
    }

    @JsonValue
    public String getJsonKey() {
        return jsonKey;
    }

    public static MechanicType fromJson(String key) {
        for (var v : values()) {
            if (v.jsonKey.equals(key)) return v;
        }
        throw new IllegalArgumentException("Unknown mechanic type: " + key);
    }
}
