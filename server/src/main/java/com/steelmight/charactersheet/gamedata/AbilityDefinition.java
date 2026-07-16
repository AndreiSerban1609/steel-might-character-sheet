package com.steelmight.charactersheet.gamedata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * One non-caster class ability from abilities-*.json (Epic 1 / Story 1.1).
 *
 * kind: active | reaction | attack-enhancer | passive.
 * resolution: auto (server rolls/applies) | manual (server validates + spends costs and
 * prints the rules text into the resolution log).
 * apCost reuses {@link SpellCost}: plain int, "all" (Onslaught) parses as special text,
 * null (passives) parses as flat 0.
 * The monk-specific cost fields (costEqualsHealing, costPercentOfMaxResource,
 * outOfCombatCostMultiplier) are loaded but not resolved until their rulings land (MK13/17).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AbilityDefinition(
        String id,
        String name,
        String classId,
        int minLevel,
        String group,
        String kind,
        SpellCost apCost,
        List<AbilityCost> costs,
        String resolution,
        List<ResourceAmount> grants,
        List<AbilityRider> riders,
        AbilityFormula healing,
        TargetEffectSpec targetEffect,
        SelfEffectSpec selfEffect,
        UsesPerRest usesPerRest,
        Integer usesPerTurn,
        Integer usesPerRage,
        Integer nextTurnApPenalty,
        Integer recharge,
        Boolean costEqualsHealing,
        Integer costPercentOfMaxResource,
        Double outOfCombatCostMultiplier,
        Double healingMultiplierOfSacredHandDirect,
        String description
) {

    /** amountDice (fury: "2 + 1d4") is rolled at spend time; total = amount + roll. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AbilityCost(String resource, Integer amount, Dice amountDice) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResourceAmount(String resource, int amount) {}

    /** Level-gated extras (e.g. Endurance: perseverance spends also regain 20 energy from L10). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AbilityRider(Integer minLevel, String name, List<ResourceAmount> gains) {}

    /** Dice count variants: fixed count | count per level | base + per-level-beyond-unlock. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DiceSpec(Integer count, Integer countPerLevel, Integer countBase,
                           Integer countPerLevelBeyond, Integer sides) {}

    /** Flat stat term; perLevel multiplies the stat modifier by character level. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StatFlat(String stat, Boolean perLevel, Double multiplier) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AbilityFormula(DiceSpec dice, StatFlat statFlat) {}

    /**
     * Stack count formulas: base flat | perLevel (level × N) | levelDivisor
     * (level / N, round up/down) | levelOffset ((level + offset) / divisor) |
     * levelMultiplier (level × M / divisor).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StacksFormula(Integer base, Integer perLevel, Integer levelDivisor,
                                String round, Integer levelOffset, Double levelMultiplier) {}

    /** Computed server-side into the payload; applied at the table until the encounter model. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TargetEffectSpec(String effectId, StacksFormula stacks, Integer durationRounds,
                                   Integer valueFromWeaponAverageDivisor) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SelfEffectSpec(String effectId, Integer durationRounds, Integer stacks) {}

    /** Either a flat amount or stat-modifier-many uses (with a minimum) per rest. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UsesPerRest(String stat, Integer min, Integer amount) {}
}
