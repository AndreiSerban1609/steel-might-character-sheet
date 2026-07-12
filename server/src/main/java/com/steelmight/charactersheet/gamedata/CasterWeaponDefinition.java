package com.steelmight.charactersheet.gamedata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** One caster weapon from caster-weapons.json (types.ts → CasterWeapon). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CasterWeaponDefinition(
        String id,
        String name,
        String type,
        int itemLevel,
        int spellModifier,
        int spellDamage,
        int manaCostReduction,
        int extraSpellsKnown,
        String damageType,
        String uniqueEffect,
        WandAttack wandAttack,
        StaffAccuracy staffAccuracy,
        String priceTier
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WandAttack(
            int apCost,
            WandDamage damage,
            String manaRestored,
            boolean autoHit
    ) {}

    /** DiceFormula plus the wand's per-item-level scaling. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WandDamage(
            double modMultiplier,
            int flat,
            Dice dice,
            int scalingPerLevel
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StaffAccuracy(
            int spellAttackBonus,
            int spellSaveDCBonus
    ) {}
}
