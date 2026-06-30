package com.steelmight.charactersheet.controller;

import com.steelmight.charactersheet.dto.*;
import com.steelmight.charactersheet.service.CharacterService;
import com.steelmight.charactersheet.service.DeckTemplateService;
import com.steelmight.charactersheet.service.SkillCheckService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/characters")
public class CharacterController {

    private final CharacterService service;
    private final SkillCheckService skillCheckService;
    private final DeckTemplateService deckService;

    public CharacterController(CharacterService service, SkillCheckService skillCheckService,
                              DeckTemplateService deckService) {
        this.service = service;
        this.skillCheckService = skillCheckService;
        this.deckService = deckService;
    }

    // --- Queries ---

    @GetMapping
    public List<CombatSnapshot> getAllCharacters() {
        return service.getAllCharacters().stream()
                .map(c -> service.getCombatSnapshot(c.getPlayerId()))
                .toList();
    }

    @GetMapping("/roster")
    public List<RosterEntry> getRoster(@RequestParam(required = false) String room) {
        return service.getRoster(room);
    }

    @GetMapping("/find")
    public CharacterCreatedResponse find(@RequestParam String room, @RequestParam String email) {
        return service.findByRoomEmail(room, email);
    }

    @PostMapping
    public ResponseEntity<CharacterCreatedResponse> createCharacter(@RequestBody CreateCharacterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createCharacter(req));
    }

    @GetMapping("/{playerId}/combat")
    public CombatSnapshot getCombat(@PathVariable String playerId) {
        return service.getCombatSnapshot(playerId);
    }

    @GetMapping("/{playerId}/bio")
    public BioSnapshot getBio(@PathVariable String playerId) {
        return service.getBioSnapshot(playerId);
    }

    @GetMapping("/{playerId}/inventory")
    public InventorySnapshot getInventory(@PathVariable String playerId) {
        return service.getInventorySnapshot(playerId);
    }

    @GetMapping("/{playerId}/spells")
    public SpellbookSnapshot getSpells(@PathVariable String playerId) {
        return service.getSpellbookSnapshot(playerId);
    }

    // --- Actions ---

    @PostMapping("/{playerId}/actions/damage")
    public ActionResponse<CombatSnapshot> damage(@PathVariable String playerId,
                                                  @Valid @RequestBody DamageRequest req) {
        return service.damage(playerId, req);
    }

    @PostMapping("/{playerId}/actions/heal")
    public ActionResponse<CombatSnapshot> heal(@PathVariable String playerId,
                                                @Valid @RequestBody HealRequest req) {
        return service.heal(playerId, req);
    }

    @PostMapping("/{playerId}/actions/apply-effect")
    public ActionResponse<CombatSnapshot> applyEffect(@PathVariable String playerId,
                                                       @Valid @RequestBody ApplyEffectRequest req) {
        return service.applyEffect(playerId, req);
    }

    @PostMapping("/{playerId}/actions/remove-effect")
    public ActionResponse<CombatSnapshot> removeEffect(@PathVariable String playerId,
                                                        @RequestParam String effectId) {
        return service.removeEffect(playerId, effectId);
    }

    @PostMapping("/{playerId}/actions/spend-resource")
    public ActionResponse<CombatSnapshot> spendResource(@PathVariable String playerId,
                                                         @Valid @RequestBody SpendResourceRequest req) {
        return service.spendResource(playerId, req);
    }

    @PostMapping("/{playerId}/actions/turn-start")
    public ActionResponse<CombatSnapshot> turnStart(@PathVariable String playerId) {
        return service.turnStart(playerId);
    }

    @PostMapping("/{playerId}/actions/turn-end")
    public ActionResponse<CombatSnapshot> turnEnd(@PathVariable String playerId) {
        return service.turnEnd(playerId);
    }

    @PostMapping("/{playerId}/actions/long-rest")
    public ActionResponse<CombatSnapshot> longRest(@PathVariable String playerId) {
        return service.longRest(playerId);
    }

    @PostMapping("/{playerId}/actions/short-rest")
    public ActionResponse<CombatSnapshot> shortRest(@PathVariable String playerId) {
        return service.shortRest(playerId);
    }

    // --- Profile ---

    @PutMapping("/{playerId}/bio")
    public BioSnapshot updateBio(@PathVariable String playerId,
                                  @RequestBody BioUpdateRequest req) {
        return service.updateBio(playerId, req);
    }

    @PutMapping("/{playerId}/inventory")
    public InventorySnapshot updateInventory(@PathVariable String playerId,
                                             @RequestBody UpdateInventoryRequest req) {
        return service.updateInventory(playerId, req);
    }

    @PutMapping("/{playerId}/stats")
    public CombatSnapshot updateStats(@PathVariable String playerId,
                                      @RequestBody UpdateStatsRequest req) {
        return service.updateStats(playerId, req);
    }

    @PutMapping("/{playerId}/vitals")
    public CombatSnapshot updateVitals(@PathVariable String playerId,
                                       @RequestBody VitalsRequest req) {
        return service.updateVitals(playerId, req);
    }

    @PutMapping("/{playerId}/identity")
    public CombatSnapshot updateIdentity(@PathVariable String playerId,
                                         @RequestBody IdentityRequest req) {
        return service.updateIdentity(playerId, req);
    }

    @PutMapping("/{playerId}/proficiencies")
    public CombatSnapshot updateProficiencies(@PathVariable String playerId,
                                              @RequestBody ProficienciesRequest req) {
        return service.updateProficiencies(playerId, req);
    }

    @PostMapping("/{playerId}/skill-check")
    public SkillCheckResult skillCheck(@PathVariable String playerId,
                                       @RequestBody SkillCheckRequest req) {
        return skillCheckService.draw(playerId, req.skillId());
    }

    @GetMapping("/{playerId}/deck")
    public PlayerDeckView getPlayerDeck(@PathVariable String playerId) {
        return deckService.getPlayerDeckView(playerId);
    }

    @PutMapping("/{playerId}/deck")
    public PlayerDeckView updatePlayerDeck(@PathVariable String playerId,
                                           @RequestBody PlayerDeckConfig config) {
        return deckService.updatePlayerDeck(playerId, config);
    }

    @DeleteMapping("/{playerId}")
    public void deleteCharacter(@PathVariable String playerId) {
        service.deleteCharacter(playerId);
    }
}
