package com.steelmight.charactersheet.engine;

import com.steelmight.charactersheet.model.DamageType;

import java.util.List;

public class DamageEvent {

    private int value;
    private final DamageType damageType;
    private final DamageCategory category;
    private final List<String> tags;
    private final boolean ignoreResistance;
    private final String sourceId;
    // M2-D: death-resist protects only during the character's own turn.
    private boolean duringOwnTurn;
    // Immunity stop: the damage never "landed" — unlike absorbed-to-0 damage,
    // post-damage triggers must NOT fire (Q04).
    private boolean halted;
    // N2 (M4-C): attacker's might for the concentration-break WILL save
    // (DC = 5 + might); null → the DM resolves the save manually.
    private Integer attackerMight;
    // Set by HpReductionRule when hp.current actually dropped — the
    // concentration check fires only on HP-reducing damage (M4-C).
    private boolean hpReduced;

    public DamageEvent(int value, DamageType damageType, DamageCategory category, List<String> tags) {
        this(value, damageType, category, tags, false, null);
    }

    public DamageEvent(int value, DamageType damageType, DamageCategory category,
                       List<String> tags, boolean ignoreResistance) {
        this(value, damageType, category, tags, ignoreResistance, null);
    }

    public DamageEvent(int value, DamageType damageType, DamageCategory category,
                       List<String> tags, boolean ignoreResistance, String sourceId) {
        this.value = value;
        this.damageType = damageType;
        this.category = category;
        this.tags = tags != null ? List.copyOf(tags) : List.of();
        this.ignoreResistance = ignoreResistance;
        this.sourceId = sourceId;
    }

    public int getValue() { return value; }
    public void setValue(int value) { this.value = value; }
    public DamageType getDamageType() { return damageType; }
    public DamageCategory getCategory() { return category; }
    public List<String> getTags() { return tags; }
    public boolean isIgnoreResistance() { return ignoreResistance; }
    public String getSourceId() { return sourceId; }
    public boolean isDuringOwnTurn() { return duringOwnTurn; }
    public void setDuringOwnTurn(boolean duringOwnTurn) { this.duringOwnTurn = duringOwnTurn; }
    public boolean isHalted() { return halted; }
    public void halt() { this.halted = true; }
    public Integer getAttackerMight() { return attackerMight; }
    public void setAttackerMight(Integer attackerMight) { this.attackerMight = attackerMight; }
    public boolean isHpReduced() { return hpReduced; }
    public void setHpReduced(boolean hpReduced) { this.hpReduced = hpReduced; }

    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }
}
