package com.steelmight.charactersheet.model;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

/** A player's customization layered on top of their room's base deck (keyed by playerId). */
@Entity
@Table(name = "player_decks")
public class PlayerDeck {

    @Id
    private String playerId;

    private int statAdjust;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "player_deck_extras", joinColumns = @JoinColumn(name = "player_id"))
    @OrderColumn(name = "idx")
    private List<TemplateCard> extraCards = new ArrayList<>();

    protected PlayerDeck() {}

    public PlayerDeck(String playerId) {
        this.playerId = playerId;
    }

    public String getPlayerId() { return playerId; }

    public int getStatAdjust() { return statAdjust; }
    public void setStatAdjust(int statAdjust) { this.statAdjust = statAdjust; }

    public List<TemplateCard> getExtraCards() { return extraCards; }
}
