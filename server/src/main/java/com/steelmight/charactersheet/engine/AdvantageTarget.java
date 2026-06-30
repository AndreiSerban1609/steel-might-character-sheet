package com.steelmight.charactersheet.engine;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum AdvantageTarget {
    OWN_ATTACKS("ownAttacks"),
    INCOMING_ATTACKS("incomingAttacks"),
    INCOMING_MELEE_ATTACKS("incomingMeleeAttacks"),
    INCOMING_RANGED_ATTACKS("incomingRangedAttacks"),
    SAVING_THROWS("savingThrows"),
    SAVING_THROW("savingThrow"),
    SKILL_CHECKS("skillChecks"),
    RANGED_ATTACKS_THROUGH("rangedAttacksThrough");

    private final String key;

    AdvantageTarget(String key) { this.key = key; }

    @JsonValue
    public String getKey() { return key; }

    @JsonCreator
    public static AdvantageTarget fromKey(String key) {
        for (var v : values()) if (v.key.equals(key)) return v;
        throw new IllegalArgumentException("Unknown AdvantageTarget: " + key);
    }
}
