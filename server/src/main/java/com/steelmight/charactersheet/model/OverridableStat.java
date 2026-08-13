package com.steelmight.charactersheet.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Derived stats the GM may pin to a literal value (demo feedback #11/#12).
 *
 * The architecture's "computed values are never stored" rule stands: these are not
 * cached derivations, they are declared inputs that REPLACE a formula the rules don't
 * cover yet. Everything downstream of the formula — active effects, armor, talents —
 * still applies on top, so a pinned AC of 18 still drops when the character is exposed.
 *
 * Deliberately a closed enum rather than a free string map: a typo'd key would silently
 * do nothing, which is the worst failure mode for an escape hatch.
 */
public enum OverridableStat {
    MAX_HP("maxHp"),
    MAX_MANA("maxMana"),
    AC("ac"),
    PA("pa"),
    MA("ma"),
    SPEED("speed"),
    AP_RECOVERY("apRecovery"),
    MAX_AP("maxAp"),
    CARRY_CAPACITY("carryCapacity");

    private final String key;

    OverridableStat(String key) { this.key = key; }

    @JsonValue
    public String getKey() { return key; }

    @JsonCreator
    public static OverridableStat fromKey(String key) {
        for (var v : values()) if (v.key.equals(key)) return v;
        throw new IllegalArgumentException("Unknown overridable stat: " + key);
    }
}
