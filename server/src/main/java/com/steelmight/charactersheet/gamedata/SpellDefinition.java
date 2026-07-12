package com.steelmight.charactersheet.gamedata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * One spell from spells-*.json (types.ts → Spell). String-union fields
 * (damageType, attackType, saveStat, components) stay strings — they are
 * payload data for the DM; nothing server-side branches on them yet.
 * Costs are {@link SpellCost}: types.ts declares plain numbers, but the data
 * carries "10%" mana costs and free-text AP costs.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SpellDefinition(
        String id,
        String name,
        String classId,
        int level,
        SpellCost apCost,
        SpellCost manaCost,
        String range,
        List<String> components,
        String duration,
        boolean concentration,
        boolean channeling,
        String damageType,
        String attackType,
        String saveStat,
        DiceFormula damage,
        DiceFormula healing,
        List<String> effects,
        SpellScaling scaling,
        String description
) {}
