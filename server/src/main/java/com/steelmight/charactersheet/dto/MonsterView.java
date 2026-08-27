package com.steelmight.charactersheet.dto;

import com.steelmight.charactersheet.model.AbilityScore;
import com.steelmight.charactersheet.model.DamageType;

import java.util.List;
import java.util.Map;

/**
 * A live monster in a room's fight — the GM board's row and the snapshot returned by every
 * combatant action on it. Mirrors the player CombatSnapshot's vitals/effects shape so the
 * frontend can reuse its effect chips and condition rendering.
 */
public record MonsterView(
        Long id,
        /** {@code monster:{id}} — the target id every combatant action route accepts. */
        String combatantId,
        Long templateId,
        String name,
        int level,
        CombatSnapshot.HpView hp,
        int ac,
        int pa,
        int ma,
        int speed,
        Integer might,
        Map<AbilityScore, Integer> stats,
        Map<AbilityScore, Integer> modifiers,
        String status,
        int stackThreshold,
        List<AbilityScore> savingThrowProficiencies,
        Map<DamageType, Double> damageTaken,
        List<CombatSnapshot.EffectView> activeEffects,
        List<String> conditions,
        String abilitiesText
) {}
