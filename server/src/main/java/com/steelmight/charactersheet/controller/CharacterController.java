package com.steelmight.charactersheet.controller;

import com.steelmight.charactersheet.dto.*;
import com.steelmight.charactersheet.service.AuditService;
import com.steelmight.charactersheet.service.CharacterService;
import com.steelmight.charactersheet.service.DeckTemplateService;
import com.steelmight.charactersheet.service.EquipmentService;
import com.steelmight.charactersheet.service.ProgressionService;
import com.steelmight.charactersheet.service.ShopService;
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
    private final ShopService shopService;
    private final EquipmentService equipmentService;
    private final ProgressionService progressionService;
    private final AuditService auditService;

    public CharacterController(CharacterService service, SkillCheckService skillCheckService,
                              DeckTemplateService deckService, ShopService shopService,
                              EquipmentService equipmentService, ProgressionService progressionService,
                              AuditService auditService) {
        this.service = service;
        this.skillCheckService = skillCheckService;
        this.deckService = deckService;
        this.shopService = shopService;
        this.equipmentService = equipmentService;
        this.progressionService = progressionService;
        this.auditService = auditService;
    }

    // --- Queries ---

    @GetMapping
    public List<CombatSnapshot> getAllCharacters() {
        return service.getAllCharacters().stream()
                .map(c -> service.getCombatSnapshot(c.getPlayerId()))
                .toList();
    }

    /**
     * The player's own combat history, newest first (demo feedback #22). Self-scoped and
     * combat-only by construction — there is no room-wide variant and no GM toggle.
     */
    @GetMapping("/{playerId}/log")
    public List<AuditView> combatLog(@PathVariable String playerId,
                                     @RequestParam(defaultValue = "50") int limit) {
        return auditService.combatLogFor(playerId, limit);
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

    @PostMapping("/{playerId}/actions/weapon-attack")
    public ActionResponse<CombatSnapshot> weaponAttack(@PathVariable String playerId,
                                                       @RequestBody(required = false) WeaponAttackRequest req) {
        return service.weaponAttack(playerId, req);
    }

    @PostMapping("/{playerId}/actions/cast")
    public ActionResponse<CombatSnapshot> cast(@PathVariable String playerId,
                                               @Valid @RequestBody CastRequest req) {
        return service.cast(playerId, req);
    }

    @PostMapping("/{playerId}/actions/level-up")
    public ActionResponse<CombatSnapshot> levelUp(@PathVariable String playerId,
                                                  @RequestBody(required = false) LevelUpRequest req) {
        return progressionService.levelUp(playerId, req);
    }

    @PostMapping("/{playerId}/actions/equip")
    public ActionResponse<CombatSnapshot> equip(@PathVariable String playerId,
                                                @RequestBody EquipRequest req) {
        return equipmentService.equip(playerId, req);
    }

    @PostMapping("/{playerId}/actions/unequip")
    public ActionResponse<CombatSnapshot> unequip(@PathVariable String playerId,
                                                  @RequestBody EquipRequest req) {
        return equipmentService.unequip(playerId, req);
    }

    @PostMapping("/{playerId}/actions/purchase")
    public ActionResponse<InventorySnapshot> purchase(@PathVariable String playerId,
                                                      @RequestBody PurchaseRequest req) {
        return shopService.purchase(playerId, req);
    }

    @PostMapping("/{playerId}/actions/sell")
    public ActionResponse<InventorySnapshot> sell(@PathVariable String playerId,
                                                  @RequestBody SellRequest req) {
        return shopService.sell(playerId, req);
    }

    @PostMapping("/{playerId}/actions/upgrade")
    public ActionResponse<InventorySnapshot> upgrade(@PathVariable String playerId,
                                                     @RequestBody UpgradeRequest req) {
        return shopService.upgrade(playerId, req);
    }

    @PostMapping("/{playerId}/actions/cast-scroll")
    public ActionResponse<CombatSnapshot> castScroll(@PathVariable String playerId,
                                                     @RequestBody CastScrollRequest req) {
        return shopService.castScroll(playerId, req);
    }

    @PostMapping("/{playerId}/actions/use-consumable")
    public ActionResponse<CombatSnapshot> useConsumable(@PathVariable String playerId,
                                                        @RequestBody UseConsumableRequest req) {
        return shopService.useConsumable(playerId, req);
    }

    @PostMapping("/{playerId}/actions/prepare-spells")
    public ActionResponse<CombatSnapshot> prepareSpells(@PathVariable String playerId,
                                                        @RequestBody PrepareSpellsRequest req) {
        return service.prepareSpells(playerId, req);
    }

    @PostMapping("/{playerId}/actions/spend-resource")
    public ActionResponse<CombatSnapshot> spendResource(@PathVariable String playerId,
                                                         @Valid @RequestBody SpendResourceRequest req) {
        return service.spendResource(playerId, req);
    }

    /** Ready a custom reaction, paying its AP now (2026-08-27). */
    @PostMapping("/{playerId}/actions/prepare-reaction")
    public ActionResponse<CombatSnapshot> prepareReaction(@PathVariable String playerId,
                                                           @Valid @RequestBody PrepareReactionRequest req) {
        return service.prepareReaction(playerId, req);
    }

    /** A prepared reaction triggered (used) or was called off — removes it, no refund. */
    @PostMapping("/{playerId}/actions/resolve-reaction")
    public ActionResponse<CombatSnapshot> resolveReaction(@PathVariable String playerId,
                                                           @Valid @RequestBody ResolveReactionRequest req) {
        return service.resolveReaction(playerId, req);
    }

    @PostMapping("/{playerId}/actions/gain-resource")
    public ActionResponse<CombatSnapshot> gainResource(@PathVariable String playerId,
                                                        @Valid @RequestBody GainResourceRequest req) {
        return service.gainResource(playerId, req);
    }

    @PostMapping("/{playerId}/actions/turn-start")
    public ActionResponse<CombatSnapshot> turnStart(@PathVariable String playerId) {
        return service.turnStart(playerId);
    }

    @PostMapping("/{playerId}/actions/turn-end")
    public ActionResponse<CombatSnapshot> turnEnd(@PathVariable String playerId) {
        return service.turnEnd(playerId);
    }

    @PostMapping("/{playerId}/actions/revive")
    public ActionResponse<CombatSnapshot> revive(@PathVariable String playerId,
                                                  @RequestBody ReviveRequest req) {
        return service.revive(playerId, req);
    }

    @PostMapping("/{playerId}/actions/combat-start")
    public ActionResponse<CombatSnapshot> combatStart(@PathVariable String playerId) {
        return service.combatStart(playerId);
    }

    /** Single tiered rest (Q20) — replaces the former short-rest/long-rest pair. */
    @PostMapping("/{playerId}/actions/rest")
    public ActionResponse<CombatSnapshot> rest(@PathVariable String playerId,
                                               @RequestBody(required = false) RestRequest req) {
        return service.rest(playerId, req);
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

    @GetMapping("/{playerId}/custom-items")
    public List<CustomItemView> getCustomItems(@PathVariable String playerId) {
        return service.getCustomItems(playerId);
    }

    @PutMapping("/{playerId}/custom-items")
    public List<CustomItemView> updateCustomItems(@PathVariable String playerId,
                                                  @RequestBody UpdateCustomItemsRequest req) {
        return service.updateCustomItems(playerId, req);
    }

    @PutMapping("/{playerId}/stat-overrides")
    public CombatSnapshot updateStatOverrides(@PathVariable String playerId,
                                              @RequestBody StatOverridesRequest req) {
        return service.updateStatOverrides(playerId, req);
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
        return skillCheckService.draw(playerId, req.skillId(), req.advantage());
    }

    /** Proficiency gamble: forfeit the current card, draw the next; the d10 stays. */
    @PostMapping("/{playerId}/skill-check/redraw")
    public SkillCheckResult skillCheckRedraw(@PathVariable String playerId) {
        return skillCheckService.redraw(playerId);
    }

    /** Accept the final card — applies consume/burn removal and closes the check. */
    @PostMapping("/{playerId}/skill-check/accept")
    public SkillCheckAccepted skillCheckAccept(@PathVariable String playerId) {
        return skillCheckService.accept(playerId);
    }

    // ── Class abilities (Epic 1) ──

    @GetMapping("/{playerId}/abilities")
    public AbilitiesSnapshot getAbilities(@PathVariable String playerId) {
        return service.getAbilitiesSnapshot(playerId);
    }

    /** Free-form picker: replaces the choice-group picks (class + level validated). */
    @PutMapping("/{playerId}/abilities")
    public AbilitiesSnapshot updateAbilities(@PathVariable String playerId,
                                             @RequestBody UpdateAbilitiesRequest req) {
        return service.updateKnownAbilities(playerId, req);
    }

    /** Free-text abilities pending official rulings — replace-list semantics. */
    @PutMapping("/{playerId}/abilities/custom")
    public AbilitiesSnapshot updateCustomAbilities(@PathVariable String playerId,
                                                   @RequestBody UpdateCustomAbilitiesRequest req) {
        return service.updateCustomAbilities(playerId, req);
    }

    /** Print a free-text ability into the resolution log (table adjudicates). */
    @PostMapping("/{playerId}/actions/use-custom-ability")
    public ActionResponse<CombatSnapshot> useCustomAbility(@PathVariable String playerId,
                                                           @RequestBody UseCustomAbilityRequest req) {
        return service.useCustomAbility(playerId, req);
    }

    /** Validate → spend costs → resolve (auto) or print the rule (manual). */
    @PostMapping("/{playerId}/actions/use-ability")
    public ActionResponse<CombatSnapshot> useAbility(@PathVariable String playerId,
                                                     @Valid @RequestBody UseAbilityRequest req) {
        return service.useAbility(playerId, req);
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
