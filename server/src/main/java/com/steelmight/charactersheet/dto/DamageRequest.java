package com.steelmight.charactersheet.dto;

import com.steelmight.charactersheet.model.DamageType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * @param attackerMight       concentration-break DC = 5 + might (N2); null → the server prints
 *                            a resolve-manually step instead of rolling
 * @param attackerCombatantId who dealt the damage (Story 2.4): a monster's authored might fills
 *                            {@code attackerMight} when it is not given, and the id becomes the
 *                            event's {@code sourceId} (wounded-by / source-matched triggers)
 *                            when none is given
 */
public record DamageRequest(
        @Min(1) int value,
        @NotNull DamageType damageType,
        List<String> tags,
        boolean ignoreResistance,
        String sourceId,
        boolean duringOwnTurn,
        Integer attackerMight,
        String attackerCombatantId
) {
    public DamageRequest(int value, DamageType damageType, List<String> tags,
                         boolean ignoreResistance, String sourceId, boolean duringOwnTurn) {
        this(value, damageType, tags, ignoreResistance, sourceId, duringOwnTurn, null, null);
    }

    public DamageRequest(int value, DamageType damageType, List<String> tags,
                         boolean ignoreResistance, String sourceId, boolean duringOwnTurn,
                         Integer attackerMight) {
        this(value, damageType, tags, ignoreResistance, sourceId, duringOwnTurn, attackerMight, null);
    }
}
