package com.steelmight.charactersheet.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Per-ability use bookkeeping (Story 1.4) — only tracked for abilities that declare
 * usesPerRest / usesPerTurn limits. usedThisTurn clears on turn start; usedThisRest
 * REDUCES by ceil(tier% × maxUses) on rest (2026-07-13 ruling), it does not clear.
 */
@Embeddable
public class AbilityUse {

    @Column(name = "ability_id")
    private String abilityId;

    @Column(name = "used_this_rest")
    private int usedThisRest;

    @Column(name = "used_this_turn")
    private int usedThisTurn;

    protected AbilityUse() {}

    public AbilityUse(String abilityId) {
        this.abilityId = abilityId;
    }

    public String getAbilityId() { return abilityId; }

    public int getUsedThisRest() { return usedThisRest; }
    public void setUsedThisRest(int usedThisRest) { this.usedThisRest = usedThisRest; }

    public int getUsedThisTurn() { return usedThisTurn; }
    public void setUsedThisTurn(int usedThisTurn) { this.usedThisTurn = usedThisTurn; }
}
