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
        // Typed absorption pools mirrored out of their effects, so the sheet can show and
        // edit them like temp HP (demo feedback #14). 0 = no shield up.
        int tempShieldPhysical,
        int tempShieldMagical,
        ApView ap,
        ManaView mana,
        // Class resource (M3 Part A): chakra/rages/energy/focus/… — null when the class has none.
        ResourceView resource,
        // Sub-resource pools (Story 1.2): perseverance/fury/… — empty for classes without pools.
        List<PoolView> pools,
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
        // every equipped WEAPON-kind item (two when dual-wielding) — drives the attack picker
        List<String> equippedWeapons,
        String equippedArmor,
        List<String> conditions,
        // Q30 (M5-B): equipped gear without proficiency — display data for the DM.
        List<PenaltyView> proficiencyPenalties,
        // M6-C progression state — drives the level-up UI's choice pools.
        List<String> talents,
        List<String> specFeats,
        // Derived stats the GM has pinned (demo feedback #11/#12) — so the sheet can mark
        // them as manual rather than leaving the player guessing why the formula "lies".
        Map<String, Integer> statOverrides
) {
    public record HpView(int current, int max, int temp) {}
    public record ApView(int current, int recovery, int max) {}
    public record ManaView(int current, int max) {}
    /** max == null → unbounded (builder resources like focus). */
    public record ResourceView(String type, int current, Integer max) {}
    /** Sub-resource pool row; current can be negative (fury disaster rule). */
    public record PoolView(String id, String name, int current, Integer max) {}
    /**
     * {@code active} is the threshold-system dormancy answer (Guide pp.8-9): a negative
     * stack-based effect does nothing until its stacks reach the character's threshold.
     * {@code threshold} is that number, and is null for effects the system doesn't gate
     * (positives, multi-instance DoTs) — the UI shows "n/threshold" only when it's set.
     */
    public record EffectView(String id, String name, int stacks, Integer value, Integer rounds,
                             boolean active, Integer threshold) {}
    public record PenaltyView(String itemId, String penalty) {}
}
