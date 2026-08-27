package com.steelmight.charactersheet.dto;

/**
 * Snapshot returned by the combatant action routes (ADR-001 §4): exactly one of
 * {@code character} / {@code monster} is set, by {@code type}. Players keep their full
 * CombatSnapshot so the sheet's existing party-targeting refresh path works unchanged.
 */
public record CombatantView(
        String type,
        String combatantId,
        String name,
        CombatSnapshot character,
        MonsterView monster
) {
    public static final String PLAYER = "PLAYER";
    public static final String MONSTER = "MONSTER";

    public static CombatantView ofPlayer(String playerId, CombatSnapshot snapshot) {
        return new CombatantView(PLAYER, playerId, snapshot.name(), snapshot, null);
    }

    public static CombatantView ofMonster(MonsterView monster) {
        return new CombatantView(MONSTER, monster.combatantId(), monster.name(), null, monster);
    }
}
