package com.steelmight.charactersheet.model;

import jakarta.persistence.*;

@Entity
@Table(name = "inventory_entries")
public class InventoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id")
    private GameCharacter character;

    private String itemId;
    private int quantity;
    private int upgradeTier;
    private boolean equipped;

    protected InventoryEntry() {}

    public InventoryEntry(String itemId, int quantity, int upgradeTier, boolean equipped) {
        this.itemId = itemId;
        this.quantity = quantity;
        this.upgradeTier = upgradeTier;
        this.equipped = equipped;
    }

    public Long getId() { return id; }

    public GameCharacter getCharacter() { return character; }
    public void setCharacter(GameCharacter character) { this.character = character; }

    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public int getUpgradeTier() { return upgradeTier; }
    public void setUpgradeTier(int upgradeTier) { this.upgradeTier = upgradeTier; }

    public boolean isEquipped() { return equipped; }
    public void setEquipped(boolean equipped) { this.equipped = equipped; }
}
