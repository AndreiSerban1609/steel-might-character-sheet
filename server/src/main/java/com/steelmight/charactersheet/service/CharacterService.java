package com.steelmight.charactersheet.service;

import com.steelmight.charactersheet.dto.*;
import com.steelmight.charactersheet.engine.*;
import com.steelmight.charactersheet.model.*;
import com.steelmight.charactersheet.gamedata.GameDataProvider;
import com.steelmight.charactersheet.repository.CharacterRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional
public class CharacterService {

    private final CharacterRepository repo;
    private final DamageResolutionPipeline damagePipeline;
    private final HealingResolutionPipeline healingPipeline;
    private final StatDerivationEngine statEngine;
    private final GameDataProvider gameData;

    public CharacterService(CharacterRepository repo,
                            DamageResolutionPipeline damagePipeline,
                            HealingResolutionPipeline healingPipeline,
                            StatDerivationEngine statEngine,
                            GameDataProvider gameData) {
        this.repo = repo;
        this.damagePipeline = damagePipeline;
        this.healingPipeline = healingPipeline;
        this.statEngine = statEngine;
        this.gameData = gameData;
    }

    /** Deterministic, human-readable id: slug(roomName) + "-" + lowercase(email). */
    public static String characterId(String roomName, String email) {
        String roomSlug = roomName.trim().toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return roomSlug + "-" + email.trim().toLowerCase();
    }

    // --- Queries ---

