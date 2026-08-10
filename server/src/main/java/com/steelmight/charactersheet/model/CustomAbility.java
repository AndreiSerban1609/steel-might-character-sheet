package com.steelmight.charactersheet.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * A player-written ability (2026-07-20 Game Owner ruling): free text until the
 * official extraction/ruling lands, then replaced by structured data. The server
 * stores and prints it — costs and outcomes are adjudicated at the table.
 */
@Embeddable
public class CustomAbility {

    @Column(name = "ability_name", length = 100)
    private String name;

    @Column(name = "ability_text", length = 4000)
    private String text;

    /** Optional flat AP cost — the one structured bit; null means the table adjudicates. */
    @Column(name = "ability_ap_cost")
    private Integer apCost;

    protected CustomAbility() {}

    public CustomAbility(String name, String text, Integer apCost) {
        this.name = name;
        this.text = text;
        this.apCost = apCost;
    }

    public String getName() { return name; }
    public String getText() { return text; }
    public Integer getApCost() { return apCost; }
}
