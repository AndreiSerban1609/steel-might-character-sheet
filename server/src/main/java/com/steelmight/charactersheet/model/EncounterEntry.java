package com.steelmight.charactersheet.model;

import jakarta.persistence.Embeddable;

/** One participant slot in a room's turn order. Name is denormalized for display. */
@Embeddable
public class EncounterEntry {

    private String playerId;
    private String name;
    private int initiative;

    /** True once this character's first turn of the combat has begun — their first turn
     *  gets NO AP recovery (2026-07-16 ruling). Wrapper for legacy-row null-safety. */
    private Boolean tookTurn;

    /** Ambushed in a surprise round: skipped for the whole of round 0, normal after. */
    private Boolean surprised;

    protected EncounterEntry() {}

    public EncounterEntry(String playerId, String name, int initiative) {
        this.playerId = playerId;
        this.name = name;
        this.initiative = initiative;
    }

    public String getPlayerId() { return playerId; }
    public String getName() { return name; }
    public int getInitiative() { return initiative; }
    public void setInitiative(int initiative) { this.initiative = initiative; }

    public boolean hasTookTurn() { return Boolean.TRUE.equals(tookTurn); }
    public void setTookTurn(boolean tookTurn) { this.tookTurn = tookTurn; }

    public boolean isSurprised() { return Boolean.TRUE.equals(surprised); }
    public void setSurprised(boolean surprised) { this.surprised = surprised; }
}
