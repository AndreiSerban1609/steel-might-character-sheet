package com.steelmight.charactersheet.engine;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PreventableAction {
    MOVEMENT("movement"),
    ALL_MOVEMENT("allMovement"),
    WEAPON_ATTACK("weaponAttack"),
    VERBAL_SPELL("verbalSpell"),
    SPEECH("speech"),
    ATTACK_OF_OPPORTUNITY("attackOfOpportunity"),
    ALL("all"),
    ACT_BEFORE_STANDING("actBeforeStanding"),
    LEAVE_AREA("leaveArea");

    private final String key;

    PreventableAction(String key) { this.key = key; }

    @JsonValue
    public String getKey() { return key; }

    @JsonCreator
    public static PreventableAction fromKey(String key) {
        for (var v : values()) if (v.key.equals(key)) return v;
        throw new IllegalArgumentException("Unknown PreventableAction: " + key);
    }
}
