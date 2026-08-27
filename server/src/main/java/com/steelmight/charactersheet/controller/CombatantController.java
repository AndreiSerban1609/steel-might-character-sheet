package com.steelmight.charactersheet.controller;

import com.steelmight.charactersheet.dto.ActionResponse;
import com.steelmight.charactersheet.dto.ApplyEffectRequest;
import com.steelmight.charactersheet.dto.CombatantView;
import com.steelmight.charactersheet.dto.DamageRequest;
import com.steelmight.charactersheet.dto.HealRequest;
import com.steelmight.charactersheet.service.CombatantActionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * Combat actions addressed by combatant id (ADR-001 §4): a playerId or {@code monster:{id}}.
 * Thin dispatch into the SAME pipeline services the per-character routes use — those
 * routes stay, players are simply combatants too.
 */
@RestController
@RequestMapping("/api/rooms/{room}/combatants/{combatantId}")
public class CombatantController {

    private final CombatantActionService actions;

    public CombatantController(CombatantActionService actions) {
        this.actions = actions;
    }

    @GetMapping
    public CombatantView get(@PathVariable String room, @PathVariable String combatantId) {
        return actions.get(room, combatantId);
    }

    @PostMapping("/actions/damage")
    public ActionResponse<CombatantView> damage(@PathVariable String room, @PathVariable String combatantId,
                                                @Valid @RequestBody DamageRequest req) {
        return actions.damage(room, combatantId, req);
    }

    @PostMapping("/actions/heal")
    public ActionResponse<CombatantView> heal(@PathVariable String room, @PathVariable String combatantId,
                                              @Valid @RequestBody HealRequest req) {
        return actions.heal(room, combatantId, req);
    }

    @PostMapping("/actions/apply-effect")
    public ActionResponse<CombatantView> applyEffect(@PathVariable String room, @PathVariable String combatantId,
                                                     @Valid @RequestBody ApplyEffectRequest req) {
        return actions.applyEffect(room, combatantId, req);
    }

    @PostMapping("/actions/remove-effect")
    public ActionResponse<CombatantView> removeEffect(@PathVariable String room, @PathVariable String combatantId,
                                                      @RequestParam String effectId) {
        return actions.removeEffect(room, combatantId, effectId);
    }

    @PostMapping("/actions/turn-start")
    public ActionResponse<CombatantView> turnStart(@PathVariable String room, @PathVariable String combatantId) {
        return actions.turnStart(room, combatantId);
    }

    @PostMapping("/actions/turn-end")
    public ActionResponse<CombatantView> turnEnd(@PathVariable String room, @PathVariable String combatantId) {
        return actions.turnEnd(room, combatantId);
    }
}
