package com.steelmight.charactersheet.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;

/**
 * The authored combat block of a monster (ADR-001 §2). Shared by the GM's reusable
 * {@link MonsterTemplate} and every {@link MonsterInstance} stamped from it — the instance
 * COPIES the block, so editing a template never mutates a live fight.
 *
 * Nothing here is derived: AC/PA/MA/HP/speed are flat GM-authored values, which is why a
 * monster satisfies {@code Combatant.overrideFor} for every combat stat (the "override
 * replaces the formula, effects still apply" seam from the player stat overrides).
 */
@Embeddable
public class MonsterBlock {

    private int level;
    private int maxHp;
    private int ac;
    private int pa;
    private int ma;
    private int speed;
    /** Might for concentration DCs when this monster is the attacker (Story 2.4); null = unknown. */
    private Integer might;
    private int initiativeBonus;
    /** Ruling E2: authored stack threshold (bosses soak more); null = ceil(level/2) like players. */
    private Integer stackThreshold;

    @Embedded
    private Stats stats;

    @Column(length = 4000)
    private String abilitiesText;

    protected MonsterBlock() {}

    /** Deep copy — instances must never share the template's Stats object. */
    public MonsterBlock(MonsterBlock other) {
        this.level = other.level;
        this.maxHp = other.maxHp;
        this.ac = other.ac;
        this.pa = other.pa;
        this.ma = other.ma;
        this.speed = other.speed;
        this.might = other.might;
        this.initiativeBonus = other.initiativeBonus;
        this.stackThreshold = other.stackThreshold;
        this.stats = other.stats == null ? null : new Stats(
                other.stats.getStr(), other.stats.getDex(), other.stats.getCon(), other.stats.getIntel(),
                other.stats.getWis(), other.stats.getWill(), other.stats.getCha());
        this.abilitiesText = other.abilitiesText;
    }

    public static MonsterBlock empty() {
        var b = new MonsterBlock();
        b.stats = new Stats(10, 10, 10, 10, 10, 10, 10);
        return b;
    }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public int getMaxHp() { return maxHp; }
    public void setMaxHp(int maxHp) { this.maxHp = maxHp; }
    public int getAc() { return ac; }
    public void setAc(int ac) { this.ac = ac; }
    public int getPa() { return pa; }
    public void setPa(int pa) { this.pa = pa; }
    public int getMa() { return ma; }
    public void setMa(int ma) { this.ma = ma; }
    public int getSpeed() { return speed; }
    public void setSpeed(int speed) { this.speed = speed; }
    public Integer getMight() { return might; }
    public void setMight(Integer might) { this.might = might; }
    public int getInitiativeBonus() { return initiativeBonus; }
    public void setInitiativeBonus(int initiativeBonus) { this.initiativeBonus = initiativeBonus; }
    public Integer getStackThreshold() { return stackThreshold; }
    public void setStackThreshold(Integer stackThreshold) { this.stackThreshold = stackThreshold; }
    public Stats getStats() { return stats; }
    public void setStats(Stats stats) { this.stats = stats; }
    public String getAbilitiesText() { return abilitiesText; }
    public void setAbilitiesText(String abilitiesText) { this.abilitiesText = abilitiesText; }
}
