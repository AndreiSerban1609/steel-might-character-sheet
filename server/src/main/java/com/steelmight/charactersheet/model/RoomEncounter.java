package com.steelmight.charactersheet.model;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

/**
 * A room's running turn order (one per room). Turn gating: only the entry at
 * {@code currentIndex} may act; {@code turnStarted} enforces the strict
 * start → end alternation. Ending a turn advances the index; wrapping past the
 * last entry increments the round.
 */
@Entity
@Table(name = "room_encounters")
public class RoomEncounter {

    @Id
    private String roomName;

    private int roundNumber;
    private int currentIndex;
    private boolean turnStarted;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "room_encounter_entries", joinColumns = @JoinColumn(name = "room_name"))
    @OrderColumn(name = "idx")
    private List<EncounterEntry> entries = new ArrayList<>();

    protected RoomEncounter() {}

    public RoomEncounter(String roomName) {
        this.roomName = roomName;
        this.roundNumber = 1;
    }

    public String getRoomName() { return roomName; }

    public int getRoundNumber() { return roundNumber; }
    public void setRoundNumber(int roundNumber) { this.roundNumber = roundNumber; }

    public int getCurrentIndex() { return currentIndex; }
    public void setCurrentIndex(int currentIndex) { this.currentIndex = currentIndex; }

    public boolean isTurnStarted() { return turnStarted; }
    public void setTurnStarted(boolean turnStarted) { this.turnStarted = turnStarted; }

    public List<EncounterEntry> getEntries() { return entries; }

    public EncounterEntry current() {
        return entries.isEmpty() ? null : entries.get(Math.floorMod(currentIndex, entries.size()));
    }
}
