package com.steelmight.charactersheet.gamedata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Per-upcast-step increases (types.ts → SpellScaling). manaCostIncrease is
 *  null on radiant-aura (percent-cost aura that only scales damage). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SpellScaling(
        Integer manaCostIncrease,
        DiceFormula damageIncrease,
        DiceFormula healingIncrease,
        String description
) {}
