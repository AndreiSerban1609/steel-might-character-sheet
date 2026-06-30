package com.steelmight.charactersheet.engine;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ModifiableStat {
    // Core defense
    AC("ac"),
    PA("pa"),
    MA("ma"),

    // Movement
    SPEED("speed"),
    MAX_RANGE("maxRange"),

    // Action Points
    AP_RECOVERY("apRecovery"),
    AP_START("apStart"),
    AP_COST("apCost"),
    WEAPON_AP_COST("weaponApCost"),
    SPELL_AP_COST("spellApCost"),

    // Offense
    ATTACK_BONUS("attackBonus"),
    CRIT_RANGE("critRange"),

    // Defense
    SAVE_BONUS("saveBonus"),
    WILL_SAVE("willSave"),

    // Spellcasting
    SPELL_DC("spellDC"),
    MANA_COST("manaCost");

    private final String key;

    ModifiableStat(String key) { this.key = key; }

    @JsonValue
    public String getKey() { return key; }

    @JsonCreator
    public static ModifiableStat fromKey(String key) {
        for (var v : values()) if (v.key.equals(key)) return v;
        throw new IllegalArgumentException("Unknown ModifiableStat: " + key);
    }
}
