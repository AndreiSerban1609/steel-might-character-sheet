package com.steelmight.charactersheet.engine;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum GrantableAbility {
    INVISIBLE("invisible"),
    REROLL_ONES("rerollOnes"),
    TELEPATHY("telepathy"),
    NO_VERBAL_COMPONENTS("noVerbalComponents"),
    AUTO_CRIT_ON_STUNNED_OR_PRONE("autoCritOnStunnedOrProne");

    private final String key;

    GrantableAbility(String key) { this.key = key; }

    @JsonValue
    public String getKey() { return key; }

    @JsonCreator
    public static GrantableAbility fromKey(String key) {
        for (var v : values()) if (v.key.equals(key)) return v;
        throw new IllegalArgumentException("Unknown GrantableAbility: " + key);
    }
}
