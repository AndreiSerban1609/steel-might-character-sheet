package com.steelmight.charactersheet.model;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

/** The GM-defined base deck for a room (keyed by room name). Players customize on top (later slice). */
@Entity
@Table(name = "room_decks")
public class RoomDeck {

    @Id
    private String roomName;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "room_deck_neutrals", joinColumns = @JoinColumn(name = "room_name"))
    @OrderColumn(name = "idx")
    private List<TemplateCard> neutralCards = new ArrayList<>();

    private int statCount;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "room_deck_encounters", joinColumns = @JoinColumn(name = "room_name"))
    @OrderColumn(name = "idx")
    private List<TemplateCard> encounterCards = new ArrayList<>();

    protected RoomDeck() {}

    public RoomDeck(String roomName) {
        this.roomName = roomName;
    }

    public String getRoomName() { return roomName; }

    public List<TemplateCard> getNeutralCards() { return neutralCards; }
    public List<TemplateCard> getEncounterCards() { return encounterCards; }

    public int getStatCount() { return statCount; }
    public void setStatCount(int statCount) { this.statCount = statCount; }
}
