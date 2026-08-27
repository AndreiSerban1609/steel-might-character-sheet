package com.steelmight.charactersheet.service;

import com.steelmight.charactersheet.engine.ResolutionResult;
import com.steelmight.charactersheet.engine.TurnTickService;
import com.steelmight.charactersheet.model.Combatant;
import com.steelmight.charactersheet.model.GameCharacter;
import com.steelmight.charactersheet.model.LifeStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns begin automatically (players and the GM only ever END turns): whoever the order
 * lands on gets their start-of-turn ticks right away. This is the one implementation of
 * that hand-off for both combatant kinds — a monster's turn opens exactly like a
 * player's minus the AP economy (E1) and per-turn ability budgets.
 */
@Service
@Transactional
public class TurnFlowService {

    private final CombatantLookup lookup;
    private final EncounterService encounters;
    private final TurnTickService ticks;

    public TurnFlowService(CombatantLookup lookup, EncounterService encounters, TurnTickService ticks) {
        this.lookup = lookup;
        this.encounters = encounters;
        this.ticks = ticks;
    }

    /**
     * After a turn end advanced the order: begin the next combatant's turn, merging its
     * ticks into the ender's log under a name prefix ("Goblin 2:burning:hp").
     */
    public void autoStartNext(String room, EncounterService.NextTurn next, ResolutionResult into) {
        if (next == null) return;
        into.addStep("turn-order", "Turn passes to " + next.name() + " (round " + next.round() + ")", 0, 0);
        startTurn(room, next.combatantId(), into, next.name());
    }

    /** Begin the current turn if nobody has (encounter start, DM skip). Silent no-op otherwise. */
    public void autoStartCurrent(String room) {
        var view = encounters.get(room);
        if (!view.active() || view.turnStarted() || view.currentPlayerId() == null) return;
        startTurn(room, view.currentPlayerId(), new ResolutionResult(), null);
    }

    private void startTurn(String room, String combatantId, ResolutionResult into, String prefix) {
        // find, not require: a row deleted mid-combat must never 404 someone ELSE's turn end
        // (advance() skips missing entries, but belt and braces).
        Combatant c = lookup.find(room, combatantId).orElse(null);
        if (c == null || c.getLifeStatus() == LifeStatus.DEAD) return;

        boolean apRecovery = encounters.validateAndMarkTurnStart(c);
        if (c instanceof GameCharacter character) {
            // Per-turn ability budgets reset at turn start (Story 1.4) — players only.
            character.getAbilityUses().forEach(u -> u.setUsedThisTurn(0));
        }
        var started = ticks.turnStart(c, apRecovery);
        if (prefix != null) {
            started.getSteps().forEach(s ->
                    into.addStep(prefix + ":" + s.rule(), s.note(), s.valueBefore(), s.valueAfter()));
            started.getEffectsTriggered().forEach(into::addTriggeredEffect);
        }
        lookup.save(c);
    }
}
