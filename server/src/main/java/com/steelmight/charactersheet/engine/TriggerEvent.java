package com.steelmight.charactersheet.engine;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TriggerEvent {
    START_OF_TURN("startOfTurn"),
    END_OF_TURN("endOfTurn"),
    ON_APPLY("onApply"),
    BEING_ATTACKED("beingAttacked"),
    HIT_BY_MELEE("hitByMelee"),
    TAKE_DAMAGE("takeDamage"),
    REACH_ZERO_HP("reachZeroHp"),
    DEAL_SINGLE_TARGET_DAMAGE("dealSingleTargetDamage"),
    TERRIFY_ATTEMPT("terrifyAttempt"),
    SOURCE_LEAVES_VISION("sourceLeavesVision"),
    HARMED_BY_SOURCE("harmedBySource"),
    HOSTILE_ACTION("hostileAction");

    private final String key;

    TriggerEvent(String key) { this.key = key; }

    @JsonValue
    public String getKey() { return key; }

    @JsonCreator
    public static TriggerEvent fromKey(String key) {
        for (var v : values()) if (v.key.equals(key)) return v;
        throw new IllegalArgumentException("Unknown TriggerEvent: " + key);
    }
}
