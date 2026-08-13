package com.steelmight.charactersheet.dto;

/** Patch for the mutable combat pools; any null field is left unchanged. */
public record VitalsRequest(Integer currentHp, Integer tempHp,
                            Integer tempShieldPhysical, Integer tempShieldMagical,
                            Integer currentAp, Integer currentMana) {}
