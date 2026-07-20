package com.steelmight.charactersheet.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One line of the room's activity log: who did what, when. The table is trusted —
 * the server can't stop a player dismissing their own stun, but everything lands
 * here for the GM to review.
 */
@Entity
@Table(name = "audit_entries")
public class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String roomName;
    private String playerId;
    private String characterName;

    /** Short action slug: "remove-effect", "damage", "rest", "use-ability", … */
    private String action;

    /** One-line human summary: "Removed burning", "Took 20 SLASHING damage", … */
    @jakarta.persistence.Column(length = 300) // must fit AuditService.MAX_SUMMARY
    private String summary;

    private Instant createdAt;

    protected AuditEntry() {}

    public AuditEntry(String roomName, String playerId, String characterName,
                      String action, String summary) {
        this.roomName = roomName;
        this.playerId = playerId;
        this.characterName = characterName;
        this.action = action;
        this.summary = summary;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getRoomName() { return roomName; }
    public String getPlayerId() { return playerId; }
    public String getCharacterName() { return characterName; }
    public String getAction() { return action; }
    public String getSummary() { return summary; }
    public Instant getCreatedAt() { return createdAt; }
}
