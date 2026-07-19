package com.steelmight.charactersheet.controller;

import com.steelmight.charactersheet.dto.AuditView;
import com.steelmight.charactersheet.dto.DeckTemplate;
import com.steelmight.charactersheet.dto.EncounterView;
import com.steelmight.charactersheet.dto.SetInitiativeRequest;
import com.steelmight.charactersheet.dto.StartEncounterRequest;
import com.steelmight.charactersheet.service.AuditService;
import com.steelmight.charactersheet.service.CharacterService;
import com.steelmight.charactersheet.service.DeckTemplateService;
import com.steelmight.charactersheet.service.EncounterService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final DeckTemplateService deckService;
    private final EncounterService encounterService;
    private final CharacterService characterService;
    private final AuditService auditService;

    public RoomController(DeckTemplateService deckService, EncounterService encounterService,
                          CharacterService characterService, AuditService auditService) {
        this.deckService = deckService;
        this.encounterService = encounterService;
        this.characterService = characterService;
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

    /** Rolls d20 + DEX mod + initiative bonus per participant and opens the turn order.
     *  Also runs each participant's combat-start pipeline (Q18: AP → starting value),
     *  then begins the first character's turn — players only ever END turns in combat. */
    @PostMapping("/{room}/encounter/start")
    public EncounterView startEncounter(@PathVariable String room,
                                        @RequestBody(required = false) StartEncounterRequest req) {
        var view = encounterService.start(room, req);
        for (var entry : view.entries()) {
            characterService.combatStart(entry.playerId());
        }
        autoStartCurrentTurn(room);
        return encounterService.get(room);
    }

    @PostMapping("/{room}/encounter/end")
    public EncounterView endEncounter(@PathVariable String room) {
        return encounterService.end(room);
    }

    /** DM override: skip the current turn (AFK player) and advance. */
    @PostMapping("/{room}/encounter/next")
    public EncounterView nextTurn(@PathVariable String room) {
        encounterService.forceNext(room);
        autoStartCurrentTurn(room);
        return encounterService.get(room);
    }

    /**
     * Turns begin automatically (players only end them). Composed here, like
     * combat-start, to keep EncounterService free of a CharacterService cycle.
     */
    private void autoStartCurrentTurn(String room) {
        var view = encounterService.get(room);
        if (!view.active() || view.turnStarted() || view.currentPlayerId() == null) return;
        boolean currentIsDead = view.entries().stream()
                .anyMatch(e -> e.playerId().equals(view.currentPlayerId()) && "DEAD".equals(e.status()));
        if (!currentIsDead) characterService.turnStart(view.currentPlayerId());
    }

    /** DM override: change a participant's initiative mid-combat. */
    @PutMapping("/{room}/encounter/initiative")
    public EncounterView setInitiative(@PathVariable String room,
                                       @Valid @RequestBody SetInitiativeRequest req) {
        return encounterService.setInitiative(room, req);
    }
}
