package com.steelmight.charactersheet.dto;

import com.steelmight.charactersheet.model.DamageType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * tags: event tags (e.g. "dot", "ignoresArmor", "environmental"); defaults to ["directAttack"].
 * ignoreResistance: DM flag for ignore-resistance-vulnerability attackers (skips the res/vuln rule).
 * sourceId: who dealt it — matches charmed's harmedBySource removal (M2-B); optional.
 * duringOwnTurn: the damage lands during the target's own turn — death-resist floors HP at 1 (M2-D).
 * attackerMight: N2 (M4-C) — feeds the concentration-break WILL save (DC = 5 + might);
 * absent → the server emits a resolve-manually step instead of rolling.
 */
public record DamageRequest(
        @Min(1) int value,
        @NotNull DamageType damageType,
        List<String> tags,
        boolean ignoreResistance,
        String sourceId,
        boolean duringOwnTurn,
        Integer attackerMight
) {
    public DamageRequest(int value, DamageType damageType, List<String> tags,
                         boolean ignoreResistance, String sourceId, boolean duringOwnTurn) {
        this(value, damageType, tags, ignoreResistance, sourceId, duringOwnTurn, null);
    }
}
