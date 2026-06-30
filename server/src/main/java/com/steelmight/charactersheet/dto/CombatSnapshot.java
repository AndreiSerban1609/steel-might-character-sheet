package com.steelmight.charactersheet.dto;

import com.steelmight.charactersheet.model.AbilityScore;
import java.util.List;
import java.util.Map;

public record CombatSnapshot(
        String name,
        int level,
        String pathId,
        String classId,
        Map<AbilityScore, Integer> stats,
        Map<AbilityScore, Integer> modifiers,
        HpView hp,
        int ac,
        int pa,
        int ma,
        ApView ap,
        ManaView mana,
        int speed,
        int bonusInitiative,
        int deathStacks,
        List<AbilityScore> savingThrowProficiencies,
        List<String> proficiencies,
        List<EffectView> activeEffects,
        String equippedWeapon,
        String equippedArmor,
        List<String> conditions
) {
    public record HpView(int current, int max, int temp) {}
    public record ApView(int current, int recovery, int max) {}
    public record ManaView(int current, int max) {}
    public record EffectView(String id, String name, int stacks, Integer value, Integer rounds) {}
}
