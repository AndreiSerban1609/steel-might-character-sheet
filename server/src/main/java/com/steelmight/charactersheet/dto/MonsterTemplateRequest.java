package com.steelmight.charactersheet.dto;

import com.steelmight.charactersheet.model.AbilityScore;
import com.steelmight.charactersheet.model.DamageType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

/**
 * Create / update / import shape for a monster template. Also the EXPORT shape: a
 * {@link MonsterTemplateView} minus id + room deserializes straight into this, so a stat
 * block can be copied between rooms (ruling E9).
 */
public record MonsterTemplateRequest(
        @NotBlank String name,
        @Min(1) int level,
        @Min(1) int maxHp,
        int ac,
        int pa,
        int ma,
        int speed,
        Integer might,
        int initiativeBonus,
        /** Missing scores default to 10. */
        Map<AbilityScore, Integer> stats,
        List<AbilityScore> savingThrowProficiencies,
        /** Multiplier per damage type: <1 resist, >1 vulnerable, 0 immune. */
        Map<DamageType, Double> damageTaken,
        String abilitiesText,
        /** Ruling E2: authored stack threshold; null = ceil(level/2). */
        Integer stackThreshold
) {}
