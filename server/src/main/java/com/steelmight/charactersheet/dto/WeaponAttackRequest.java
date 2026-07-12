package com.steelmight.charactersheet.dto;

/**
 * POST /actions/weapon-attack — attack with the equipped weapon.
 *
 * @param itemId which weapon, when dual-wielding two light weapons; optional
 *               (and ignored) with a single equipped weapon
 */
public record WeaponAttackRequest(String itemId) {}
