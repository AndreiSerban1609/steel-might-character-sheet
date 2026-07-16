package com.steelmight.charactersheet.gamedata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A class sub-resource pool from abilities-*.json (perseverance, fury, fatal-fixation…).
 *
 * restore: "on-rest" = tier-scaled regain per the 2026-07-13 ruling
 * (current += ceil(tier% × max), capped) | "manual" = never auto-restored.
 * min present (fury) = spends may push the pool negative (disaster rule is DM-side).
 * maxFormula (shapeshift-hp) marks a derived pool the engine cannot materialize yet —
 * skipped until its rulings land (S1/S7); initial/max are null on such pools.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PoolDefinition(
        String id,
        String name,
        Integer unlockLevel,
        Integer initial,
        Integer min,
        Integer max,
        String maxFormula,
        String restore,
        String description
) {}
