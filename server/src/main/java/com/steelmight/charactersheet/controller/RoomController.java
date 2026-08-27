package com.steelmight.charactersheet.controller;

import com.steelmight.charactersheet.dto.AuditView;
import com.steelmight.charactersheet.dto.DeckTemplate;
import com.steelmight.charactersheet.dto.EncounterView;
import com.steelmight.charactersheet.dto.EndEncounterResponse;
import com.steelmight.charactersheet.dto.SetInitiativeRequest;
import com.steelmight.charactersheet.dto.StartEncounterRequest;
import com.steelmight.charactersheet.model.CombatantType;
import com.steelmight.charactersheet.service.AuditService;
import com.steelmight.charactersheet.service.CharacterService;
import com.steelmight.charactersheet.service.DeckTemplateService;
import com.steelmight.charactersheet.service.EncounterService;
import com.steelmight.charactersheet.service.TurnFlowService;
import jakarta.validation.Valid;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final DeckTemplateService deckService;
    private final EncounterService encounterService;
    private final CharacterService characterService;
    private final TurnFlowService turnFlow;
    private final AuditService auditService;

    public RoomController(DeckTemplateService deckService, EncounterService encounterService,
                          CharacterService characterService, TurnFlowService turnFlow,
                          AuditService auditService) {
        this.deckService = deckService;
        this.encounterService = encounterService;
        this.characterService = characterService;
        this.turnFlow = turnFlow;
        this.auditService = auditService;
    }

    /** The room's activity log, newest first — who did what, when (trusted-table review). */
    @GetMapping("/{room}/audit")
    public List<AuditView> audit(@PathVariable String room,
                                 @RequestParam(defaultValue = "50") int limit) {
        return auditService.recent(room, limit);
    }

    @GetMapping("/{room}/deck")
    public DeckTemplate getDeck(@PathVariable String room) {
        return deckService.getTemplate(room);
    }

    @PutMapping("/{room}/deck")
    public DeckTemplate updateDeck(@PathVariable String room, @RequestBody DeckTemplate template) {
        return deckService.updateTemplate(room, template);
    }

    // ---- Initiative & turn order ----

    @GetMapping("/{room}/encounter")
    public EncounterView getEncounter(@PathVariable String room) {
        return encounterService.get(room);
    }

    /** Rolls d20 + DEX mod + initiative bonus per participant — every player plus every
     *  living monster in the room — and opens the turn order. Also runs each PLAYER's
     *  combat-start pipeline (Q18: AP → starting value; monsters have no AP), then begins
     *  the first combatant's turn — nobody starts turns themselves in combat.
     *  Transactional: the composition is all-or-nothing (no half-started encounters). */
    @Transactional
    @PostMapping("/{room}/encounter/start")
    public EncounterView startEncounter(@PathVariable String room,
                                        @RequestBody(required = false) StartEncounterRequest req) {
        var view = encounterService.start(room, req);
        for (var entry : view.entries()) {
            if (entry.combatantType() == CombatantType.PLAYER) {
                characterService.combatStart(entry.playerId());
            }
        }
        turnFlow.autoStartCurrent(room);
        return encounterService.get(room);
    }

    /** Ends the combat and pays out its XP pool, split evenly among the players in the order (2026-08-27). */
    @Transactional
    @PostMapping("/{room}/encounter/end")
    public EndEncounterResponse endEncounter(@PathVariable String room) {
        return encounterService.endAndAward(room);
    }

    /** DM override: skip the current turn (AFK player) and advance. */
    @Transactional
    @PostMapping("/{room}/encounter/next")
    public EncounterView nextTurn(@PathVariable String room) {
        encounterService.forceNext(room);
        turnFlow.autoStartCurrent(room);
        return encounterService.get(room);
    }

    /** DM override: change a participant's initiative mid-combat. */
    @PutMapping("/{room}/encounter/initiative")
    public EncounterView setInitiative(@PathVariable String room,
                                       @Valid @RequestBody SetInitiativeRequest req) {
        return encounterService.setInitiative(room, req);
    }
}
