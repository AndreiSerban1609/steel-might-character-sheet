package com.steelmight.charactersheet.model;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * One participant slot in a room's turn order. Name is denormalized for display.
 * {@code playerId} keeps its column name for legacy rows but holds a COMBATANT id:
 * a playerId, or {@code monster:{id}} (ADR-001 §4).
 */
@Embeddable
public class EncounterEntry {

    private String playerId;
    private String name;
    private int initiative;
    /** True once this combatant's first turn of the combat has begun — their first turn
     *  gets NO AP recovery (2026-07-16 ruling). Wrapper for legacy-row null-safety. */
    private Boolean tookTurn;
    /** Ambushed in a surprise round: skipped for the whole of round 0, normal after. */
    private Boolean surprised;
    /** Nullable → PLAYER for rows written before monsters existed. */
    @Enumerated(EnumType.STRING)
    private CombatantType combatantType;

    protected EncounterEntry() {}

    public EncounterEntry(String combatantId, String name, int initiative) {
        this(combatantId, name, initiative, CombatantType.of(combatantId));
    }

    public EncounterEntry(String combatantId, String name, int initiative, CombatantType type) {
        this.playerId = combatantId;
        this.name = name;
        this.initiative = initiative;
        this.combatantType = type;
    }

    /** Legacy accessor — the value is a combatant id. */
    public String getPlayerId() { return playerId; }
    public String getCombatantId() { return playerId; }
    public String getName() { return name; }
    public int getInitiative() { return initiative; }
    public void setInitiative(int initiative) { this.initiative = initiative; }
    public boolean hasTookTurn() { return Boolean.TRUE.equals(tookTurn); }
    public void setTookTurn(boolean tookTurn) { this.tookTurn = tookTurn; }
    public boolean isSurprised() { return Boolean.TRUE.equals(surprised); }
    public void setSurprised(boolean surprised) { this.surprised = surprised; }
    public CombatantType getCombatantType() {
        return combatantType != null ? combatantType : CombatantType.of(playerId);
    }
    public boolean isMonster() { return getCombatantType() == CombatantType.MONSTER; }
}
