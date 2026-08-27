package com.steelmight.charactersheet.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Use a class ability (Story 1.4): validate → spend costs → resolve/print.
 *
 * @param targetCombatantId who receives the ability's structured target effect (a playerId
 *                          or {@code monster:{id}}); omitted → the stacks are printed for the
 *                          table to apply, as before targets existed
 */
public record UseAbilityRequest(@NotBlank String abilityId, String targetCombatantId) {
    public UseAbilityRequest(String abilityId) {
        this(abilityId, null);
    }
}
