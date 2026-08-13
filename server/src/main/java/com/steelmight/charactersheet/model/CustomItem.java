package com.steelmight.charactersheet.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * A player- or GM-defined weapon or armor (demo feedback #19, ruled 2026-08-13:
 * players may define them too, not just the GM). Per-character: the item belongs to the
 * sheet that defined it, which is how custom abilities already work and needs no
 * ownership model.
 *
 * These are GRANTED, not bought — a custom item has no price tier, so it is outside the
 * shop's purchase/sell/upgrade flows. Everything else (equip, attack, AC/PA/MA derivation,
 * carry weight, proficiency penalties) treats it exactly like a catalog item, because
 * {@code CustomItemNodes} renders it into the same JSON shape the catalogs use.
 *
 * Wrapper types throughout: rows written before a field existed come back null.
 */
@Embeddable
public class CustomItem {

    /** Prefix that marks an id as character-owned rather than a catalog entry. */
    public static final String ID_PREFIX = "custom-";

    @Column(name = "item_id")
    private String itemId;

    @Column(name = "item_name")
    private String name;

    /** WEAPON or ARMOR — the only two kinds the sheet can act on mechanically. */
    @Column(name = "item_kind")
    private String kind;

    @Column(name = "inventory_space")
    private Double inventorySpace;

    /** Comma-separated property ids (two-handed, finesse, …); free text is tolerated. */
    @Column(name = "properties")
    private String properties;

    /** false → the sheet applies the same non-proficiency penalties a catalog item would. */
    @Column(name = "proficient")
    private Boolean proficient;

    // --- Weapon fields ---
    @Column(name = "damage_dice")
    private String damageDice;

    @Column(name = "damage_flat")
    private Integer damageFlat;

    @Column(name = "damage_type")
    private String damageType;

    /** Per-upgrade-tier flat damage growth, mirroring weapons.json `scaling`. */
    @Column(name = "damage_scaling")
    private Integer damageScaling;

    /** Ability the attack uses: str | dex | … */
    @Column(name = "weapon_stat")
    private String weaponStat;

    @Column(name = "ap_cost")
    private Integer apCost;

    // --- Armor fields ---
    /** light | medium | heavy | shield */
    @Column(name = "armor_type")
    private String armorType;

    @Column(name = "ac_base")
    private Integer acBase;

    @Column(name = "ac_dex_mod")
    private Boolean acDexMod;

    @Column(name = "pa")
    private Integer pa;

    @Column(name = "ma")
    private Integer ma;

    @Column(name = "pa_scaling")
    private Integer paScaling;

    @Column(name = "ma_scaling")
    private Integer maScaling;

    protected CustomItem() {}

    public CustomItem(String itemId, String name, String kind) {
        this.itemId = itemId;
        this.name = name;
        this.kind = kind;
    }

    public String getItemId() { return itemId; }
    public String getName() { return name; }
    public String getKind() { return kind; }
    public boolean isWeapon() { return "WEAPON".equals(kind); }
    public boolean isArmor() { return "ARMOR".equals(kind); }
    public boolean isShield() { return "shield".equals(armorType); }

    public double getInventorySpace() { return inventorySpace != null ? inventorySpace : 1.0; }
    public String getProperties() { return properties != null ? properties : ""; }
    public boolean isProficient() { return proficient == null || proficient; }

    public String getDamageDice() { return damageDice; }
    public int getDamageFlat() { return damageFlat != null ? damageFlat : 0; }
    public String getDamageType() { return damageType; }
    public int getDamageScaling() { return damageScaling != null ? damageScaling : 0; }
    public String getWeaponStat() { return weaponStat; }
    public int getApCost() { return apCost != null ? apCost : 3; }

    public String getArmorType() { return armorType; }
    public int getAcBase() { return acBase != null ? acBase : 10; }
    public boolean isAcDexMod() { return acDexMod != null && acDexMod; }
    public int getPa() { return pa != null ? pa : 0; }
    public int getMa() { return ma != null ? ma : 0; }
    public int getPaScaling() { return paScaling != null ? paScaling : 0; }
    public int getMaScaling() { return maScaling != null ? maScaling : 0; }

    public void setName(String name) { this.name = name; }
    public void setInventorySpace(Double v) { this.inventorySpace = v; }
    public void setProperties(String v) { this.properties = v; }
    public void setProficient(Boolean v) { this.proficient = v; }
    public void setDamageDice(String v) { this.damageDice = v; }
    public void setDamageFlat(Integer v) { this.damageFlat = v; }
    public void setDamageType(String v) { this.damageType = v; }
    public void setDamageScaling(Integer v) { this.damageScaling = v; }
    public void setWeaponStat(String v) { this.weaponStat = v; }
    public void setApCost(Integer v) { this.apCost = v; }
    public void setArmorType(String v) { this.armorType = v; }
    public void setAcBase(Integer v) { this.acBase = v; }
    public void setAcDexMod(Boolean v) { this.acDexMod = v; }
    public void setPa(Integer v) { this.pa = v; }
    public void setMa(Integer v) { this.ma = v; }
    public void setPaScaling(Integer v) { this.paScaling = v; }
    public void setMaScaling(Integer v) { this.maScaling = v; }
}
