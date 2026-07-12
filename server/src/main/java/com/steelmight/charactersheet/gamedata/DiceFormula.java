package com.steelmight.charactersheet.gamedata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A spell damage/healing formula: dice + flat + modMultiplier × spell-stat modifier.
 * modMultiplier is fractional in the data (radiant-aura scales at 0.5×); dice may be
 * null for flat-only formulas.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DiceFormula(
        double modMultiplier,
        int flat,
        Dice dice
) {}