    public GameCharacter getCharacter(String playerId) {
        return repo.findById(playerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Character not found"));
    }

    public List<GameCharacter> getAllCharacters() {
        return repo.findAll();
    }

    public List<RosterEntry> getRoster(String room) {
        var stream = repo.findAll().stream();
        if (room != null && !room.isBlank()) {
            String wanted = room.trim();
            stream = stream.filter(c -> wanted.equalsIgnoreCase(c.getRoomName()));
        }
        return stream
                .map(c -> new RosterEntry(
                        c.getPlayerId(), c.getRoomName(), c.getEmail(), c.getName(), c.getLevel(),
                        c.getPathId(), c.getClassId(),
                        c.getHp() != null ? c.getHp().getCurrent() : 0,
                        statEngine.computeMaxHP(c), statEngine.computeAC(c)))
                .toList();
    }

    public CharacterCreatedResponse findByRoomEmail(String room, String email) {
        requireText(room, "room");
        requireText(email, "email");
        String id = characterId(room, email);
        var c = repo.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "no character for this room and email"));
        return new CharacterCreatedResponse(id, buildCombatSnapshot(c));
    }

    public CharacterCreatedResponse createCharacter(CreateCharacterRequest req) {
        requireText(req.roomName(), "roomName");
        requireText(req.email(), "email");
        requireText(req.name(), "name");
        requireText(req.pathId(), "pathId");
        requireText(req.classId(), "classId");
        if (!req.email().contains("@")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email must be a valid address");
        }
        validatePathAndClass(req.pathId(), req.classId());

        String id = characterId(req.roomName(), req.email());
        if (id.startsWith("-")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "roomName must contain letters or digits");
        }
        if (repo.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "a character already exists for this room and email");
        }

        int level = req.level() != null ? req.level() : 1;
        if (level < 1 || level > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "level out of range (1-20): " + level);
        }

        var c = new GameCharacter(id);
        c.setRoomName(req.roomName().trim());
        c.setEmail(req.email().trim().toLowerCase());
        c.setName(req.name().trim());
        c.setPathId(req.pathId());
        c.setClassId(req.classId());
        c.setLevel(level);
        c.setSpeed(30);
        c.setAp(new ActionPoints(6, 6, 10));
        c.setStats(req.stats() != null ? statsFrom(req.stats()) : defaultStats());

        int maxHp = statEngine.computeMaxHP(c);
        int maxMana = statEngine.computeMaxMana(c);
        c.setHp(new HitPoints(maxHp, maxHp, 0));
        c.setMana(new ManaPool(maxMana, maxMana));

        repo.save(c);
        return new CharacterCreatedResponse(id, buildCombatSnapshot(c));
    }

    public CombatSnapshot getCombatSnapshot(String playerId) {
        var c = getCharacter(playerId);
        return buildCombatSnapshot(c);
    }

    public BioSnapshot getBioSnapshot(String playerId) {
        var c = getCharacter(playerId);
        var appearanceView = c.getAppearance() != null
                ? new BioSnapshot.AppearanceView(
                        c.getAppearance().getAge(), c.getAppearance().getEyeColor(),
                        c.getAppearance().getHeightCm(), c.getAppearance().getSkin(),
                        c.getAppearance().getWeightKg(), c.getAppearance().getHair())
                : null;
        return new BioSnapshot(
                c.getName(), c.getPortraitUrl(), c.getSymbolUrl(),
                c.getRaceId(), c.getPathId(), c.getClassId(), c.getSpecializationId(),
                c.getLevel(), c.getBackground(), c.getAlignment(),
                appearanceView,
                c.getPersonalityTraits(), c.getIdeals(), c.getBonds(), c.getFlaws(),
                c.getBackstory(), c.getNotes(),
                c.getAllies(), c.getOrganizations(), c.getTitles()
        );
    }

    public InventorySnapshot getInventorySnapshot(String playerId) {
        var c = getCharacter(playerId);
        return buildInventorySnapshot(c);
    }

    private InventorySnapshot buildInventorySnapshot(GameCharacter c) {
        var items = c.getInventory().stream()
                .map(e -> new InventorySnapshot.ItemView(
                        e.getItemId(), e.getQuantity(), e.getUpgradeTier(), e.isEquipped(),
                        gameData.getItemSpace(e.getItemId())))
                .toList();
        return new InventorySnapshot(items, c.getGold(),
                statEngine.computeCarriedSpace(c), statEngine.computeCarryCapacity(c));
    }

    public InventorySnapshot updateInventory(String playerId, UpdateInventoryRequest req) {
        var c = getCharacter(playerId);

        var inputs = req.items() != null ? req.items() : List.<UpdateInventoryRequest.ItemInput>of();
        double totalSpace = 0;
        for (var in : inputs) {
            if (in.itemId() == null || in.itemId().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "item is missing an id");
            }
            if (!gameData.isKnownItem(in.itemId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown item: " + in.itemId());
            }
            int qty = in.quantity() != null ? in.quantity() : 1;
            if (qty < 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "quantity must be at least 1 for " + in.itemId());
            }
            int tier = in.upgradeTier() != null ? in.upgradeTier() : 0;
            if (tier < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "upgradeTier must be >= 0");
            }
            totalSpace += gameData.getItemSpace(in.itemId()) * qty;
        }

        // Q33: hard-prevent carrying more than capacity.
        int capacity = statEngine.computeCarryCapacity(c);
        if (Math.round(totalSpace * 100.0) / 100.0 > capacity) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    String.format("over carrying capacity: %.1f / %d slots", totalSpace, capacity));
        }

        if (req.gold() != null) {
            if (req.gold() < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "gold must be >= 0");
            }
            c.setGold(req.gold());
        }

        c.getInventory().clear();
        for (var in : inputs) {
            c.addItem(new InventoryEntry(
                    in.itemId(),
                    in.quantity() != null ? in.quantity() : 1,
                    in.upgradeTier() != null ? in.upgradeTier() : 0,
                    in.equipped() != null && in.equipped()));
        }

        repo.save(c);
        return buildInventorySnapshot(c);
    }

    public SpellbookSnapshot getSpellbookSnapshot(String playerId) {
        var c = getCharacter(playerId);
        boolean concentrating = c.getActiveEffects().stream()
                .anyMatch(e -> "concentrating".equals(e.getEffectId()));
        return new SpellbookSnapshot(
                c.getKnownSpells(), c.getPreparedSpells(),
                c.getMana().getCurrent(), c.getMana().getMax(),
                concentrating,
                statEngine.getSpellcastingAttribute(c),
                statEngine.computeSpellSaveDC(c),
                statEngine.computeSpellAttackBonus(c)
        );
    }

    // --- Actions ---

    public ActionResponse<CombatSnapshot> damage(String playerId, DamageRequest req) {
        var c = getCharacter(playerId);
        var event = new DamageEvent(req.value(), req.damageType());
        var result = damagePipeline.resolve(event, c);
        repo.save(c);
        return new ActionResponse<>(result, buildCombatSnapshot(c));
    }

    public ActionResponse<CombatSnapshot> heal(String playerId, HealRequest req) {
        var c = getCharacter(playerId);
        var event = new HealEvent(req.value());
        var result = healingPipeline.resolve(event, c);
        repo.save(c);
        return new ActionResponse<>(result, buildCombatSnapshot(c));
    }

    public ActionResponse<CombatSnapshot> applyEffect(String playerId, ApplyEffectRequest req) {
        var c = getCharacter(playerId);
        var result = new ResolutionResult();

        // TODO: check Warded, check immunity, handle stacking rules
        var effect = new ActiveEffect(req.effectId(), req.source(), req.stacks(),
                req.value(), req.duration(), c.getActiveEffects().size());
        c.addEffect(effect);
        result.addStep("apply-effect", "Applied " + req.effectId(), 0, 1);

        repo.save(c);
        return new ActionResponse<>(result, buildCombatSnapshot(c));
    }

    public ActionResponse<CombatSnapshot> removeEffect(String playerId, String effectId) {
        var c = getCharacter(playerId);
        var result = new ResolutionResult();

        c.getActiveEffects().removeIf(e -> e.getEffectId().equals(effectId));
        result.addStep("remove-effect", "Removed " + effectId, 1, 0);

        repo.save(c);
        return new ActionResponse<>(result, buildCombatSnapshot(c));
    }

    public ActionResponse<CombatSnapshot> spendResource(String playerId, SpendResourceRequest req) {
        var c = getCharacter(playerId);
        var result = new ResolutionResult();

        switch (req.resource()) {
            case "ap" -> {
                int before = c.getAp().getCurrent();
                int after = Math.max(0, before - req.amount());
                c.getAp().setCurrent(after);
                result.addStep("spend-ap", "Spent " + req.amount() + " AP", before, after);
            }
            case "mana" -> {
                int before = c.getMana().getCurrent();
                int after = Math.max(0, before - req.amount());
                c.getMana().setCurrent(after);
                result.addStep("spend-mana", "Spent " + req.amount() + " mana", before, after);
            }
            default -> {
                if (c.getResource() != null && req.resource().equals(c.getResource().getType())) {
                    int before = c.getResource().getCurrent();
                    int after = Math.max(0, before - req.amount());
                    c.getResource().setCurrent(after);
                    result.addStep("spend-" + req.resource(), "Spent " + req.amount() + " " + req.resource(), before, after);
                } else {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown resource: " + req.resource());
                }
            }
        }

        repo.save(c);
        return new ActionResponse<>(result, buildCombatSnapshot(c));
    }

    public ActionResponse<CombatSnapshot> turnStart(String playerId) {
        var c = getCharacter(playerId);
        var result = new ResolutionResult();
        // TODO: tick DoTs (Burning, Envenomed), AP recovery (check Dazed), Suffocating
        repo.save(c);
        return new ActionResponse<>(result, buildCombatSnapshot(c));
    }

    public ActionResponse<CombatSnapshot> turnEnd(String playerId) {
        var c = getCharacter(playerId);
        var result = new ResolutionResult();
        // TODO: Regenerating heal, Suffocating exhaustion, duration expiry
        repo.save(c);
        return new ActionResponse<>(result, buildCombatSnapshot(c));
    }

    public ActionResponse<CombatSnapshot> longRest(String playerId) {
        var c = getCharacter(playerId);
        var result = new ResolutionResult();
        // TODO: restore HP, mana, class resources, remove consumed effects, etc.
        repo.save(c);
        return new ActionResponse<>(result, buildCombatSnapshot(c));
    }

    public ActionResponse<CombatSnapshot> shortRest(String playerId) {
        var c = getCharacter(playerId);
        var result = new ResolutionResult();
        // TODO: partial resource recovery
        repo.save(c);
        return new ActionResponse<>(result, buildCombatSnapshot(c));
    }

    // --- Profile ---

    public BioSnapshot updateBio(String playerId, BioUpdateRequest req) {
        var c = getCharacter(playerId);
        if (req.name() != null) c.setName(req.name());
        if (req.portraitUrl() != null) c.setPortraitUrl(req.portraitUrl());
        if (req.symbolUrl() != null) c.setSymbolUrl(req.symbolUrl());
        if (req.background() != null) c.setBackground(req.background());
        if (req.alignment() != null) c.setAlignment(req.alignment());
        if (req.personalityTraits() != null) c.setPersonalityTraits(req.personalityTraits());
        if (req.ideals() != null) c.setIdeals(req.ideals());
        if (req.bonds() != null) c.setBonds(req.bonds());
        if (req.flaws() != null) c.setFlaws(req.flaws());
        if (req.backstory() != null) c.setBackstory(req.backstory());
        if (req.notes() != null) c.setNotes(req.notes());
        if (req.allies() != null) c.setAllies(req.allies());
        if (req.organizations() != null) c.setOrganizations(req.organizations());
        if (req.titles() != null) c.setTitles(req.titles());
        if (req.appearance() != null) {
            var a = req.appearance();
            c.setAppearance(new Appearance(a.age(), a.eyeColor(), a.heightCm(),
                    a.skin(), a.weightKg(), a.hair()));
        }
        repo.save(c);
        return getBioSnapshot(playerId);
    }

    public CombatSnapshot updateStats(String playerId, UpdateStatsRequest req) {
        var c = getCharacter(playerId);
        if (req.stats() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "stats are required");
        }
        for (var ability : AbilityScore.values()) {
            Integer value = req.stats().get(ability);
            if (value == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "missing stat: " + ability);
            }
            if (value < 1 || value > 40) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        ability + " out of range (1-40): " + value);
            }
        }
        var stats = c.getStats();
        for (var ability : AbilityScore.values()) {
            stats.set(ability, req.stats().get(ability));
        }
        clampPoolsToMax(c);
        repo.save(c);
        return buildCombatSnapshot(c);
    }

    public CombatSnapshot updateVitals(String playerId, VitalsRequest req) {
        var c = getCharacter(playerId);
        if (req.currentHp() != null) {
            if (req.currentHp() < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "currentHp must be >= 0");
            }
            c.getHp().setCurrent(Math.min(req.currentHp(), statEngine.computeMaxHP(c)));
        }
        if (req.tempHp() != null) {
            if (req.tempHp() < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tempHp must be >= 0");
            }
            c.getHp().setTemp(req.tempHp());
        }
        if (req.currentAp() != null) {
            if (req.currentAp() < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "currentAp must be >= 0");
            }
            c.getAp().setCurrent(Math.min(req.currentAp(), c.getAp().getMax()));
        }
        if (req.currentMana() != null) {
            if (req.currentMana() < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "currentMana must be >= 0");
            }
            c.getMana().setCurrent(Math.min(req.currentMana(), statEngine.computeMaxMana(c)));
        }
        repo.save(c);
        return buildCombatSnapshot(c);
    }

    public CombatSnapshot updateIdentity(String playerId, IdentityRequest req) {
        var c = getCharacter(playerId);
        if (req.name() != null) {
            if (req.name().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name cannot be blank");
            }
            c.setName(req.name().trim());
        }
        if (req.level() != null) {
            if (req.level() < 1 || req.level() > 20) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "level out of range (1-20): " + req.level());
            }
            c.setLevel(req.level());
        }
        clampPoolsToMax(c);
        repo.save(c);
        return buildCombatSnapshot(c);
    }

    public CombatSnapshot updateProficiencies(String playerId, ProficienciesRequest req) {
        var c = getCharacter(playerId);
        var valid = validSkillIds();
        var ids = req.skillIds() != null ? req.skillIds() : List.<String>of();
        for (String s : ids) {
            if (!valid.contains(s)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown skill: " + s);
            }
        }
        c.getProficiencies().clear();
        c.getProficiencies().addAll(ids.stream().distinct().toList());
        repo.save(c);
        return buildCombatSnapshot(c);
    }

    public void deleteCharacter(String playerId) {
        repo.deleteById(playerId);
    }

    // --- Snapshot builders ---

    /** Keep current HP/mana from exceeding their derived maxima after a change that lowers them. */
    private void clampPoolsToMax(GameCharacter c) {
        int maxHp = statEngine.computeMaxHP(c);
        if (c.getHp().getCurrent() > maxHp) c.getHp().setCurrent(maxHp);
        int maxMana = statEngine.computeMaxMana(c);
        if (c.getMana().getCurrent() > maxMana) c.getMana().setCurrent(maxMana);
    }

    private Set<String> validSkillIds() {
        var set = new HashSet<String>();
        var skills = gameData.getSkills();
        if (skills != null && skills.isArray()) {
            for (var s : skills) set.add(s.path("id").asText());
        }
        return set;
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
    }

    private void validatePathAndClass(String pathId, String classId) {
        var paths = gameData.getClasses();
        if (paths != null && paths.isArray()) {
            for (var p : paths) {
                if (pathId.equals(p.path("id").asText())) {
                    for (var cl : p.path("classes")) {
                        if (classId.equals(cl.asText())) return;
                    }
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "class '" + classId + "' is not part of path '" + pathId + "'");
                }
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown path '" + pathId + "'");
    }

    private Stats statsFrom(Map<AbilityScore, Integer> map) {
        for (var ability : AbilityScore.values()) {
            Integer v = map.get(ability);
            if (v == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "missing stat: " + ability);
            }
            if (v < 1 || v > 40) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ability + " out of range (1-40): " + v);
            }
        }
        return new Stats(map.get(AbilityScore.STR), map.get(AbilityScore.DEX), map.get(AbilityScore.CON),
                map.get(AbilityScore.INT), map.get(AbilityScore.WIS), map.get(AbilityScore.WILL),
                map.get(AbilityScore.CHA));
    }

    /** character-creation.json statArray over [str, dex, const, int, wis, will, cha]. */
    private static Stats defaultStats() {
        return new Stats(15, 13, 12, 11, 10, 9, 8);
    }

    private CombatSnapshot buildCombatSnapshot(GameCharacter c) {
        var effectViews = c.getActiveEffects().stream()
                .map(e -> new CombatSnapshot.EffectView(
                        e.getEffectId(), e.getEffectId(), e.getStacks(),
                        e.getValue(), e.getRemainingRounds()))
                .toList();

        // TODO: derive conditions from active effects (e.g., below 50% HP → Bloodied)
        List<String> conditions = List.of();

        return new CombatSnapshot(
                c.getName(), c.getLevel(), c.getPathId(), c.getClassId(),
                c.getStats().toMap(), c.getStats().modifierMap(),
                new CombatSnapshot.HpView(c.getHp().getCurrent(), statEngine.computeMaxHP(c), c.getHp().getTemp()),
                statEngine.computeAC(c),
                statEngine.computePA(c),
                statEngine.computeMA(c),
                new CombatSnapshot.ApView(c.getAp().getCurrent(), statEngine.computeAPRecovery(c), c.getAp().getMax()),
                new CombatSnapshot.ManaView(c.getMana().getCurrent(), statEngine.computeMaxMana(c)),
                statEngine.computeSpeed(c), c.getBonusInitiative(), c.getDeathStacks(),
                c.getSavingThrowProficiencies(),
                c.getProficiencies(),
                effectViews,
                statEngine.findEquippedWeaponId(c),
                statEngine.findEquippedArmorId(c),
                conditions
        );
    }
}
