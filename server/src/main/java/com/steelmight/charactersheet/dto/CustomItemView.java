package com.steelmight.charactersheet.dto;

/**
 * One player/GM-defined weapon or armor (demo feedback #19), in and out.
 *
 * {@code id} is server-assigned on create and echoed back on update; clients send null
 * for a new item. Weapon fields are ignored for ARMOR and vice versa, so the UI can send
 * one flat shape.
 */
public record CustomItemView(
        String id,
        String name,
        // "WEAPON" | "ARMOR"
        String kind,
        Double inventorySpace,
        String properties,
        Boolean proficient,
        // weapon
        String damageDice,
        Integer damageFlat,
        String damageType,
        Integer damageScaling,
        String weaponStat,
        Integer apCost,
        // armor
        String armorType,
        Integer acBase,
        Boolean acDexMod,
        Integer pa,
        Integer ma,
        Integer paScaling,
        Integer maScaling
) {}
