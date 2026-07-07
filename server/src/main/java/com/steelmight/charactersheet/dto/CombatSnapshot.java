package com.steelmight.charactersheet.dto;

import com.steelmight.charactersheet.model.AbilityScore;
import java.util.List;
import java.util.Map;

public record CombatSnapshot(
        String name,
        int level,
        String pathId,
        String classId,
        String specializationId,
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
        // Death & dying (M2-D): downedRoundsRemaining/reviveDC only while DOWNED.
        String status,
        Integer downedRoundsRemaining,
        Integer reviveDC,
        boolean pendingDeathFight,
        int downsThisCombat,
        List<AbilityScore> savingThrowProficiencies,
        List<String> proficiencies,
        List<EffectView> activeEffects,
        String equippedWeapon,
        String equippedArmor,
        List<String> conditions,
        // Q30 (M5-B): equipped gear without proficiency — display data for the DM.
        List<PenaltyView> proficiencyPenalties,
        // M6-C progression state — drives the level-up UI's choice pools.
        List<String> talents,
        List<String> specFeats
) {
    public record HpView(int current, int max, int temp) {}
    public record ApView(int current, int recovery, int max) {}
    public record ManaView(int current, int max) {}
    public record EffectView(String id, String name, int stacks, Integer value, Integer rounds) {}
    public record PenaltyView(String itemId, String penalty) {}
}
