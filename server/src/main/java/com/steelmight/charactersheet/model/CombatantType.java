package com.steelmight.charactersheet.model;

/** Which kind of entity a combatant id names (ADR-001 §4). */
public enum CombatantType {
    PLAYER,
    MONSTER;

    public static CombatantType of(String combatantId) {
        return MonsterInstance.isMonsterId(combatantId) ? MONSTER : PLAYER;
    }
}
