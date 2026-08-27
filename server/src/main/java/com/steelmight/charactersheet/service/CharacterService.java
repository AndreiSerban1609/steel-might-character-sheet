package com.steelmight.charactersheet.service;

import com.steelmight.charactersheet.dto.*;
import com.steelmight.charactersheet.engine.*;
import com.steelmight.charactersheet.model.*;
import com.steelmight.charactersheet.gamedata.AbilityDefinition;
import com.steelmight.charactersheet.gamedata.Dice;
import com.steelmight.charactersheet.gamedata.DiceFormula;
import com.steelmight.charactersheet.gamedata.GameDataProvider;
import com.steelmight.charactersheet.gamedata.ItemKind;
import com.steelmight.charactersheet.repository.CharacterRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
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
    private final EffectApplicationEngine effectEngine;
    private final TurnTickService turnTickService;
    private final GameDataProvider gameData;
    private final RandomSource randomSource;
    private final DeckTemplateService deckTemplates;
    private final EncounterService encounters;
    private final AuditService audit;
    private final CombatActionService combatActions;
    private final TurnFlowService turnFlow;
    private final CombatantLookup combatants;

    public CharacterService(CharacterRepository repo,
                            DamageResolutionPipeline damagePipeline,
                            HealingResolutionPipeline healingPipeline,
                            StatDerivationEngine statEngine,
                            EffectApplicationEngine effectEngine,
                            TurnTickService turnTickService,
                            GameDataProvider gameData,
                            RandomSource randomSource,
                            DeckTemplateService deckTemplates,
                            EncounterService encounters,
                            AuditService audit,
                            CombatActionService combatActions,
                            TurnFlowService turnFlow,
                            CombatantLookup combatants) {
        this.combatActions = combatActions;
        this.turnFlow = turnFlow;
        this.combatants = combatants;
        this.repo = repo;
        this.damagePipeline = damagePipeline;
        this.healingPipeline = healingPipeline;
        this.statEngine = statEngine;
        this.effectEngine = effectEngine;
        this.turnTickService = turnTickService;
        this.gameData = gameData;
        this.randomSource = randomSource;
        this.deckTemplates = deckTemplates;
        this.encounters = encounters;
        this.audit = audit;
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

    /** Full M6-A creation: race/path/class/specialization, stat-array permutation +
     *  bonus allocation, 3 skill proficiencies, caster spell allotment, derived pools. */
    public CharacterCreatedResponse createCharacter(CreateCharacterRequest req) {
        requireText(req.roomName(), "roomName");
        requireText(req.email(), "email");
        requireText(req.name(), "name");
        requireText(req.raceId(), "raceId");
        requireText(req.pathId(), "pathId");
        requireText(req.classId(), "classId");
        if (!req.email().contains("@")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email must be a valid address");
        }
        validatePathAndClass(req.pathId(), req.classId());
        var race = findRace(req.raceId());
        if (race == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown race '" + req.raceId() + "'");
        }

        // Q35: specialization is chosen at creation — required when the class has any
        // in the data (all 33 do today); matched by slug(name).
        var classSpecs = gameData.getSpecializations().path(req.classId());
        com.fasterxml.jackson.databind.JsonNode spec = null;
        if (classSpecs.isArray() && !classSpecs.isEmpty()) {
            requireText(req.specializationId(), "specializationId");
            spec = gameData.findSpecialization(req.classId(), req.specializationId());
            if (spec == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "specialization '" + req.specializationId() + "' is not valid for class '"
                                + req.classId() + "'");
            }
        }

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

        // Stats: exactly a permutation of the statArray, then +5 bonus (max 2/stat, N17).
        var baseStats = statsFrom(requireStatArrayPermutation(req.stats()));
        var bonus = validateBonusAllocation(req.bonusAllocation(),
                gameData.getCharacterCreation().path("bonusPoints").asInt(5),
                gameData.getCharacterCreation().path("maxBonusPerStat").asInt(2));
        for (var e : bonus.entrySet()) {
            baseStats.set(e.getKey(), baseStats.get(e.getKey()) + e.getValue());
        }

        // Exactly 3 distinct skill proficiencies (Q36: no class/race extras).
        int requiredSkills = gameData.getCharacterCreation().path("defaultSkillProficiencies").asInt(3);
        var skills = req.skillProficiencies() != null ? req.skillProficiencies() : List.<String>of();
        if (skills.stream().distinct().count() != requiredSkills || skills.size() != requiredSkills) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "exactly " + requiredSkills + " distinct skill proficiencies required");
        }
        var validSkills = validSkillIds();
        for (var s : skills) {
            if (!validSkills.contains(s)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown skill: " + s);
            }
        }

        var c = new GameCharacter(id);
        c.setRoomName(req.roomName().trim());
        c.setEmail(req.email().trim().toLowerCase());
        c.setName(req.name().trim());
        c.setRaceId(req.raceId());
        c.setPathId(req.pathId());
        c.setClassId(req.classId());
        if (spec != null) {
            c.setSpecializationId(GameDataProvider.slug(spec.path("name").asText()));
        }
        c.setLevel(level);
        c.setStats(baseStats);

        // Spells: casters get exactly the progression allotment; non-casters none.
        validateAndSetKnownSpells(c, req.knownSpells(), level);

        // races.json → movementSpeed (ft per AP; halves stored as-is pending N13).
        c.setSpeed(race.path("movementSpeed").asInt(30));

        // races.json → initiativeBonus (structured field; human +5). Feeds the
        // encounter initiative roll: d20 + DEX mod + bonusInitiative.
        c.setBonusInitiative(race.path("initiativeBonus").asInt(0));

        // N16: saving throws belong to the CLASS; the values still live on the path in
        // classes.json pending the data migration (OPEN-QUESTIONS work item A).
        for (var path : gameData.getClasses()) {
            if (!path.path("id").asText().equals(req.pathId())) continue;
            for (var st : path.path("savingThrowProficiencies")) {
                c.getSavingThrowProficiencies().add(parseAbility(st.asText()));
            }
        }

        var ap = gameData.getCharacterCreation().path("defaultAP");
        c.setAp(new ActionPoints(ap.path("starting").asInt(6),
                ap.path("recovery").asInt(6), ap.path("maximum").asInt(10)));

        int maxHp = statEngine.computeMaxHP(c);
        int maxMana = statEngine.computeMaxMana(c);
        c.setHp(new HitPoints(maxHp, maxHp, 0));
        c.setMana(new ManaPool(maxMana, maxMana));

        // Class resource per M3 Part A: max derived; builders start at 0, allotments full (Q19).
        String resourceType = statEngine.getClassResourceType(c);
        if (resourceType != null) {
            Integer derived = statEngine.computeClassResourceMax(c);
            int max = derived == null ? 0
                    : derived == StatDerivationEngine.UNBOUNDED_RESOURCE ? 0 : derived;
            int current = statEngine.isBuilderResource(resourceType) ? 0 : max;
            c.setResource(new ClassResource(resourceType, current, max));
        }

        // Q38 (re-cut 2026-07-06: ONE generic gold currency): starting money = 10 × a
        // level-1 quest reward = 100. Higher-level starting money awaits the Game Owner.
        c.setGold(100);

        c.getProficiencies().addAll(skills);

        // The specialization's startingTalent is auto-granted ("unknown" = PDF gap).
        if (spec != null) {
            String startingTalent = spec.path("startingTalent").asText(null);
            if (startingTalent != null && !"unknown".equals(startingTalent)) {
                c.getTalents().add(startingTalent);
            }
        }

        grantStartingEquipment(c, req);

        repo.save(c);
        return new CharacterCreatedResponse(id, buildCombatSnapshot(c));
    }

    /**
     * Starting equipment (Game Owner 2026-08-12): one weapon, one shield and one
     * body armor — free, granted at item level 1 and equipped. Each pick is
     * optional; a two-handed weapon and the shield exclude each other (the same
     * rule EquipmentService enforces on equip). Non-proficient picks are allowed —
     * the usual penalties apply at the table.
     */
    private void grantStartingEquipment(GameCharacter c, CreateCharacterRequest req) {
        boolean shield = Boolean.TRUE.equals(req.startingShield());

        if (req.startingWeaponId() != null && !req.startingWeaponId().isBlank()) {
            var weapon = gameData.findItem(req.startingWeaponId());
            if (weapon == null || weapon.kind() != com.steelmight.charactersheet.gamedata.ItemKind.WEAPON) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "startingWeaponId must be a weapon: " + req.startingWeaponId());
            }
            if (shield && EquipmentService.hasProperty(weapon, "two-handed")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "a two-handed weapon and a shield exclude each other — pick one");
            }
            c.addItem(new InventoryEntry(weapon.id(), 1, 1, true));
        }

        if (req.startingArmorId() != null && !req.startingArmorId().isBlank()) {
            var armor = gameData.findItem(req.startingArmorId());
            if (armor == null || armor.kind() != com.steelmight.charactersheet.gamedata.ItemKind.ARMOR
                    || EquipmentService.isShield(armor)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "startingArmorId must be a body armor: " + req.startingArmorId());
            }
            c.addItem(new InventoryEntry(armor.id(), 1, 1, true));
        }

        if (shield) {
            var sh = gameData.findItem("shield");
            if (sh == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "no shield item exists in the data");
            }
            c.addItem(new InventoryEntry(sh.id(), 1, 1, true));
        }
    }

    private com.fasterxml.jackson.databind.JsonNode findRace(String raceId) {
        var list = gameData.getRaces() != null ? gameData.getRaces().path("races") : null;
        if (list == null || !list.isArray()) return null;
        for (var race : list) {
            if (race.path("id").asText().equals(raceId)) return race;
        }
        return null;
    }

    /** M6-A step 3: the 7 base stats must be a permutation of the statArray. */
    private Map<AbilityScore, Integer> requireStatArrayPermutation(Map<AbilityScore, Integer> stats) {
        if (stats == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "stats are required");
        }
        var expected = new java.util.ArrayList<Integer>();
        for (var v : gameData.getCharacterCreation().path("statArray")) expected.add(v.asInt());
        var provided = new java.util.ArrayList<Integer>();
        for (var ability : AbilityScore.values()) {
            Integer v = stats.get(ability);
            if (v == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "missing stat: " + ability);
            }
            provided.add(v);
        }
        var sortedExpected = expected.stream().sorted().toList();
        var sortedProvided = provided.stream().sorted().toList();
        if (!sortedExpected.equals(sortedProvided)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "base stats must be a permutation of the standard array " + expected);
        }
        return stats;
    }

    /** M6-A step 4 / M6-B stat increases: values >= 1, each <= maxPerStat, sum == points. */
    Map<AbilityScore, Integer> validateBonusAllocation(Map<AbilityScore, Integer> allocation,
                                                       int points, int maxPerStat) {
        if (allocation == null || allocation.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "bonusAllocation is required (+" + points + " across any stats, max "
                            + maxPerStat + " each)");
        }
        int sum = 0;
        for (var e : allocation.entrySet()) {
            if (e.getValue() == null || e.getValue() < 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "bonus for " + e.getKey() + " must be at least 1");
            }
            if (e.getValue() > maxPerStat) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "bonus for " + e.getKey() + " exceeds the per-stat maximum of " + maxPerStat);
            }
            sum += e.getValue();
        }
        if (sum != points) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "bonus points must sum to " + points + " (got " + sum + ")");
        }
        return allocation;
    }

    /** M6-A step 6: casters know exactly the progression allotment for their level. */
    private void validateAndSetKnownSpells(GameCharacter c, List<String> spellIds, int level) {
        var casterType = statEngine.getCasterType(c);
        var ids = spellIds != null ? spellIds : List.<String>of();
        if (casterType == CasterType.NONE) {
            if (!ids.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "non-casters cannot know spells");
            }
            return;
        }
        var progression = gameData.getSpellcasting().path("spellsKnownProgression")
                .path(casterType.name().toLowerCase()).path("cumulative");
        int required = progression.isArray() && level <= progression.size()
                ? progression.get(level - 1).asInt(1) : 1;
        if (ids.size() != required || ids.stream().distinct().count() != required) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    casterType.name().toLowerCase() + " casters start with exactly "
                            + required + " distinct known spell(s) at level " + level);
        }
        int maxLevel = spellLevelAccess(casterType, level);
        for (var spellId : ids) {
            var spell = gameData.getSpell(spellId);
            if (spell == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown spell: " + spellId);
            }
            if (!spell.classId().equals(c.getClassId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "spell '" + spellId + "' belongs to class '" + spell.classId() + "'");
            }
            if (spell.level() > maxLevel) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "no access to level-" + spell.level() + " spells at character level " + level);
            }
        }
        c.getKnownSpells().addAll(ids);
    }

    private static AbilityScore parseAbility(String key) {
        return switch (key.toLowerCase()) {
            case "str" -> AbilityScore.STR;
            case "dex" -> AbilityScore.DEX;
            case "con", "const" -> AbilityScore.CON;
            case "int" -> AbilityScore.INT;
            case "wis" -> AbilityScore.WIS;
            case "will" -> AbilityScore.WILL;
            case "cha" -> AbilityScore.CHA;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "unknown ability: " + key);
        };
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

    /** Public so ShopService's actions can return the same snapshot shape (M5). */
    public InventorySnapshot buildInventorySnapshot(GameCharacter c) {
        var items = c.getInventory().stream()
                .map(e -> new InventorySnapshot.ItemView(
                        e.getItemId(), e.getQuantity(), e.getUpgradeTier(), e.isEquipped(),
                        e.isSilvered(), statEngine.itemSpace(c, e.getItemId()),
                        e.getChargesRemaining(), e.getStoredSpellId()))
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
            if (!gameData.isKnownItem(in.itemId()) && c.customItem(in.itemId()) == null) {
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
            // Scrolls hold a specific spell (2026-07-07) — the DM grant path validates it too.
            if (in.spellId() != null && !in.spellId().isBlank()) {
                var item = gameData.findItem(in.itemId());
                if (item == null || item.kind() != com.steelmight.charactersheet.gamedata.ItemKind.SCROLL) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "spellId only applies to scrolls (" + in.itemId() + ")");
                }
                ShopService.validateScrollSpell(gameData, item, in.spellId());
            }
            totalSpace += statEngine.itemSpace(c, in.itemId()) * qty;
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
            int before = c.getGold();
            if (req.gold() != before) {
                audit.log(c, "inventory-edit", "Gold set to " + req.gold() + " (was " + before + ")");
            }
            c.setGold(req.gold());
        }

        c.getInventory().clear();
        for (var in : inputs) {
            var entry = new InventoryEntry(
                    in.itemId(),
                    in.quantity() != null ? in.quantity() : 1,
                    in.upgradeTier() != null ? in.upgradeTier() : 0,
                    in.equipped() != null && in.equipped());
            if (in.spellId() != null && !in.spellId().isBlank()) {
                entry.setStoredSpellId(in.spellId());
            }
            c.addItem(entry);
        }

        repo.save(c);
        return buildInventorySnapshot(c);
    }

    public SpellbookSnapshot getSpellbookSnapshot(String playerId) {
        var c = getCharacter(playerId);
        boolean concentrating = c.getActiveEffects().stream()
                .anyMatch(e -> "concentrating".equals(e.getEffectId()));
        // Copies initialize the lazy collections inside this transaction (no OSIV reliance).
        return new SpellbookSnapshot(
                List.copyOf(c.getKnownSpells()), List.copyOf(c.getPreparedSpells()),
                c.getMana().getCurrent(), c.getMana().getMax(),
                concentrating,
                statEngine.getSpellcastingAttribute(c),
                statEngine.computeSpellSaveDC(c),
                statEngine.computeSpellAttackBonus(c)
        );
    }

    // --- Actions ---

    // The four plain combat actions are combatant-agnostic (ADR-001): validation, event
    // building and the pipeline call live in CombatActionService, shared with monsters.
    // This class only loads, saves, audits and snapshots the player.

    public ActionResponse<CombatSnapshot> damage(String playerId, DamageRequest req) {
        var c = getCharacter(playerId);
        return finish(c, "damage", combatActions.damage(c, req));
    }

    public ActionResponse<CombatSnapshot> heal(String playerId, HealRequest req) {
        var c = getCharacter(playerId);
        return finish(c, "heal", combatActions.heal(c, req));
    }

    public ActionResponse<CombatSnapshot> applyEffect(String playerId, ApplyEffectRequest req) {
        var c = getCharacter(playerId);
        return finish(c, "apply-effect", combatActions.applyEffect(c, req));
    }

    public ActionResponse<CombatSnapshot> removeEffect(String playerId, String effectId) {
        var c = getCharacter(playerId);
        return finish(c, "remove-effect", combatActions.removeEffect(c, effectId));
    }

    private ActionResponse<CombatSnapshot> finish(GameCharacter c, String action, CombatActionService.Outcome out) {
        repo.save(c);
        audit.log(c, action, out.auditSummary());
        return new ActionResponse<>(out.resolution(), buildCombatSnapshot(c));
    }

    public ActionResponse<CombatSnapshot> spendResource(String playerId, SpendResourceRequest req) {
        var c = getCharacter(playerId);
        if (req.amount() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be positive");
        }
        var result = new ResolutionResult();

        // Validated spending (M0-D): insufficient → 400, no partial spend, never clamp.
        switch (req.resource()) {
            case "ap" -> {
                int before = c.getAp().getCurrent();
                requireSufficient("ap", before, req.amount());
                c.getAp().setCurrent(before - req.amount());
                result.addStep("spend-ap", "Spent " + req.amount() + " AP", before, before - req.amount());
            }
            case "mana" -> {
                int before = c.getMana().getCurrent();
                requireSufficient("mana", before, req.amount());
                c.getMana().setCurrent(before - req.amount());
                result.addStep("spend-mana", "Spent " + req.amount() + " mana", before, before - req.amount());
            }
            default -> {
                if (c.getResource() != null && req.resource().equals(c.getResource().getType())) {
                    int before = c.getResource().getCurrent();
                    requireSufficient(req.resource(), before, req.amount());
                    c.getResource().setCurrent(before - req.amount());
                    result.addStep("spend-" + req.resource(),
                            "Spent " + req.amount() + " " + req.resource(), before, before - req.amount());
                } else {
                    spendFromPool(c, req.resource(), req.amount(), result);
                }
            }
        }

        repo.save(c);
        audit.log(c, "spend-resource", "Spent " + req.amount() + " " + req.resource());
        return new ActionResponse<>(result, buildCombatSnapshot(c));
    }

    /**
     * Spend from a sub-resource pool (Story 1.2). Pools whose definition declares a
     * `min` (fury) may go NEGATIVE — the cost is still paid and the disaster rule is
     * adjudicated at the table (B12); all other pools validate sufficiency.
     */
    private void spendFromPool(GameCharacter c, String poolId, int amount, ResolutionResult result) {
        spendFromPool(c, poolId, amount, result, "");
    }

    private void spendFromPool(GameCharacter c, String poolId, int amount, ResolutionResult result,
                               String noteSuffix) {
        ensurePools(c);
        var pool = c.findPool(poolId);
        if (pool == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown resource: " + poolId);
        }
        var def = gameData.getPoolsForClass(c.getClassId()).stream()
                .filter(d -> d.id().equals(poolId)).findFirst().orElse(null);
        boolean mayGoNegative = def != null && def.min() != null;
        int before = pool.getCurrent();
        if (!mayGoNegative) {
            requireSufficient(poolId, before, amount);
        }
        pool.setCurrent(before - amount);
        result.addStep("spend-" + poolId,
                "Spent " + amount + " " + poolId + noteSuffix
                        + (pool.getCurrent() < 0 ? " — pool is NEGATIVE (disaster rule, DM adjudicates)" : ""),
                before, pool.getCurrent());
    }

    /**
     * Materialize missing pools from the class's definitions (Story 1.2): numeric pools
     * at/above their unlock level start at `initial`. Formula pools (shapeshift-hp)
     * wait for their rulings (S1/S7).
     */
    private void ensurePools(GameCharacter c) {
        if (c.getClassId() == null) return;
        for (var def : gameData.getPoolsForClass(c.getClassId())) {
            if (def.initial() == null || def.maxFormula() != null) continue;
            if (def.unlockLevel() != null && c.getLevel() < def.unlockLevel()) continue;
            if (c.findPool(def.id()) == null) {
                c.getPools().add(new CharacterPool(def.id(), def.initial(), def.max()));
            }
        }
    }

    private static void requireSufficient(String resource, int have, int need) {
        if (have < need) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Insufficient " + resource + ": have " + have + ", need " + need);
        }
    }

    // ── Class abilities (Epic 1: Stories 1.3 + 1.4) ──

    public AbilitiesSnapshot getAbilitiesSnapshot(String playerId) {
        return buildAbilitiesSnapshot(getCharacter(playerId));
    }

    /** Effective known set: group-null abilities are class-granted at their level; picks add the rest. */
    private AbilitiesSnapshot buildAbilitiesSnapshot(GameCharacter c) {
        var known = new ArrayList<String>();
        for (var def : gameData.getAbilitiesForClass(c.getClassId())) {
            if (def.group() == null && def.minLevel() <= c.getLevel()) {
                known.add(def.id());
            }
        }
        for (var picked : c.getKnownAbilities()) {
            if (!known.contains(picked)) known.add(picked);
        }
        var uses = new ArrayList<AbilitiesSnapshot.AbilityUseView>();
        for (var id : known) {
            var def = gameData.getAbility(id);
            if (def == null || (def.usesPerRest() == null && def.usesPerTurn() == null)) continue;
            // Read the counter without abilityUse() — that helper creates a row, and this is a read path.
            var use = c.getAbilityUses().stream()
                    .filter(u -> u.getAbilityId().equals(id)).findFirst().orElse(null);
            Integer perRestMax = maxUsesPerRest(c, def);
            Integer perRestRemaining = perRestMax != null
                    ? Math.max(0, perRestMax - (use != null ? use.getUsedThisRest() : 0)) : null;
            Integer perTurnMax = def.usesPerTurn();
            Integer perTurnRemaining = perTurnMax != null
                    ? Math.max(0, perTurnMax - (use != null ? use.getUsedThisTurn() : 0)) : null;
            uses.add(new AbilitiesSnapshot.AbilityUseView(id, perRestRemaining, perRestMax, perTurnRemaining, perTurnMax));
        }
        var custom = c.getCustomAbilities().stream()
                .map(a -> new AbilitiesSnapshot.CustomAbilityView(a.getName(), a.getText(), a.getApCost()))
                .toList();
        return new AbilitiesSnapshot(c.getClassId(), known, List.copyOf(c.getKnownAbilities()), uses, custom);
    }

    /**
     * Free-text abilities (2026-07-20 Game Owner ruling): until every ability question
     * is resolved, players write the unresolved ones here verbatim. Replace-list
     * semantics like the picker; the table adjudicates what they do.
     */
    /** This character's custom weapon/armor definitions (demo feedback #19). */
    public List<CustomItemView> getCustomItems(String playerId) {
        return getCharacter(playerId).getCustomItems().stream().map(CharacterService::toView).toList();
    }

    /**
     * Replace the custom item list wholesale, mirroring the custom-abilities editor.
     *
     * Ids are server-assigned and STABLE: an item keeps its id across edits so inventory
     * rows that reference it don't dangle. Deleting a definition that is still carried is
     * rejected rather than silently orphaning the inventory row — a sheet holding an item
     * the server can't resolve is exactly the state that breaks equip and attack.
     */
    public List<CustomItemView> updateCustomItems(String playerId, UpdateCustomItemsRequest req) {
        var c = getCharacter(playerId);
        var entries = req.items() != null ? req.items() : List.<CustomItemView>of();
        if (entries.size() > 40) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "too many custom items (max 40)");
        }

        var usedIds = new java.util.HashSet<String>();
        var built = new java.util.ArrayList<CustomItem>();
        for (var view : entries) built.add(buildCustomItem(c, view, usedIds));

        var keptIds = built.stream().map(CustomItem::getItemId).collect(java.util.stream.Collectors.toSet());
        for (var existing : c.getCustomItems()) {
            if (keptIds.contains(existing.getItemId())) continue;
            boolean carried = c.getInventory().stream()
                    .anyMatch(e -> existing.getItemId().equals(e.getItemId()));
            if (carried) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "\"" + existing.getName() + "\" is still in the inventory — drop it before deleting the definition");
            }
        }

        c.getCustomItems().clear();
        c.getCustomItems().addAll(built);
        repo.save(c);
        audit.log(c, "custom-items", "Custom gear updated (" + built.size() + " defined)");
        return built.stream().map(CharacterService::toView).toList();
    }

    private CustomItem buildCustomItem(GameCharacter c, CustomItemView view, java.util.Set<String> usedIds) {
        if (view.name() == null || view.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "every custom item needs a name");
        }
        String name = view.name().trim();
        if (name.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "custom item name too long (max 100)");
        }
        String kind = view.kind() != null ? view.kind().toUpperCase() : "";
        if (!kind.equals("WEAPON") && !kind.equals("ARMOR")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "custom item kind must be WEAPON or ARMOR: " + name);
        }

        String id = view.id() != null && !view.id().isBlank()
                ? view.id().trim()
                : nextCustomItemId(c, name, usedIds);
        if (!usedIds.add(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "duplicate custom item id: " + id);
        }

        var item = new CustomItem(id, name, kind);
        item.setInventorySpace(view.inventorySpace() != null ? view.inventorySpace() : 1.0);
        item.setProperties(view.properties());
        item.setProficient(view.proficient());

        if (kind.equals("WEAPON")) {
            item.setDamageDice(view.damageDice());
            item.setDamageFlat(view.damageFlat());
            item.setDamageType(view.damageType());
            item.setDamageScaling(view.damageScaling());
            item.setWeaponStat(view.weaponStat());
            item.setApCost(view.apCost());
            if (view.apCost() != null && (view.apCost() < 0 || view.apCost() > 30)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "AP cost out of range (0-30): " + name);
            }
            if (item.getDamageType() != null && !item.getDamageType().isBlank()) {
                try {
                    DamageType.valueOf(item.getDamageType().toUpperCase());
                } catch (IllegalArgumentException e) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "unknown damage type \"" + item.getDamageType() + "\" on " + name);
                }
            }
        } else {
            String type = view.armorType() != null ? view.armorType().toLowerCase() : "light";
            if (!List.of("light", "medium", "heavy", "shield").contains(type)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "armor type must be light/medium/heavy/shield: " + name);
            }
            item.setArmorType(type);
            item.setAcBase(view.acBase());
            item.setAcDexMod(view.acDexMod());
            item.setPa(view.pa());
            item.setMa(view.ma());
            item.setPaScaling(view.paScaling());
            item.setMaScaling(view.maScaling());
        }
        return item;
    }

    /** custom-&lt;slug&gt;, suffixed until it collides with neither the catalog nor a sibling. */
    private String nextCustomItemId(GameCharacter c, String name, java.util.Set<String> usedIds) {
        String slug = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (slug.isBlank()) slug = "item";
        String base = CustomItem.ID_PREFIX + slug;
        String id = base;
        int n = 2;
        while (gameData.isKnownItem(id) || usedIds.contains(id)) {
            id = base + "-" + n++;
        }
        return id;
    }

    private static CustomItemView toView(CustomItem i) {
        return new CustomItemView(i.getItemId(), i.getName(), i.getKind(),
                i.getInventorySpace(), i.getProperties(), i.isProficient(),
                i.getDamageDice(), i.getDamageFlat(), i.getDamageType(), i.getDamageScaling(),
                i.getWeaponStat(), i.getApCost(),
                i.getArmorType(), i.getAcBase(), i.isAcDexMod(),
                i.getPa(), i.getMa(), i.getPaScaling(), i.getMaScaling());
    }

    public AbilitiesSnapshot updateCustomAbilities(String playerId, UpdateCustomAbilitiesRequest req) {
        var c = getCharacter(playerId);
        var entries = req.abilities() != null ? req.abilities() : List.<AbilitiesSnapshot.CustomAbilityView>of();
        if (entries.size() > 40) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "too many custom abilities (max 40)");
        }
        var seenNames = new java.util.HashSet<String>();
        for (var a : entries) {
            if (a.name() == null || a.name().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "every custom ability needs a name");
            }
            if (a.name().length() > 100) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "custom ability name too long (max 100)");
            }
            if (a.text() != null && a.text().length() > 4000) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "custom ability text too long (max 4000): " + a.name());
            }
            // use-by-name would silently pick the first duplicate — reject up front
            if (!seenNames.add(a.name().trim().toLowerCase())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "duplicate custom ability name: " + a.name().trim());
            }
            if (a.apCost() != null && (a.apCost() < 0 || a.apCost() > 30)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "AP cost out of range (0-30): " + a.name().trim());
            }
        }
        c.getCustomAbilities().clear();
        for (var a : entries) {
            // 0 AP means "explicitly free" — store as no cost so Use skips the spend step
            var apCost = a.apCost() != null && a.apCost() > 0 ? a.apCost() : null;
            c.getCustomAbilities().add(new CustomAbility(a.name().trim(), a.text() != null ? a.text() : "", apCost));
        }
        repo.save(c);
        audit.log(c, "custom-abilities", "Custom ability list updated (" + entries.size() + " entries)");
        return buildAbilitiesSnapshot(c);
    }

    /**
     * Print a free-text ability into the resolution log. When the player set an AP cost
     * on it, that cost is validated and spent (M0-D semantics: insufficient → 400, no
     * partial spend); everything else about the ability is still adjudicated at the table.
     */
    public ActionResponse<CombatSnapshot> useCustomAbility(String playerId, UseCustomAbilityRequest req) {
        var c = getCharacter(playerId);
        var name = req.name() != null ? req.name().trim() : "";
        var ability = c.getCustomAbilities().stream()
                .filter(a -> a.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "no custom ability named: " + name));
        var result = new ResolutionResult();
        int apCost = ability.getApCost() != null ? ability.getApCost() : 0;
        if (apCost > 0) {
            int before = c.getAp().getCurrent();
            requireSufficient("ap", before, apCost);
            c.getAp().setCurrent(before - apCost);
            result.addStep("spend-ap", "Spent " + apCost + " AP on " + ability.getName(),
                    before, before - apCost);
            repo.save(c);
        }
        result.addStep("use-custom-ability", ability.getName()
                + (ability.getText().isBlank() ? "" : " — " + ability.getText())
                + (apCost > 0
                        ? " (free-text — AP spent; the table adjudicates other costs and outcomes)"
                        : " (free-text — the table adjudicates costs and outcomes)"), 0, 0);
        audit.log(c, "use-ability", "Used " + ability.getName() + " (custom"
                + (apCost > 0 ? ", " + apCost + " AP" : "") + ")");
        return new ActionResponse<>(result, buildCombatSnapshot(c));
    }

    /** Free-form picker (Story 1.3 ruling): class + level checks only, no choice-group enforcement. */
    public AbilitiesSnapshot updateKnownAbilities(String playerId, UpdateAbilitiesRequest req) {
        var c = getCharacter(playerId);
        var ids = req.abilityIds() != null
                ? req.abilityIds().stream().distinct().toList()
                : List.<String>of();
        for (var id : ids) {
            var def = gameData.getAbility(id);
            if (def == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown ability: " + id);
            }
            if (!def.classId().equals(c.getClassId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        def.name() + " belongs to " + def.classId() + ", not " + c.getClassId());
            }
            if (def.minLevel() > c.getLevel()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        def.name() + " requires level " + def.minLevel());
            }
        }
        c.getKnownAbilities().clear();
        c.getKnownAbilities().addAll(ids);
        repo.save(c);
        return buildAbilitiesSnapshot(c);
    }

    /**
     * Use a class ability (Story 1.4): validate (known → prevented → budgets → costs,
     * all-or-nothing) → spend → resolve. auto: heal/self-effects/grants applied by the
     * server; manual: the rules text lands in the log. Target effects are COMPUTED into
     * the payload but applied at the table until the encounter model.
     */
    public ActionResponse<CombatSnapshot> useAbility(String playerId, UseAbilityRequest req) {
        var c = getCharacter(playerId);
        var ability = gameData.getAbility(req.abilityId());
        if (ability == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown ability: " + req.abilityId());
        }
        if ("passive".equals(ability.kind())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    ability.name() + " is a passive — it is always on");
        }
        if (!ability.classId().equals(c.getClassId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    ability.name() + " belongs to " + ability.classId());
        }
        if (ability.minLevel() > c.getLevel()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    ability.name() + " requires level " + ability.minLevel());
        }
        boolean implicitlyKnown = ability.group() == null;
        if (!implicitlyKnown && !c.getKnownAbilities().contains(ability.id())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    ability.name() + " is not known — pick it in the Abilities tab");
        }

        // Prevented (stunned, frozen … — dormancy/composite-aware, same as cast step 5).
        int threshold = statEngine.computeStackThreshold(c);
        var prevented = ActiveMechanics.collect(c, gameData, threshold, MechanicType.PREVENT_ACTION).stream()
                .filter(h -> h.mechanic().action() == PreventableAction.ALL)
                .findFirst();
        if (prevented.isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "cannot use abilities while " + prevented.get().def().name().toLowerCase());
        }

        // Pending cost rulings (MK13/MK17) — refuse rather than guess.
        if (Boolean.TRUE.equals(ability.costEqualsHealing()) || ability.costPercentOfMaxResource() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    ability.name() + "'s cost model awaits a Game Owner ruling — resolve at the table");
        }

        // Use budgets (usesPerTurn resets on turn start; usesPerRest per the tier ruling).
        var use = (ability.usesPerTurn() != null || ability.usesPerRest() != null)
                ? c.abilityUse(ability.id()) : null;
        if (use != null && ability.usesPerTurn() != null && use.getUsedThisTurn() >= ability.usesPerTurn()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    ability.name() + " was already used this turn (max " + ability.usesPerTurn() + "/turn)");
        }
        Integer maxPerRest = maxUsesPerRest(c, ability);
        if (use != null && maxPerRest != null && use.getUsedThisRest() >= maxPerRest) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "no " + ability.name() + " uses left until a rest (" + maxPerRest + " per rest)");
        }

        var result = new ResolutionResult();

        // AP cost ("all" = Onslaught: requires base recovery AP available, consumes everything).
        int apCost = 0;
        boolean apAll = false;
        if (ability.apCost() != null) {
            if (ability.apCost().isSpecial()) {
                if ("all".equalsIgnoreCase(ability.apCost().special())) {
                    apAll = true;
                } else {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "AP cost '" + ability.apCost().special() + "' — DM adjudicates");
                }
            } else if (ability.apCost().flat() != null) {
                apCost = ability.apCost().flat();
            }
        }
        if (apAll) {
            requireSufficient("ap", c.getAp().getCurrent(), c.getAp().getRecovery());
            apCost = c.getAp().getCurrent();
        } else if (apCost > 0) {
            requireSufficient("ap", c.getAp().getCurrent(), apCost);
        }

        // Resource costs: roll dice costs first, validate EVERYTHING, then spend (all-or-nothing).
        record CostLine(String resource, int total, String rollNote) {}
        var costLines = new ArrayList<CostLine>();
        for (var cost : ability.costs() != null ? ability.costs() : List.<AbilityDefinition.AbilityCost>of()) {
            int total = cost.amount() != null ? cost.amount() : 0;
            String rollNote = "";
            if (cost.amountDice() != null) {
                int roll = 0;
                for (int i = 0; i < cost.amountDice().count(); i++) {
                    roll += randomSource.nextInt(cost.amountDice().sides()) + 1;
                }
                total += roll;
                rollNote = " (" + cost.amountDice() + " rolled " + roll + ")";
            }
            costLines.add(new CostLine(cost.resource(), total, rollNote));
        }
        ensurePools(c);
        for (var line : costLines) {
            if (c.getResource() != null && line.resource().equals(c.getResource().getType())) {
                requireSufficient(line.resource(), c.getResource().getCurrent(), line.total());
            } else if ("mana".equals(line.resource())) {
                requireSufficient("mana", c.getMana().getCurrent(), line.total());
            } else {
                var pool = c.findPool(line.resource());
                if (pool == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            ability.name() + " needs the " + line.resource() + " pool, which this character does not have");
                }
                var def = gameData.getPoolsForClass(c.getClassId()).stream()
                        .filter(d -> d.id().equals(line.resource())).findFirst().orElse(null);
                if (def == null || def.min() == null) { // min-bearing pools (fury) may go negative
                    requireSufficient(line.resource(), pool.getCurrent(), line.total());
                }
            }
        }

        // Story 2.3 last mile: the structured target effect can land on a named combatant.
        // Resolved (and taunt-checked when the effect is harmful) BEFORE spending.
        Combatant abilityTarget = null;
        if (req.targetCombatantId() != null && !req.targetCombatantId().isBlank()) {
            if (ability.targetEffect() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        ability.name() + " has no target effect to apply — use it without a target");
            }
            abilityTarget = combatants.require(c.getRoomName(), req.targetCombatantId());
            var def = gameData.getEffect(ability.targetEffect().effectId());
            if (abilityTarget != c && def != null && def.isNegative()) {
                combatActions.enforceTaunt(c, abilityTarget);
            }
        }

        // Spend.
        if (apCost > 0) {
            int before = c.getAp().getCurrent();
            c.getAp().setCurrent(before - apCost);
            result.addStep("spend-ap", "Spent " + apCost + " AP" + (apAll ? " (all)" : ""), before, before - apCost);
        }
        for (var line : costLines) {
            if (c.getResource() != null && line.resource().equals(c.getResource().getType())) {
                int before = c.getResource().getCurrent();
                c.getResource().setCurrent(before - line.total());
                result.addStep("spend-" + line.resource(),
                        "Spent " + line.total() + " " + line.resource() + line.rollNote(), before, before - line.total());
            } else if ("mana".equals(line.resource())) {
                int before = c.getMana().getCurrent();
                c.getMana().setCurrent(before - line.total());
                result.addStep("spend-mana", "Spent " + line.total() + " mana" + line.rollNote(), before, before - line.total());
            } else {
                spendFromPool(c, line.resource(), line.total(), result, line.rollNote());
            }
        }
        if (use != null) {
            use.setUsedThisTurn(use.getUsedThisTurn() + 1);
            use.setUsedThisRest(use.getUsedThisRest() + 1);
            if (maxPerRest != null) {
                result.addStep("ability-uses", ability.name() + " uses left until rest",
                        maxPerRest - use.getUsedThisRest() + 1, maxPerRest - use.getUsedThisRest());
            }
        }

        // Resolve.
        if ("auto".equals(ability.resolution())) {
            resolveAutoAbility(c, ability, result);
        } else {
            result.addStep("use-ability", ability.name()
                    + ("reaction".equals(ability.kind()) ? " (reaction)" : "")
                    + ("attack-enhancer".equals(ability.kind()) ? " (declared on an attack)" : "")
                    + " — " + ability.description(), 0, 0);
        }
        // A structured self-effect is real for BOTH resolutions — a manual ability keeps
        // its narrative text, but its mechanical slice (rage's resistance) still applies.
        applySelfEffect(c, ability, result);
        emitTargetEffect(c, ability, abilityTarget, result);
        if (ability.nextTurnApPenalty() != null) {
            result.addStep("ability-note", "Start your next turn with " + ability.nextTurnApPenalty()
                    + " fewer AP (apply manually at turn start — A6 wiring pending)", 0, 0);
        }

        repo.save(c);
        audit.log(c, "use-ability", "Used " + ability.name());
        return new ActionResponse<>(result, buildCombatSnapshot(c));
    }

    private void resolveAutoAbility(GameCharacter c, AbilityDefinition ability, ResolutionResult result) {
        if (ability.grants() != null) {
            for (var grant : ability.grants()) {
                gainAny(c, grant.resource(), grant.amount(), result);
            }
        }
        if (ability.riders() != null) {
            for (var rider : ability.riders()) {
                if (rider.minLevel() != null && c.getLevel() < rider.minLevel()) continue;
                for (var gain : rider.gains() != null ? rider.gains() : List.<AbilityDefinition.ResourceAmount>of()) {
                    gainAny(c, gain.resource(), gain.amount(), result);
                }
            }
        }
        if (ability.healing() != null) {
            int total = rollAbilityHealing(c, ability, result);
            var healResult = healingPipeline.resolve(new HealEvent(total), c);
            mergeSteps(result, "ability", healResult);
        }
    }

    /**
     * hasValue effects need a magnitude: the data's int stacks doubles as it
     * (reduced-weapon-ap-cost 1 = −1 AP); with neither, the amount depends on the
     * spend and the DM applies it (focus-to-temp-hp) — applying valueless would 400.
     */
    private void applySelfEffect(GameCharacter c, AbilityDefinition ability, ResolutionResult result) {
        var se = ability.selfEffect();
        if (se == null) return;
        var def = gameData.getEffect(se.effectId());
        Integer value = null;
        if (def != null && def.hasValue()) {
            if (se.stacks() == null) {
                result.addStep("ability-self-effect", ability.name() + " grants " + se.effectId()
                        + " — magnitude depends on the spend (DM applies)", 0, 0);
                return;
            }
            value = se.stacks();
        }
        var applied = effectEngine.apply(c, new EffectApplication(
                se.effectId(), "ability:" + ability.id(), se.stacks(), value,
                se.durationRounds(), true, false, false, null));
        mergeSteps(result, "ability", applied);
    }

    /** Dice count: fixed | per level | base + 1 per level beyond the threshold level. */
    private int rollAbilityHealing(GameCharacter c, AbilityDefinition ability, ResolutionResult result) {
        var formula = ability.healing();
        int total = 0;
        var rolls = new ArrayList<Integer>();
        if (formula.dice() != null) {
            var dice = formula.dice();
            int count = 0;
            if (dice.count() != null) count = dice.count();
            else if (dice.countPerLevel() != null) count = dice.countPerLevel() * c.getLevel();
            else if (dice.countBase() != null) {
                count = dice.countBase() + Math.max(0,
                        c.getLevel() - (dice.countPerLevelBeyond() != null ? dice.countPerLevelBeyond() : c.getLevel()));
            }
            for (int i = 0; i < count; i++) {
                int roll = randomSource.nextInt(dice.sides()) + 1;
                rolls.add(roll);
                total += roll;
            }
        }
        int flat = 0;
        if (formula.statFlat() != null) {
            var statFlat = formula.statFlat();
            int mod = c.getStats().modifier(AbilityScore.valueOf(statFlat.stat().toUpperCase()));
            double value = mod;
            if (Boolean.TRUE.equals(statFlat.perLevel())) value *= c.getLevel();
            if (statFlat.multiplier() != null) value *= statFlat.multiplier();
            flat = (int) Math.floor(value);
        }
        total += flat;
        total = Math.max(0, total);
        result.putPayload("healingRoll", Map.of("rolls", rolls, "flat", flat, "total", total));
        return total;
    }

    /**
     * Target effects: computed here and, when the use names a target (Story 2.3), applied
     * through that combatant's effect engine — its protections/immunities answer as usual.
     * Without a target the stacks are printed for the table, as before targets existed.
     */
    private void emitTargetEffect(GameCharacter c, AbilityDefinition ability, Combatant target,
                                  ResolutionResult result) {
        var te = ability.targetEffect();
        if (te == null) return;
        String duration = te.durationRounds() != null ? " for " + te.durationRounds() + " round(s)" : "";
        if (te.stacks() != null) {
            int stacks = resolveStacks(te.stacks(), c.getLevel());
            result.putPayload("targetEffect", Map.of("effectId", te.effectId(), "stacks", stacks));
            if (target != null) {
                var landed = combatActions.applyEffect(target, new ApplyEffectRequest(
                        te.effectId(), stacks, null, te.durationRounds(), c.getCombatantId(),
                        false, false, false, null));
                CombatActionService.mergeSteps(result, target.getName(), landed.resolution());
                if (target != c) {
                    audit.log(target.getRoomName(), target.getCombatantId(), target.getName(), "apply-effect",
                            landed.auditSummary() + " — from " + c.getName() + " (" + ability.name() + ")");
                    combatants.save(target);
                }
                result.putPayload("effectsAppliedTo", target.getCombatantId());
                return;
            }
            result.addStep("ability-target", "Apply " + stacks + " " + te.effectId()
                    + " stack(s) to the target" + duration + " (DM applies)", 0, stacks);
        } else if (te.valueFromWeaponAverageDivisor() != null) {
            result.addStep("ability-target", "Apply " + te.effectId() + " with value = your weapon's average"
                    + " damage / " + te.valueFromWeaponAverageDivisor() + " (rounded down)" + duration
                    + " (DM computes)", 0, 0);
        }
    }

    private static int resolveStacks(AbilityDefinition.StacksFormula formula, int level) {
        if (formula.base() != null) return formula.base();
        if (formula.perLevel() != null) return formula.perLevel() * level;
        double value = level;
        if (formula.levelMultiplier() != null) value = level * formula.levelMultiplier();
        if (formula.levelOffset() != null) value = level + formula.levelOffset();
        if (formula.levelDivisor() != null) value = value / formula.levelDivisor();
        return (int) ("down".equals(formula.round()) ? Math.floor(value) : Math.ceil(value));
    }

    /** usesPerRest: flat amount, or stat-modifier-many with a minimum (Adrenaline: WILL mod, min 1). */
    private Integer maxUsesPerRest(GameCharacter c, AbilityDefinition ability) {
        var uses = ability.usesPerRest();
        if (uses == null) return null;
        if (uses.amount() != null) return uses.amount();
        int mod = c.getStats().modifier(AbilityScore.valueOf(uses.stat().toUpperCase()));
        return Math.max(uses.min() != null ? uses.min() : 0, mod);
    }

    /** Gain into AP / mana / the class resource / a pool — capped like gainResource. */
    private void gainAny(GameCharacter c, String resource, int amount, ResolutionResult result) {
        switch (resource) {
            case "ap" -> gainInto(result, "ap", amount,
                    c.getAp().getCurrent(), statEngine.computeMaxAP(c), v -> c.getAp().setCurrent(v));
            case "mana" -> gainInto(result, "mana", amount,
                    c.getMana().getCurrent(), statEngine.computeMaxMana(c), v -> c.getMana().setCurrent(v));
            default -> {
                if (c.getResource() != null && resource.equals(c.getResource().getType())) {
                    Integer derived = statEngine.computeClassResourceMax(c);
                    int cap = derived == null ? c.getResource().getMax()
                            : derived == StatDerivationEngine.UNBOUNDED_RESOURCE ? Integer.MAX_VALUE : derived;
                    gainInto(result, resource, amount, c.getResource().getCurrent(), cap,
                            v -> c.getResource().setCurrent(v));
                } else {
                    ensurePools(c);
                    var pool = c.findPool(resource);
                    if (pool != null) {
                        int cap = pool.getMax() != null ? pool.getMax() : Integer.MAX_VALUE;
                        gainInto(result, resource, amount, pool.getCurrent(), cap, pool::setCurrent);
                    }
                }
            }
        }
    }

    /** M4-A: validate → spend AP + mana → payload with saveDC/attackBonus (dice arrive M4-B). */
    public ActionResponse<CombatSnapshot> cast(String playerId, CastRequest req) {
        var c = getCharacter(playerId);
        if (req.spellId() == null || req.spellId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "spellId is required");
        }

        // 1. Spell exists.
        var spell = gameData.getSpell(req.spellId());
        if (spell == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown spell: " + req.spellId());
        }

        // 2. Is a caster at all.
        var casterType = statEngine.getCasterType(c);
        if (casterType == CasterType.NONE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "not a spellcaster");
        }

        // 3. Castable: known or prepared; spell belongs to the caster's class (Q21: reject mismatches).
        if (!c.getKnownSpells().contains(spell.id()) && !c.getPreparedSpells().contains(spell.id())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "spell is not known or prepared: " + spell.id());
        }
        if (!spell.classId().equals(c.getClassId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "spell '" + spell.id() + "' belongs to class '" + spell.classId() + "'");
        }

        // 4. Level access (spellcasting.json → spellLevelAccess[casterType][characterLevel - 1]).
        int castAtLevel = req.castAtLevel() != null ? req.castAtLevel() : spell.level();
        if (castAtLevel < spell.level()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "castAtLevel " + castAtLevel + " is below the spell's level " + spell.level());
        }
        int maxLevel = spellLevelAccess(casterType, c.getLevel());
        if (castAtLevel > maxLevel) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "cannot cast at level " + castAtLevel + " (max spell level " + maxLevel + ")");
        }

        // 4.5 (M4-D). Components: V blocked while silenced unless the no-verbal-components
        // grant is active; S has no blocker (Q22); W needs an equipped weapon — the data's
        // only W spells are arcane-ranger shots channelled through their (martial) weapon,
        // so any equipped weapon qualifies, caster weapons included.
        var casterWeapon = statEngine.findEquippedCasterWeapon(c);
        int threshold = statEngine.computeStackThreshold(c);
        if (spell.components() != null) {
            for (String component : spell.components()) {
                if (req.componentsAvailable() != null && !req.componentsAvailable().contains(component)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "component " + component + " is not available");
                }
                switch (component) {
                    case "V" -> {
                        boolean granted = ActiveMechanics
                                .collect(c, gameData, threshold, MechanicType.GRANT_ABILITY).stream()
                                .anyMatch(h -> h.mechanic().ability() == GrantableAbility.NO_VERBAL_COMPONENTS);
                        if (granted) continue;
                        boolean silenced = ActiveMechanics
                                .collect(c, gameData, threshold, MechanicType.PREVENT_ACTION).stream()
                                .anyMatch(h -> h.mechanic().action() == PreventableAction.VERBAL_SPELL);
                        if (silenced) {
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                    "silenced — cannot provide the verbal component");
                        }
                    }
                    case "W" -> {
                        if (casterWeapon == null && statEngine.findEquippedWeaponId(c) == null) {
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                    "spell requires an equipped weapon (W component)");
                        }
                    }
                    default -> { } // S — nothing blocks somatic components (Q22)
                }
            }
        }

        // 5. Prevented from casting (stunned, frozen, … — dormancy/composite-aware).
        var prevented = ActiveMechanics.collect(c, gameData, threshold, MechanicType.PREVENT_ACTION).stream()
                .filter(h -> h.mechanic().action() == PreventableAction.ALL)
                .findFirst();
        if (prevented.isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "cannot cast while " + prevented.get().def().name().toLowerCase());
        }
        // 5b (Q30, M5-B): non-proficient armor/shield is the one server-enforced penalty.
        if (statEngine.hasNonProficientArmorEquipped(c)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "cannot cast spells in non-proficient armor");
        }

        // 8 (early: the mana math below uses scaling). Upcasting requires a scaling block.
        int upcastSteps = castAtLevel - spell.level();
        if (upcastSteps > 0 && spell.scaling() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "spell does not upcast");
        }

        // 6 + 7. AP and mana — all-or-nothing: both validated before either is spent.
        // Free-text AP costs ("reaction", "1 or 2") are DM calls, not server math.
        if (spell.apCost().isSpecial()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "spell has a non-numeric AP cost ('" + spell.apCost().special()
                            + "') — DM adjudicates; spend AP via spend-resource");
        }
        int apCost = statEngine.resolveModifiedStat(c, ModifiableStat.SPELL_AP_COST,
                spell.apCost().resolve(statEngine.computeMaxAP(c)));
        requireSufficient("ap", c.getAp().getCurrent(), apCost);
        // Percent costs (paladin auras: "10%") resolve against the derived max mana.
        // Q23: flat reductions then percent multipliers, floored at 0 (resolveModifiedStat).
        if (spell.manaCost().isSpecial()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "spell has a non-numeric mana cost ('" + spell.manaCost().special()
                            + "') — DM adjudicates; spend mana via spend-resource");
        }
        Integer costIncrease = spell.scaling() != null ? spell.scaling().manaCostIncrease() : null;
        int baseManaCost = spell.manaCost().resolve(statEngine.computeMaxMana(c))
                + upcastSteps * (costIncrease != null ? costIncrease : 0);
        // M4-D: the equipped caster weapon's flat reduction joins the other flat
        // reductions before percent multipliers (Q23/Q24).
        if (casterWeapon != null && casterWeapon.manaCostReduction() > 0) {
            baseManaCost = Math.max(0, baseManaCost - casterWeapon.manaCostReduction());
        }
        int manaCost = statEngine.resolveModifiedStat(c, ModifiableStat.MANA_COST, baseManaCost);
        requireSufficient("mana", c.getMana().getCurrent(), manaCost);

        return resolveCast(playerId, c, spell, castAtLevel, apCost, manaCost,
                req.effectsTargetId(), req.applyEffectsToSelf(), new ResolutionResult());
    }

    /**
     * Post-validation cast resolution shared by /actions/cast and scroll casting
     * (ShopService.castScroll, Shops p.17): spends AP/mana, handles concentration
     * markers, self/party effects, payload and dice. Callers validate everything
     * else (and check sufficiency) first; scroll casts pass manaCost 0 and
     * castAtLevel = the spell's own level.
     */
    ActionResponse<CombatSnapshot> resolveCast(String playerId, GameCharacter c,
                                               com.steelmight.charactersheet.gamedata.SpellDefinition spell,
                                               int castAtLevel, int apCost, int manaCost,
                                               String targetPlayerId, Boolean applyEffectsToSelf,
                                               ResolutionResult result) {
        int upcastSteps = castAtLevel - spell.level();
        var casterWeapon = statEngine.findEquippedCasterWeapon(c);

        // M4-C: resolve the effects target BEFORE spending — an unknown target must
        // not cost the caster anything (all-or-nothing). Story 2.3: the target is any
        // combatant — a party member or a monster in the caster's room.
        Combatant effectsTarget = null;
        if (targetPlayerId != null && !targetPlayerId.isBlank()) {
            effectsTarget = targetPlayerId.equals(playerId) ? c : combatants.require(c.getRoomName(), targetPlayerId);
        } else if (Boolean.TRUE.equals(applyEffectsToSelf)) {
            effectsTarget = c;
        }
        // Taunt (ruling 2026-08-26): a taunted caster can only aim HARMFUL effects at the taunter.
        // Checked before spending — refused casts cost nothing.
        if (effectsTarget != null && effectsTarget != c && spell.effects() != null
                && spell.effects().stream().anyMatch(id -> {
                    var def = gameData.getEffect(id);
                    return def != null && def.isNegative();
                })) {
            combatActions.enforceTaunt(c, effectsTarget);
        }

        int apBefore = c.getAp().getCurrent();
        c.getAp().setCurrent(apBefore - apCost);
        result.addStep("spend-ap", "Spent " + apCost + " AP casting " + spell.name(),
                apBefore, apBefore - apCost);
        if (manaCost > 0) {
            int manaBefore = c.getMana().getCurrent();
            c.getMana().setCurrent(manaBefore - manaCost);
            result.addStep("spend-mana", "Spent " + manaCost + " mana casting " + spell.name()
                            + (upcastSteps > 0 ? " at level " + castAtLevel : ""),
                    manaBefore, manaBefore - manaCost);
        }

        // M4-C: attribution string — caster + spell. Feeds the shield-exclusivity
        // same-source rule, the target's log, and the concentration sweep.
        String castSource = playerId + ":" + spell.id();
        var duration = convertSpellDuration(spell.duration());

        // Concentration/channeling markers: starting a new maintained spell
        // immediately ends the previous one — marker AND its applied effects.
        boolean concentrationDropped = false;
        if (spell.concentration() || spell.channeling()) {
            for (String markerId : List.of("concentrating", "channeling")) {
                var previous = c.getActiveEffects().stream()
                        .filter(e -> markerId.equals(e.getEffectId()))
                        .findFirst()
                        .orElse(null);
                if (previous == null) continue;
                String previousSource = previous.getSource();
                result.addStep("drop-" + markerId,
                        "Starting " + spell.name() + " ends the previous maintained spell ("
                                + previousSource + ")", 0, 0);
                mergeSteps(result, "cast", effectEngine.remove(c, markerId));
                mergeSteps(result, "cast", effectEngine.removeBySource(c, previousSource));
                concentrationDropped = true;
            }
            String markerId = spell.channeling() ? "channeling" : "concentrating";
            mergeSteps(result, "cast", effectEngine.apply(c, new EffectApplication(
                    markerId, castSource, 1, null, duration.rounds(),
                    false, true, false, duration.type())));
        }
        result.putPayload("concentrationDropped", concentrationDropped);


        // M4-D: a staff's accuracy joins the derived DC/attack bonus (Q24).
        int saveDC = statEngine.computeSpellSaveDC(c);
        int attackBonus = statEngine.computeSpellAttackBonus(c);
        if (casterWeapon != null && casterWeapon.staffAccuracy() != null) {
            saveDC += casterWeapon.staffAccuracy().spellSaveDCBonus();
            attackBonus += casterWeapon.staffAccuracy().spellAttackBonus();
        }
        result.putPayload("saveDC", saveDC);
        result.putPayload("attackBonus", attackBonus);
        if (spell.damageType() != null) {
            result.putPayload("damageType", spell.damageType());
        }

        // Story 2.3 last mile: a target other than the caster is a COMBAT target — the
        // attack roll is compared to its AC, saves are rolled on it, and damage/healing/
        // effects land through its pipeline. Self-casts and untargeted casts print numbers.
        Combatant combatTarget = effectsTarget != null && effectsTarget != c ? effectsTarget : null;
        boolean landed = true;   // false after a miss against a combat target
        boolean saved = false;   // true after a successful save against a combat target

        // Attack-type spells roll their d20 as part of the cast (Guide 1.3/4.1:
        // 1d20 + proficiency + spell stat). Nat 20 = crit, nat 1 = fumble ("cannot
        // be changed by modifiers"); critRange effects are PERCENT chance — base 5%
        // (the 20 face), each 5% widens the range by one face.
        boolean criticalHit = false;
        if ("rangedSpellAttack".equals(spell.attackType())
                || "meleeSpellAttack".equals(spell.attackType())) {
            int roll = 1 + randomSource.nextInt(20);
            int critPercent = statEngine.resolveModifiedStat(c, ModifiableStat.CRIT_RANGE, 5);
            int critFrom = Math.max(2, 21 - Math.max(1, critPercent / 5));
            criticalHit = roll >= critFrom;
            boolean fumble = roll == 1;

            var attackRoll = new java.util.LinkedHashMap<String, Object>();
            attackRoll.put("roll", roll);
            attackRoll.put("bonus", attackBonus);
            attackRoll.put("total", roll + attackBonus);
            if (criticalHit) attackRoll.put("critical", true);
            if (fumble) attackRoll.put("criticalFailure", true);
            String versus = " (vs target AC)";
            if (combatTarget != null) {
                int targetAC = statEngine.computeAC(combatTarget);
                landed = criticalHit || (!fumble && roll + attackBonus >= targetAC);
                attackRoll.put("targetAC", targetAC);
                attackRoll.put("hit", landed);
                versus = " vs " + combatTarget.getName() + "'s AC " + targetAC + (landed ? " — HIT" : " — MISS");
            }
            result.putPayload("attackRoll", attackRoll);
            result.addStep("attack-roll",
                    "Spell attack: d20 " + roll + " + " + attackBonus + " = " + (roll + attackBonus)
                            + (criticalHit ? " — CRITICAL" : "")
                            + (fumble ? " — natural 1, automatic miss" : "")
                            + versus,
                    roll, roll + attackBonus);
        }

        // Save-type spells roll the target's save against the DC (E6: monsters save like
        // players). A save halves the damage and shrugs off the spell's effects.
        if (spell.saveStat() != null && combatTarget != null && landed) {
            var stat = parseAbility(spell.saveStat());
            saved = combatActions.rollSave(combatTarget, stat, saveDC, result);
            result.putPayload("save", Map.of("stat", stat.name(), "dc", saveDC, "success", saved));
        }

        // Self/party effects (M4-C): the target's own protections resolve in apply().
        // Without a target, the payload carries ready-to-apply details (converted
        // durations) so the table sees exactly what lands on hit.
        if (spell.effects() != null && !spell.effects().isEmpty()) {
            if (combatTarget != null && (!landed || saved)) {
                // A miss or a successful save shrugs the spell's effects off (Story 2.3).
                result.addStep("effects", combatTarget.getName() + (saved ? " saved — " : " was missed — ")
                        + "no effects applied", 0, 0);
            } else if (effectsTarget != null) {
                for (String effectId : spell.effects()) {
                    if (gameData.getEffect(effectId) == null) {
                        // Data-driven id, not caller input — skip with a visible step.
                        result.addStep("apply-effect",
                                "unknown effect '" + effectId + "' in " + spell.id() + " — skipped", 0, 0);
                        continue;
                    }
                    mergeSteps(result, "cast", effectEngine.apply(effectsTarget, new EffectApplication(
                            effectId, castSource, 1, null, duration.rounds(),
                            false, false, false, duration.type())));
                }
                if (effectsTarget != c) {
                    combatants.save(effectsTarget);
                    result.putPayload("effectsAppliedTo", effectsTarget.getCombatantId());
                }
            } else {
                var onHit = new java.util.ArrayList<Map<String, Object>>();
                for (String effectId : spell.effects()) {
                    var def = gameData.getEffect(effectId);
                    var entry = new java.util.LinkedHashMap<String, Object>();
                    entry.put("id", effectId);
                    entry.put("name", def != null ? def.name() : effectId);
                    if (duration.rounds() != null) entry.put("rounds", duration.rounds());
                    if (duration.type() != null) entry.put("durationType", duration.type().name());
                    onHit.add(entry);
                }
                result.putPayload("effectsOnHit", onHit);
            }
        }

        // M4-B: roll damage/healing at castAtLevel.
        // M4-D (Q24, Shops p.19): the weapon's spellModifier raises the spell-modifier
        // stat inside damage formulas; spellDamage adds flat to the damage roll.
        var spellAttr = statEngine.getSpellcastingAttribute(c);
        int spellMod = spellAttr != null ? c.getStats().modifier(spellAttr) : 0;
        if (spell.damage() != null) {
            var increase = spell.scaling() != null ? spell.scaling().damageIncrease() : null;
            int damageMod = spellMod + (casterWeapon != null ? casterWeapon.spellModifier() : 0);
            int weaponFlat = casterWeapon != null ? casterWeapon.spellDamage() : 0;
            var damage = rollFormula(spell.damage(), increase, upcastSteps, damageMod, weaponFlat,
                    criticalHit, result, "damage");
            result.putPayload("damage", damage);
            if (combatTarget != null) {
                if (!landed) {
                    result.addStep("target", combatTarget.getName() + " is unharmed — the spell missed", 0, 0);
                } else {
                    int amount = (Integer) damage.get("total");
                    if (saved) {
                        int halved = amount / 2;
                        result.addStep("save", "Damage halved by the save", amount, halved);
                        amount = halved;
                    }
                    landDamage(c, combatTarget, amount,
                            CombatActionService.damageTypeOf(spell.damageType()), List.of("spell"), result);
                }
            }
        }
        if (spell.healing() != null) {
            var increase = spell.scaling() != null ? spell.scaling().healingIncrease() : null;
            var healing = rollFormula(spell.healing(), increase, upcastSteps, spellMod, 0,
                    false, result, "healing");
            result.putPayload("healing", healing);
            // Healing lands on a named target (or the caster when the effects target is self);
            // untargeted healing rolls stay numbers for the DM.
            Combatant healTarget = combatTarget != null ? combatTarget : (effectsTarget == c ? c : null);
            if (healTarget != null) landHealing(c, healTarget, (Integer) healing.get("total"), result);
        }

        repo.save(c);
        audit.log(c, "cast", "Cast " + spell.name()
                + (combatTarget != null ? " at " + combatTarget.getName() : ""));
        return new ActionResponse<>(result, buildCombatSnapshot(c));
    }

    /** Base dice + flat + modMultiplier × spell-stat mod; each upcast step adds the
     *  scaling block's dice + flat + mod on top (M4-B). weaponFlat is the equipped
     *  caster weapon's spellDamage, kept as its own breakdown entry (M4-D).
     *  A critical hit doubles the damage (Game Owner 2026-07-07). */
    private Map<String, Object> rollFormula(DiceFormula base, DiceFormula increase,
                                            int upcastSteps, int spellMod, int weaponFlat,
                                            boolean critical, ResolutionResult result, String kind) {
        var rolls = new java.util.ArrayList<Integer>();
        rollDice(base.dice(), rolls);
        int flat = base.flat();
        double multiplier = base.modMultiplier();
        if (upcastSteps > 0 && increase != null) {
            for (int i = 0; i < upcastSteps; i++) {
                rollDice(increase.dice(), rolls);
            }
            flat += upcastSteps * increase.flat();
            multiplier += upcastSteps * increase.modMultiplier();
        }
        int modifierPart = (int) Math.floor(multiplier * spellMod);
        int diceTotal = rolls.stream().mapToInt(Integer::intValue).sum();
        int total = (diceTotal + flat + modifierPart + weaponFlat) * (critical ? 2 : 1);

        var breakdown = new java.util.LinkedHashMap<String, Object>();
        breakdown.put("rolls", rolls);
        breakdown.put("flat", flat);
        breakdown.put("modifier", modifierPart);
        if (weaponFlat != 0) breakdown.put("weaponDamage", weaponFlat);
        if (critical) breakdown.put("critMultiplier", 2);
        breakdown.put("total", total);
        result.addStep("roll-" + kind,
                "Rolled " + kind + ": " + diceTotal + " (dice) + " + flat + " (flat) + "
                        + modifierPart + " (modifier)"
                        + (weaponFlat != 0 ? " + " + weaponFlat + " (weapon)" : "")
                        + (critical ? ", DOUBLED (critical)" : ""), 0, total);
        return breakdown;
    }

    private void rollDice(Dice dice, List<Integer> into) {
        if (dice == null) return;
        for (int i = 0; i < dice.count(); i++) {
            into.add(1 + randomSource.nextInt(dice.sides()));
        }
    }

    /** M4-E: after-rest INT-modifier spell preparation (spellcasting.json →
     *  intModifierSpells). Replaces the whole preparedSpells list — a rest cleared
     *  it (M3 step 6); this is the re-choosing. */
    public ActionResponse<CombatSnapshot> prepareSpells(String playerId, PrepareSpellsRequest req) {
        var c = getCharacter(playerId);
        var ids = req != null && req.spellIds() != null ? req.spellIds() : List.<String>of();

        var casterType = statEngine.getCasterType(c);
        if (casterType == CasterType.NONE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "not a spellcaster");
        }

        int allowed = Math.max(0, c.getStats().modifier(AbilityScore.INT));
        if (ids.size() > allowed) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "can prepare at most " + allowed + " spells (INT modifier), got " + ids.size());
        }
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "duplicate spell ids");
        }

        int maxLevel = spellLevelAccess(casterType, c.getLevel());
        var countPerLevel = new java.util.HashMap<Integer, Integer>();
        for (String id : ids) {
            var spell = gameData.getSpell(id);
            if (spell == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown spell: " + id);
            }
            if (!spell.classId().equals(c.getClassId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "spell '" + id + "' belongs to class '" + spell.classId() + "'");
            }
            if (spell.level() > maxLevel) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "no access to level-" + spell.level() + " spells yet (max " + maxLevel + ")");
            }
            // Restrictions exactly as written in intModifierSpells: minors never get
            // 5th-level spells this way and carry max 2 of a level (except 1st);
            // majors carry max 1 of each level (except 1st).
            if (casterType == CasterType.MINOR && spell.level() >= 5) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "minor casters cannot prepare 5th-level spells from the INT bonus");
            }
            if (spell.level() != 1) {
                int cap = casterType == CasterType.MINOR ? 2 : 1;
                int count = countPerLevel.merge(spell.level(), 1, Integer::sum);
                if (count > cap) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "at most " + cap + " prepared spell(s) of level " + spell.level()
                                    + " (1st-level spells are exempt)");
                }
            }
        }

        var result = new ResolutionResult();
        result.addStep("prepare-spells", "Prepared spells replaced",
                c.getPreparedSpells().size(), ids.size());
        c.getPreparedSpells().clear();
        c.getPreparedSpells().addAll(ids);
        repo.save(c);
        return new ActionResponse<>(result, buildCombatSnapshot(c));
    }

    /**
     * Self-describing weapon attack (Guide 4.2): proficient attackers roll
     * d20 + proficiency + the weapon's best stat modifier and add the modifier
     * to damage; non-proficient attackers roll a bare d20, add nothing, and
     * cannot use the weapon's properties. Damage = weapon dice + flat +
     * per-level scaling; crits double it (Game Owner 2026-07-07).
     * Advantage/disadvantage from active effects and non-proficient armor
     * resolve server-side — stacked disadvantage is an automatic miss
     * (Guide 4.3). The DM compares the total to the target's AC until the
     * encounter round lands.
     */
    public ActionResponse<CombatSnapshot> weaponAttack(String playerId, WeaponAttackRequest req) {
        var c = getCharacter(playerId);

        var equippedWeapons = c.getInventory().stream()
                .filter(InventoryEntry::isEquipped)
                .filter(e -> {
                    var it = statEngine.resolveItem(c, e.getItemId());
                    return it != null && it.kind() == ItemKind.WEAPON;
                })
                .toList();
        if (equippedWeapons.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "no weapon equipped");
        }
        InventoryEntry entry;
        if (req != null && req.itemId() != null && !req.itemId().isBlank()) {
            entry = equippedWeapons.stream()
                    .filter(e -> e.getItemId().equals(req.itemId()))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            req.itemId() + " is not an equipped weapon"));
        } else if (equippedWeapons.size() == 1) {
            entry = equippedWeapons.get(0);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "dual-wielding — specify which weapon attacks (itemId)");
        }
        var item = statEngine.resolveItem(c, entry.getItemId());
        var node = item.node();

        int threshold = statEngine.computeStackThreshold(c);
        for (var hit : ActiveMechanics.collect(c, gameData, threshold, MechanicType.PREVENT_ACTION)) {
            var action = hit.mechanic().action();
            if (action == PreventableAction.ALL || action == PreventableAction.WEAPON_ATTACK) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "cannot attack while " + hit.def().name().toLowerCase());
            }
        }

        int apCost = Math.max(1, statEngine.resolveModifiedStat(c, ModifiableStat.WEAPON_AP_COST,
                node.path("apCost").asInt(3)));
        requireSufficient("ap", c.getAp().getCurrent(), apCost);

        // Story 2.3 last mile: a named target is resolved (and taunt-checked) BEFORE spending —
        // an unknown or forbidden target costs nothing.
        Combatant target = null;
        if (req != null && req.targetCombatantId() != null && !req.targetCombatantId().isBlank()) {
            target = combatants.require(c.getRoomName(), req.targetCombatantId());
            if (target == c) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "you cannot attack yourself");
            }
            combatActions.enforceTaunt(c, target);
        }

        boolean proficient = statEngine.isProficientWith(c, item);
        // finesse weapons list several stats — the best modifier applies
        int statMod = 0;
        boolean first = true;
        for (var s : node.path("stat")) {
            int mod = c.getStats().modifier(parseAbility(s.asText()));
            if (first || mod > statMod) statMod = mod;
            first = false;
        }

        // Advantage/disadvantage on own attacks: active effects (unconditional
        // ones — "while flanking" etc. stay DM calls) plus non-proficient armor
        // (Q30); one advantage cancels one disadvantage.
        int advantage = 0;
        int disadvantage = 0;
        var chargedToConsume = new java.util.ArrayList<String>();
        for (var type : List.of(MechanicType.ADVANTAGE, MechanicType.DISADVANTAGE)) {
            for (var hit : ActiveMechanics.collect(c, gameData, threshold, type)) {
                var on = hit.mechanic().on();
                if (on != AdvantageTarget.OWN_ATTACKS && on != AdvantageTarget.ALL_ROLLS) continue;
                if (hit.mechanic().condition() != null) continue;
                if (type == MechanicType.ADVANTAGE) advantage++; else disadvantage++;
                if (hit.mechanic().charges() != null) chargedToConsume.add(hit.def().id());
            }
        }
        boolean armorDisadvantage = statEngine.hasNonProficientArmorEquipped(c);
        if (armorDisadvantage) disadvantage++;
        int cancelled = Math.min(advantage, disadvantage);
        advantage -= cancelled;
        disadvantage -= cancelled;

        var result = new ResolutionResult();
        int apBefore = c.getAp().getCurrent();
        c.getAp().setCurrent(apBefore - apCost);
        result.addStep("spend-ap", "Spent " + apCost + " AP attacking with " + item.name(),
                apBefore, apBefore - apCost);

        var weaponInfo = new java.util.LinkedHashMap<String, Object>();
        weaponInfo.put("id", item.id());
        weaponInfo.put("name", item.name());
        result.putPayload("weapon", weaponInfo);
        if (node.hasNonNull("damageType")) result.putPayload("damageType", node.path("damageType").asText());
        if (entry.isSilvered()) result.putPayload("silvered", true);
        if (proficient && node.path("properties").isArray() && !node.path("properties").isEmpty()) {
            var properties = new java.util.ArrayList<String>();
            node.path("properties").forEach(p -> properties.add(p.asText()));
            result.putPayload("properties", properties);
        }

        var attackRoll = new java.util.LinkedHashMap<String, Object>();
        int attackBonus = proficient ? statEngine.computeProficiencyBonus(c) + statMod : 0;
        boolean critical = false;

        if (disadvantage >= 2) {
            // Guide 4.3: an attack that already has disadvantage while wearing
            // non-proficient armor misses automatically.
            attackRoll.put("autoMiss", true);
            result.putPayload("attackRoll", attackRoll);
            result.addStep("attack-roll", "Stacked disadvantage — automatic miss", 0, 0);
        } else {
            var rolls = new java.util.ArrayList<Integer>();
            rolls.add(1 + randomSource.nextInt(20));
            int natural = rolls.get(0);
            if (advantage > 0 || disadvantage > 0) {
                rolls.add(1 + randomSource.nextInt(20));
                natural = advantage > 0 ? Math.max(rolls.get(0), rolls.get(1))
                        : Math.min(rolls.get(0), rolls.get(1));
            }
            int critPercent = statEngine.resolveModifiedStat(c, ModifiableStat.CRIT_RANGE, 5);
            int critFrom = Math.max(2, 21 - Math.max(1, critPercent / 5));
            critical = natural >= critFrom;
            boolean fumble = natural == 1;

            attackRoll.put("roll", natural);
            if (rolls.size() > 1) attackRoll.put("rolls", rolls);
            if (advantage > 0) attackRoll.put("advantage", true);
            if (disadvantage > 0) attackRoll.put("disadvantage", true);
            attackRoll.put("bonus", attackBonus);
            attackRoll.put("total", natural + attackBonus);
            if (critical) attackRoll.put("critical", true);
            if (fumble) attackRoll.put("criticalFailure", true);

            // Against a named target the roll is compared to THEIR AC right here: a crit
            // always hits, a natural 1 always misses, otherwise total ≥ AC.
            String versus = " (vs target AC)";
            boolean hit = !fumble;
            if (target != null) {
                int targetAC = statEngine.computeAC(target);
                hit = critical || (!fumble && natural + attackBonus >= targetAC);
                attackRoll.put("targetAC", targetAC);
                attackRoll.put("hit", hit);
                versus = " vs " + target.getName() + "'s AC " + targetAC + (hit ? " — HIT" : " — MISS");
            }
            result.putPayload("attackRoll", attackRoll);
            result.addStep("attack-roll",
                    "Weapon attack: d20 " + natural
                            + (rolls.size() > 1 ? " (rolled " + rolls + (advantage > 0 ? ", advantage" : ", disadvantage") + ")" : "")
                            + " + " + attackBonus + " = " + (natural + attackBonus)
                            + (critical ? " — CRITICAL" : "")
                            + (fumble ? " — natural 1, automatic miss" : "")
                            + versus,
                    natural, natural + attackBonus);

            // damage: dice + flat + per-level scaling (+ stat mod when proficient)
            int weaponLevel = Math.max(1, entry.getUpgradeTier());
            var damageNode = node.path("damage");
            var formula = new DiceFormula(
                    damageNode.path("modMultiplier").asDouble(1),
                    damageNode.path("flat").asInt(0)
                            + node.path("scaling").asInt(0) * (weaponLevel - 1),
                    Dice.from(damageNode.path("dice")));
            var damage = rollFormula(formula, null, 0, proficient ? statMod : 0, 0, critical, result, "damage");
            result.putPayload("damage", damage);

            // A hit lands through the target's own pipeline (resistance → armor → shields →
            // HP → death), attributed to this attacker.
            if (target != null && hit) {
                landDamage(c, target, (Integer) damage.get("total"),
                        CombatActionService.damageTypeOf(node.path("damageType").asText(null)),
                        entry.isSilvered() ? List.of("directAttack", "weapon", "silvered") : List.of("directAttack", "weapon"),
                        result);
            } else if (target != null) {
                result.addStep("target", target.getName() + " is unharmed — the attack missed", 0, 0);
            }
        }

        if (!proficient) {
            result.addStep("proficiency", "Not proficient with " + item.name()
                    + " — bare d20, no stat modifier, weapon properties unavailable", 0, 0);
        }
        if (armorDisadvantage) {
            result.addStep("proficiency",
                    "Non-proficient armor imposes disadvantage on weapon attacks", 0, 0);
        }

        // single-use riders (disadvantage-next-attack) are spent by this attack
        for (var effectId : chargedToConsume.stream().distinct().toList()) {
            mergeSteps(result, "attack", effectEngine.remove(c, effectId));
            result.addTriggeredEffect("consumed:" + effectId);
        }

        repo.save(c);
        audit.log(c, "weapon-attack", "Attacked with " + node.path("name").asText(entry.getItemId()));
        return new ActionResponse<>(result, buildCombatSnapshot(c));
    }

    private int spellLevelAccess(CasterType casterType, int characterLevel) {
        var access = gameData.getSpellcasting().path("spellLevelAccess")
                .path(casterType.name().toLowerCase());
        if (access.isArray() && characterLevel >= 1 && characterLevel <= access.size()) {
            return access.get(characterLevel - 1).asInt(1);
        }
        return 1;
    }

    public ActionResponse<CombatSnapshot> gainResource(String playerId, GainResourceRequest req) {
        var c = getCharacter(playerId);
        if (req.amount() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be positive");
        }
        var result = new ResolutionResult();

        switch (req.resource()) {
            case "ap" -> gainInto(result, "ap", req.amount(),
                    c.getAp().getCurrent(), statEngine.computeMaxAP(c), v -> c.getAp().setCurrent(v));
            case "mana" -> gainInto(result, "mana", req.amount(),
                    c.getMana().getCurrent(), statEngine.computeMaxMana(c), v -> c.getMana().setCurrent(v));
            default -> {
                if (c.getResource() != null && req.resource().equals(c.getResource().getType())) {
                    // Cap at the derived max (M3 Part A); builders like focus are unbounded;
                    // fall back to the stored max when no derivation exists for the class.
                    Integer derived = statEngine.computeClassResourceMax(c);
                    int cap = derived == null ? c.getResource().getMax()
                            : derived == StatDerivationEngine.UNBOUNDED_RESOURCE ? Integer.MAX_VALUE : derived;
                    gainInto(result, req.resource(), req.amount(),
                            c.getResource().getCurrent(), cap,
                            v -> c.getResource().setCurrent(v));
                } else {
                    ensurePools(c);
                    var pool = c.findPool(req.resource());
                    if (pool == null) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown resource: " + req.resource());
                    }
                    int cap = pool.getMax() != null ? pool.getMax() : Integer.MAX_VALUE;
                    gainInto(result, req.resource(), req.amount(),
                            pool.getCurrent(), cap, pool::setCurrent);
                }
            }
        }

        repo.save(c);
        audit.log(c, "gain-resource", "Gained " + req.amount() + " " + req.resource());
        return new ActionResponse<>(result, buildCombatSnapshot(c));
    }

    private static void gainInto(ResolutionResult result, String resource, int amount,
                                 int current, int max, java.util.function.IntConsumer setter) {
        int after = Math.min(max, current + amount);
        int lost = current + amount - after;
        setter.accept(after);
        result.addStep("gain-" + resource,
                "Gained " + (after - current) + " " + resource
                        + (lost > 0 ? " (" + lost + " lost to cap)" : ""),
                current, after);
    }

    public ActionResponse<CombatSnapshot> turnStart(String playerId) {
        var c = getCharacter(playerId);
        // Turn gating: within a running encounter, only the current character may start,
        // and only once (strict start → end alternation). Free ticking outside encounters.
        // A participant's FIRST turn of the combat gets no AP recovery (2026-07-16 ruling).
        boolean apRecovery = encounters.validateAndMarkTurnStart(c);
        // Per-turn ability budgets reset at turn start (Story 1.4).
        c.getAbilityUses().forEach(u -> u.setUsedThisTurn(0));
        // M2-B: DoT ticks (Q13: before AP recovery) → AP recovery → startOfTurn triggers.
        var result = turnTickService.turnStart(c, apRecovery);
        repo.save(c);
        return new ActionResponse<>(result, buildCombatSnapshot(c));
    }

    public ActionResponse<CombatSnapshot> turnEnd(String playerId) {
        var c = getCharacter(playerId);
        encounters.validateTurnEnd(c);
        // M2-B: HoT ticks → endOfTurn triggers (suffocating→exhaustion) → duration
        // expiry / threshold stack consumption (M0-A/N9).
        var result = turnTickService.turnEnd(c);
        repo.save(c);
        // Nobody starts turns themselves in combat — the next combatant's turn (player OR
        // monster) begins here, its ticks merged into this log (TurnFlowService).
        turnFlow.autoStartNext(c.getRoomName(), encounters.completeTurn(c), result);
        return new ActionResponse<>(result, buildCombatSnapshot(c));
    }

    /** Medicine-check revive / Death-fight return (M2-D, N11). */
    public ActionResponse<CombatSnapshot> revive(String playerId, ReviveRequest req) {
        var c = getCharacter(playerId);
        var result = new ResolutionResult();

        if (req.criticalFail()) {
            if (c.getLifeStatus() != LifeStatus.DOWNED) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "criticalFail applies to a downed character (status: " + c.getLifeStatus() + ")");
            }
            c.setLifeStatus(LifeStatus.DEAD);
            c.setDownedRoundsRemaining(null);
            c.setPendingDeathFight(true);
            result.addStep("death",
                    "Medicine check critically failed — " + c.getName()
                            + " dies; Death fight after this combat", 0, 0);
            result.addTriggeredEffect("death");
            repo.save(c);
            audit.log(c, "revive", "Revive CRIT-FAILED — dead, Death fight pending");
            return new ActionResponse<>(result, buildCombatSnapshot(c));
        }

        if (c.getLifeStatus() == LifeStatus.ALIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "character is not downed or dead");
        }

        if (req.deathStackGained()) {
            // Returning from a WON Death fight: revert to pre-fight status is DM-driven
            // (hpRestored); the sheet tracks the victory count.
            c.setDeathStacks(c.getDeathStacks() + 1);
            result.addStep("death-stack",
                    "Death fight won — Death grows stronger", c.getDeathStacks() - 1, c.getDeathStacks());
        }

        int hp = req.hpRestored() != null ? req.hpRestored() : 1;
        if (hp < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "hpRestored must be at least 1");
        }
        hp = Math.min(hp, statEngine.computeMaxHP(c));
        c.setLifeStatus(LifeStatus.ALIVE);
        c.setDownedRoundsRemaining(null);
        c.setPendingDeathFight(false);
        int before = c.getHp().getCurrent();
        c.getHp().setCurrent(hp);
        result.addStep("revive", c.getName() + " is back up", before, hp);

        repo.save(c);
        audit.log(c, "revive", "Revived at " + hp + " HP");
        return new ActionResponse<>(result, buildCombatSnapshot(c));
    }

    /** Combat start (M2-D + Q18): AP resets to starting (= recovery), downs counter resets. */
    public ActionResponse<CombatSnapshot> combatStart(String playerId) {
        var c = getCharacter(playerId);
        var result = new ResolutionResult();

        int startingAp = Math.min(statEngine.computeMaxAP(c), c.getAp().getRecovery());
        int before = c.getAp().getCurrent();
        if (before != startingAp) {
            c.getAp().setCurrent(startingAp);
            result.addStep("combat-start", "AP set to starting", before, startingAp);
        }
        if (c.getDownsThisCombat() != 0) {
            result.addStep("combat-start", "Downs-this-combat counter reset (revive DC baseline)",
                    c.getDownsThisCombat(), 0);
            c.setDownsThisCombat(0);
        }

        repo.save(c);
        return new ActionResponse<>(result, buildCombatSnapshot(c));
    }

    /** Single tiered rest (M3 Part C / Q20): restores p% of HP/mana/class resources,
     *  clears until-rest effects and ALL threshold stacks (N10), resets prepared spells.
     *  deathStacks and AP are untouched (AP is combat-scoped, Q18).
     *  Game Owner 2026-08-12: the quality tier is any integer 0-100, player-typed —
     *  the fixed 25/50/75/100 steps are gone; every p%-scaled formula takes it as-is. */
    public ActionResponse<CombatSnapshot> rest(String playerId, RestRequest req) {
        var c = getCharacter(playerId);
        int tier = req != null && req.tier() != null ? req.tier() : 100;
        if (tier < 0 || tier > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "tier must be between 0 and 100 (got " + tier + ")");
        }
        double p = tier / 100.0;
        var result = new ResolutionResult();

        // 1. HP += p% of derived max (capped); temp HP clears with the rest.
        int maxHp = statEngine.computeMaxHP(c);
        int hpBefore = c.getHp().getCurrent();
        int hpAfter = Math.min(maxHp, hpBefore + (int) Math.floor(maxHp * p));
        if (hpAfter != hpBefore) {
            c.getHp().setCurrent(hpAfter);
            result.addStep("rest-hp", "Restored " + (hpAfter - hpBefore) + " HP (" + tier + "%)",
                    hpBefore, hpAfter);
        }
        if (c.getHp().getTemp() > 0) {
            mergeSteps(result, "rest", effectEngine.remove(c, "temporary-hp"));
            c.getHp().setTemp(0);
        }

        // 2. Mana += p% of derived max (casters).
        int maxMana = statEngine.computeMaxMana(c);
        if (maxMana > 0) {
            int before = c.getMana().getCurrent();
            int after = Math.min(maxMana, before + (int) Math.floor(maxMana * p));
            if (after != before) {
                c.getMana().setCurrent(after);
                result.addStep("rest-mana", "Restored " + (after - before) + " mana (" + tier + "%)",
                        before, after);
            }
        }

        // 3. Class resource (Q19): builders reset to 0; charge-style counts restore
        //    floor + probability; pools restore p% of max. Max is always the derived value.
        restoreClassResource(c, p, result);

        // 4. Until-rest effects clear (enum name kept from the old split, M4-C compatibility).
        var untilRest = c.getActiveEffects().stream()
                .filter(e -> e.getDurationType() == DurationType.UNTIL_LONG_REST)
                .map(ActiveEffect::getEffectId)
                .distinct()
                .toList();
        for (var effectId : untilRest) {
            mergeSteps(result, "rest", effectEngine.remove(c, effectId));
        }

        // 5. N10: ANY rest clears every accumulated negative stack, regardless of tier.
        //    multiInstance DoTs (burning, envenomed) are exempt from threshold
        //    dormancy/consumption but NOT from this — you don't sleep while on fire.
        var stackEffects = c.getActiveEffects().stream()
                .filter(e -> {
                    var def = gameData.getEffect(e.getEffectId());
                    return def != null && def.stackBased() && def.isNegative();
                })
                .map(ActiveEffect::getEffectId)
                .distinct()
                .toList();
        for (var effectId : stackEffects) {
            mergeSteps(result, "rest", effectEngine.remove(c, effectId));
        }

        // 6. Prepared spells reset — re-choosing is POST /actions/prepare-spells (M4-E).
        if (!c.getPreparedSpells().isEmpty()) {
            result.addStep("rest-spells", "Prepared spells cleared", c.getPreparedSpells().size(), 0);
            c.getPreparedSpells().clear();
        }

        // 7. Deck of Fates consume cards return on any rest (burned cards are gone forever).
        int cardsRestored = deckTemplates.restoreConsumedCards(playerId);
        if (cardsRestored > 0) {
            result.addStep("rest-deck", "Consumed deck cards restored", 0, cardsRestored);
        }

        // 8. Sub-resource pools (Story 1.2, ruling 2026-07-13): on-rest pools regain
        //    ceil(tier% × max), additive and capped. Manual pools (fury) are untouched.
        ensurePools(c);
        for (var poolDef : gameData.getPoolsForClass(c.getClassId())) {
            if (!"on-rest".equals(poolDef.restore()) || poolDef.max() == null) continue;
            var pool = c.findPool(poolDef.id());
            if (pool == null) continue;
            int before = pool.getCurrent();
            int regained = (int) Math.ceil(poolDef.max() * p);
            int after = Math.min(poolDef.max(), before + regained);
            if (after != before) {
                pool.setCurrent(after);
                result.addStep("rest-pool", poolDef.name() + " +" + (after - before)
                        + " (" + tier + "%)", before, after);
            }
        }

        // 8b. Ability use budgets (Story 1.4, tier ruling): usedThisRest reduces by
        //     ceil(tier% × maxUses) — NOT a full clear. Turn budgets clear too.
        for (var abilityUse : c.getAbilityUses()) {
            abilityUse.setUsedThisTurn(0);
            if (abilityUse.getUsedThisRest() <= 0) continue;
            var def = gameData.getAbility(abilityUse.getAbilityId());
            Integer maxUses = def != null ? maxUsesPerRest(c, def) : null;
            if (maxUses == null) continue;
            int restored = (int) Math.ceil(maxUses * p);
            int after = Math.max(0, abilityUse.getUsedThisRest() - restored);
            if (after != abilityUse.getUsedThisRest()) {
                result.addStep("rest-ability", (def.name() != null ? def.name() : abilityUse.getAbilityId())
                        + " uses restored (" + tier + "%)", abilityUse.getUsedThisRest(), after);
                abilityUse.setUsedThisRest(after);
            }
        }

        // 9. M5-C: usesPerLongRest item charges restore with the same floor+probability
        //    treatment as charge-style class resources at partial tiers.
        for (var entry : c.getInventory()) {
            var item = statEngine.resolveItem(c, entry.getItemId());
            if (item == null || !item.node().path("usesPerLongRest").isInt()) continue;
            int max = item.node().path("usesPerLongRest").asInt();
            int before = entry.getChargesRemaining() != null ? entry.getChargesRemaining() : max;
            if (before >= max) continue;
            double exact = max * p;
            int restored = (int) Math.floor(exact);
            double frac = exact - restored;
            if (frac > 0 && randomSource.nextInt(100) < Math.round(frac * 100)) {
                restored += 1;
            }
            int after = Math.min(max, before + restored);
            if (after != before) {
                entry.setChargesRemaining(after);
                result.addStep("rest-charges", entry.getItemId() + " charges restored", before, after);
            }
        }

        repo.save(c);
        audit.log(c, "rest", "Rested (" + tier + "%)");
        return new ActionResponse<>(result, buildCombatSnapshot(c));
    }

    private void restoreClassResource(GameCharacter c, double p, ResolutionResult result) {
        var resource = c.getResource();
        if (resource == null || resource.getType() == null) return;
        String type = resource.getType();

        if (statEngine.isBuilderResource(type)) {
            int before = resource.getCurrent();
            if (before != 0) {
                resource.setCurrent(0);
                result.addStep("rest-" + type, type + " resets to 0 (builder resource)", before, 0);
            }
            return;
        }

        Integer derivedMax = statEngine.computeClassResourceMax(c);
        int max = derivedMax != null && derivedMax != StatDerivationEngine.UNBOUNDED_RESOURCE
                ? derivedMax : resource.getMax();
        if (derivedMax != null && derivedMax != StatDerivationEngine.UNBOUNDED_RESOURCE) {
            resource.setMax(derivedMax); // max is derived, never hand-set (M3 Part A)
        }
        if (max <= 0) return;

        int before = resource.getCurrent();
        int restored;
        if (statEngine.isChargeStyleResource(type)) {
            // floor(max × p) guaranteed + one extra charge with probability frac (Q19).
            double exact = max * p;
            restored = (int) Math.floor(exact);
            double frac = exact - restored;
            if (frac > 0 && randomSource.nextInt(100) < Math.round(frac * 100)) {
                restored += 1;
            }
        } else {
            restored = (int) Math.floor(max * p);
        }
        int after = Math.min(max, before + restored);
        if (after != before) {
            resource.setCurrent(after);
            result.addStep("rest-" + type, "Restored " + (after - before) + " " + type, before, after);
        }
    }

    /**
     * Resolve-onto-target (Story 2.3 last mile): push rolled damage through the TARGET's
     * pipeline, attributed to the attacker, merge the target's steps under its name, persist
     * and audit it, and describe the outcome in the payload. A damage type the enum doesn't
     * know ("?" placeholders in the data) falls back to a printed instruction.
     */
    private void landDamage(GameCharacter attacker, Combatant target, int amount, DamageType type,
                            List<String> tags, ResolutionResult result) {
        var outcome = new java.util.LinkedHashMap<String, Object>();
        outcome.put("combatantId", target.getCombatantId());
        outcome.put("name", target.getName());
        if (type == null) {
            result.addStep("target", "Apply " + amount + " damage to " + target.getName()
                    + " manually — the damage type is not machine-readable", 0, amount);
            outcome.put("manual", true);
            result.putPayload("target", outcome);
            return;
        }
        if (amount > 0) {
            var landed = combatActions.damage(target, new DamageRequest(amount, type, tags, false,
                    attacker.getCombatantId(), false, null, null));
            CombatActionService.mergeSteps(result, target.getName(), landed.resolution());
            audit.log(target.getRoomName(), target.getCombatantId(), target.getName(), "damage",
                    landed.auditSummary() + " — from " + attacker.getName());
            combatants.save(target);
        }
        outcome.put("hpAfter", target.getHp().getCurrent());
        outcome.put("hpMax", statEngine.computeMaxHP(target));
        outcome.put("status", target.getLifeStatus().name());
        result.putPayload("target", outcome);
    }

    /** Healing lands the same way — through the target's pipeline (Cursed/Decaying apply). */
    private void landHealing(GameCharacter healer, Combatant target, int amount, ResolutionResult result) {
        if (amount <= 0) return;
        var landed = combatActions.heal(target, new HealRequest(amount));
        CombatActionService.mergeSteps(result, target.getName(), landed.resolution());
        audit.log(target.getRoomName(), target.getCombatantId(), target.getName(), "heal",
                landed.auditSummary() + " — from " + healer.getName());
        combatants.save(target);
        var outcome = new java.util.LinkedHashMap<String, Object>();
        outcome.put("combatantId", target.getCombatantId());
        outcome.put("name", target.getName());
        outcome.put("hpAfter", target.getHp().getCurrent());
        outcome.put("hpMax", statEngine.computeMaxHP(target));
        outcome.put("status", target.getLifeStatus().name());
        result.putPayload("target", outcome);
    }

    private static void mergeSteps(ResolutionResult into, String prefix, ResolutionResult from) {
        from.getSteps().forEach(s ->
                into.addStep(prefix + ":" + s.rule(), s.note(), s.valueBefore(), s.valueAfter()));
        from.getEffectsTriggered().forEach(into::addTriggeredEffect);
    }

    /** A spell duration converted per Q25 (a round ≈ 10 seconds, 1 min = 6 rounds). */
    private record ConvertedDuration(Integer rounds, DurationType type) {}

    private static final java.util.regex.Pattern DURATION_PATTERN =
            java.util.regex.Pattern.compile("^(\\d+)\\s*(round|turn|min|hour|day|week)");

    /**
     * Q25 conversion: "1 round"/"1 turn" = 1, "1 min" = 6, "10 min" = 60; an hour or
     * longer = until the next rest (M3's UNTIL_LONG_REST column); null / "until
     * dispelled" / unparseable = no stored expiry.
     */
    private ConvertedDuration convertSpellDuration(String duration) {
        if (duration == null) return new ConvertedDuration(null, null);
        String d = duration.trim().toLowerCase();
        if (d.startsWith("up to ")) d = d.substring("up to ".length());
        if (d.startsWith("until dispelled")) return new ConvertedDuration(null, DurationType.UNTIL_DISPELLED);
        if (d.startsWith("until long rest")) return new ConvertedDuration(null, DurationType.UNTIL_LONG_REST);
        var m = DURATION_PATTERN.matcher(d);
        if (m.find()) {
            int n = Integer.parseInt(m.group(1));
            return switch (m.group(2)) {
                case "round", "turn" -> new ConvertedDuration(n, DurationType.ROUNDS);
                case "min" -> new ConvertedDuration(n * 6, DurationType.ROUNDS);
                default -> new ConvertedDuration(null, DurationType.UNTIL_LONG_REST); // hour/day/week
            };
        }
        return new ConvertedDuration(null, null);
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
            // DM override — route through the effect engine so the temporary-hp effect and
            // hp.temp stay mirrored (set semantics: remove then re-apply, bypassing
            // keep-higher AND the M2-A protection phase — it's a system write).
            effectEngine.remove(c, "temporary-hp");
            if (req.tempHp() > 0) {
                effectEngine.apply(c, new EffectApplication(
                        "temporary-hp", "dm-override", 1, req.tempHp(), null, false, true, false));
            }
        }
        // Typed shields, same set semantics as temp HP above (demo feedback #14). Q08
        // exclusivity means re-applying replaces rather than stacks.
        applyShieldOverride(c, "physical-shield", req.tempShieldPhysical(), "tempShieldPhysical");
        applyShieldOverride(c, "magic-shield", req.tempShieldMagical(), "tempShieldMagical");
        if (req.currentAp() != null) {
            if (req.currentAp() < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "currentAp must be >= 0");
            }
            c.getAp().setCurrent(Math.min(req.currentAp(), statEngine.computeMaxAP(c)));
        }
        if (req.currentMana() != null) {
            if (req.currentMana() < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "currentMana must be >= 0");
            }
            c.getMana().setCurrent(Math.min(req.currentMana(), statEngine.computeMaxMana(c)));
        }
        repo.save(c);
        audit.log(c, "vitals-edit", "Vitals override"
                + (req.currentHp() != null ? " HP=" + req.currentHp() : "")
                + (req.tempHp() != null ? " temp=" + req.tempHp() : "")
                + (req.tempShieldPhysical() != null ? " physShield=" + req.tempShieldPhysical() : "")
                + (req.tempShieldMagical() != null ? " magShield=" + req.tempShieldMagical() : "")
                + (req.currentAp() != null ? " AP=" + req.currentAp() : "")
                + (req.currentMana() != null ? " mana=" + req.currentMana() : ""));
        return buildCombatSnapshot(c);
    }

    /** Set-semantics write of a typed shield pool: remove the effect, re-apply when > 0. */
    private void applyShieldOverride(GameCharacter c, String effectId, Integer value, String field) {
        if (value == null) return;
        if (value < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be >= 0");
        }
        effectEngine.remove(c, effectId);
        if (value > 0) {
            effectEngine.apply(c, new EffectApplication(
                    effectId, "dm-override", 1, value, null, false, true, false));
        }
    }

    /**
     * Replace the pinned-stat map wholesale (demo feedback #11/#12). Unknown keys 400
     * rather than being dropped — a silently ignored override is worse than an error.
     * Current HP/mana/AP are re-clamped, so lowering a pinned max doesn't leave a
     * character sitting above it.
     */
    public CombatSnapshot updateStatOverrides(String playerId, StatOverridesRequest req) {
        var c = getCharacter(playerId);
        var next = new java.util.HashMap<String, Integer>();
        if (req.overrides() != null) {
            for (var entry : req.overrides().entrySet()) {
                if (entry.getValue() == null) continue; // null = "back to derived"
                OverridableStat stat;
                try {
                    stat = OverridableStat.fromKey(entry.getKey());
                } catch (IllegalArgumentException e) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Unknown overridable stat '" + entry.getKey() + "'");
                }
                if (entry.getValue() < 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            stat.getKey() + " override must be >= 0");
                }
                next.put(stat.getKey(), entry.getValue());
            }
        }

        var overrides = c.getStatOverrides();
        overrides.clear();
        overrides.putAll(next);

        c.getHp().setCurrent(Math.min(c.getHp().getCurrent(), statEngine.computeMaxHP(c)));
        c.getMana().setCurrent(Math.min(c.getMana().getCurrent(), statEngine.computeMaxMana(c)));
        c.getAp().setCurrent(Math.min(c.getAp().getCurrent(), statEngine.computeMaxAP(c)));
        clampPoolsToMax(c);
        repo.save(c);
        audit.log(c, "stat-overrides", next.isEmpty()
                ? "Cleared all stat overrides"
                : "Stat overrides set: " + next);
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

    /** HP-state condition terms (M0-B R4) — pure derivation from effects.json → conditionTerms, never stored. */
    private CombatSnapshot buildCombatSnapshot(GameCharacter c) {
        // Effect chips + HP-threshold conditions are combatant-agnostic (shared with MonsterView).
        var effectViews = combatActions.effectViews(c);
        List<String> conditions = combatActions.conditions(c);

        ensurePools(c); // materialize sub-resource pools on first sight (Story 1.2)
        var poolViews = c.getPools().stream()
                .map(pool -> {
                    var def = gameData.getPoolsForClass(c.getClassId()).stream()
                            .filter(d -> d.id().equals(pool.getPoolId())).findFirst().orElse(null);
                    return new CombatSnapshot.PoolView(pool.getPoolId(),
                            def != null && def.name() != null ? def.name() : pool.getPoolId(),
                            pool.getCurrent(), pool.getMax());
                })
                .toList();

        CombatSnapshot.ResourceView resourceView = null;
        if (c.getResource() != null && c.getResource().getType() != null) {
            Integer derived = statEngine.computeClassResourceMax(c);
            // if/else, not a ternary: a mixed int/Integer conditional unboxes the null branch
            Integer max;
            if (derived == null) {
                max = c.getResource().getMax();
            } else if (derived == StatDerivationEngine.UNBOUNDED_RESOURCE) {
                max = null; // builder resources (focus) have no cap
            } else {
                max = derived;
            }
            resourceView = new CombatSnapshot.ResourceView(
                    c.getResource().getType(), c.getResource().getCurrent(), max);
        }

        boolean downed = c.getLifeStatus() == LifeStatus.DOWNED;
        return new CombatSnapshot(
                c.getName(), c.getLevel(), c.getPathId(), c.getClassId(), c.getSpecializationId(),
                c.getStats().toMap(), c.getStats().modifierMap(),
                new CombatSnapshot.HpView(c.getHp().getCurrent(), statEngine.computeMaxHP(c), c.getHp().getTemp()),
                statEngine.computeAC(c),
                statEngine.computePA(c),
                statEngine.computeMA(c),
                shieldPool(c, "physical-shield"),
                shieldPool(c, "magic-shield"),
                new CombatSnapshot.ApView(c.getAp().getCurrent(), statEngine.computeAPRecovery(c), statEngine.computeMaxAP(c)),
                new CombatSnapshot.ManaView(c.getMana().getCurrent(), statEngine.computeMaxMana(c)),
                resourceView,
                poolViews,
                statEngine.computeSpeed(c), c.getBonusInitiative(), c.getDeathStacks(),
                c.getLifeStatus().name(),
                downed ? c.getDownedRoundsRemaining() : null,
                downed ? statEngine.computeReviveDC(c) : null,
                c.isPendingDeathFight(),
                c.getDownsThisCombat(),
                List.copyOf(c.getSavingThrowProficiencies()),
                List.copyOf(c.getProficiencies()),
                effectViews,
                statEngine.findEquippedWeaponId(c),
                c.getInventory().stream()
                        .filter(InventoryEntry::isEquipped)
                        .filter(e -> {
                            var it = statEngine.resolveItem(c, e.getItemId());
                            return it != null && it.kind() == ItemKind.WEAPON;
                        })
                        .map(InventoryEntry::getItemId)
                        .toList(),
                statEngine.findEquippedArmorId(c),
                conditions,
                deriveProficiencyPenalties(c),
                List.copyOf(c.getTalents()),
                List.copyOf(c.getSpecFeats()),
                Map.copyOf(c.getStatOverrides())
        );
    }

    /**
     * Remaining absorption in a typed shield effect, summed across instances (the Q08
     * exclusivity rule keeps that at one in practice). 0 when no shield is up.
     */
    private int shieldPool(GameCharacter c, String effectId) {
        return c.getActiveEffects().stream()
                .filter(e -> effectId.equals(e.getEffectId()))
                .mapToInt(e -> e.getValue() != null ? e.getValue() : 0)
                .sum();
    }

    /** Q30 (M5-B): equipped items the character isn't proficient with, plus consequences. */
    private List<CombatSnapshot.PenaltyView> deriveProficiencyPenalties(GameCharacter c) {
        var penalties = new java.util.ArrayList<CombatSnapshot.PenaltyView>();
        for (var entry : c.getInventory()) {
            if (!entry.isEquipped()) continue;
            var item = statEngine.resolveItem(c, entry.getItemId());
            if (item == null) continue;
            var kind = item.kind();
            if (kind != com.steelmight.charactersheet.gamedata.ItemKind.WEAPON
                    && kind != com.steelmight.charactersheet.gamedata.ItemKind.ARMOR) continue;
            if (!statEngine.isProficientWith(c, item)) {
                penalties.add(new CombatSnapshot.PenaltyView(item.id(),
                        EquipmentService.penaltyText(item)));
            }
        }
        return penalties;
    }
}
