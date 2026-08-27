package com.steelmight.charactersheet.dto;

import com.steelmight.charactersheet.model.AbilityScore;
import com.steelmight.charactersheet.model.DamageType;

import java.util.List;
import java.util.Map;

/** A room's monster template. Same field names as {@link MonsterTemplateRequest} plus identity. */
public record MonsterTemplateView(
        Long id,
        String roomName,
        String name,
        int level,
        int maxHp,
        int ac,
        int pa,
        int ma,
        int speed,
        Integer might,
        int initiativeBonus,
        Map<AbilityScore, Integer> stats,
        List<AbilityScore> savingThrowProficiencies,
        Map<DamageType, Double> damageTaken,
        String abilitiesText,
        Integer stackThreshold
) {}
