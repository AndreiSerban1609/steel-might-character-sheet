package com.steelmight.charactersheet.model;

import jakarta.persistence.Embeddable;

/** One participant slot in a room's turn order. Name is denormalized for display. */
@Embeddable
public class EncounterEntry {

    private String playerId;
    private String name;
    private int initiative;

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
}
