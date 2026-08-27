package com.steelmight.charactersheet.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * A reaction the player set up on their own turn ("I prepare to roll out of the way",
 * "I extinguish my torch when X happens") — 2026-08-27 Game Owner: the prep costs AP on
 * the turn it is declared. The server only holds the declaration so the whole table can
 * see it; the trigger and its outcome are adjudicated at the table (the reaction-window
 * state machine, ADR-001 §8, is not built). Expires unused at the start of the
 * preparer's next turn.
 */
@Embeddable
public class PreparedReaction {
    /** Max length is also the OBR metadata budget guard — the note rides in the encounter mirror. */
    public static final int MAX_NOTE_LENGTH = 120;

    @Column(name = "reaction_note", length = MAX_NOTE_LENGTH)
    private String note;
    @Column(name = "reaction_ap_cost")
    private int apCost;

    protected PreparedReaction() {}

    public PreparedReaction(String note, int apCost) {
        this.note = note;
        this.apCost = apCost;
    }

    public String getNote() { return note; }
    public int getApCost() { return apCost; }
}
