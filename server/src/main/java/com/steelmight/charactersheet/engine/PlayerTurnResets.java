package com.steelmight.charactersheet.engine;

import com.steelmight.charactersheet.model.GameCharacter;

/**
 * Player-only bookkeeping that happens when a turn starts — shared by the two paths that
 * start turns: {@code CharacterService.turnStart} (free play / explicit start) and
 * {@code TurnFlowService.startTurn} (auto-start after the previous combatant's turn end).
 * Monsters have neither ability budgets nor an AP economy (ruling E1), so nothing here
 * applies to them.
 */
public final class PlayerTurnResets {
    private PlayerTurnResets() {}

    public static void atTurnStart(GameCharacter c, ResolutionResult result) {
        // Per-turn ability budgets reset at turn start (Story 1.4).
        c.getAbilityUses().forEach(u -> u.setUsedThisTurn(0));
        // Prepared reactions (2026-08-27) last until the preparer's next turn begins; one
        // that never triggered simply lapses — the AP paid to prepare it stays spent.
        expirePreparedReactions(c, result, "expired unused at the start of your turn");
    }

    /** Drop every prepared reaction with one log step per reaction; no-op when there are none. */
    public static void expirePreparedReactions(GameCharacter c, ResolutionResult result, String why) {
        if (c.getPreparedReactions().isEmpty()) return;
        for (var r : c.getPreparedReactions()) {
            result.addStep("prepared-reaction",
                    "Prepared reaction " + why + ": " + r.getNote() + " (" + r.getApCost() + " AP)", 0, 0);
        }
        c.getPreparedReactions().clear();
    }
}
