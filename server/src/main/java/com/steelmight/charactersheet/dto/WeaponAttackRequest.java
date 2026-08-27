package com.steelmight.charactersheet.dto;

/**
 * POST /actions/weapon-attack — attack with the equipped weapon.
 *
 * @param itemId            which weapon, when dual-wielding two light weapons; optional
 *                          (and ignored) with a single equipped weapon
 * @param targetCombatantId who is attacked (a playerId or {@code monster:{id}}). Named: the
 *                          roll is compared to THEIR AC and a hit's damage lands through their
 *                          pipeline. Omitted: the numbers are printed for the table (legacy).
 */
public record WeaponAttackRequest(String itemId, String targetCombatantId) {
    public WeaponAttackRequest(String itemId) {
        this(itemId, null);
    }
}
