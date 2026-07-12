package com.steelmight.charactersheet.service;

import com.steelmight.charactersheet.dto.*;
import com.steelmight.charactersheet.engine.*;
import com.steelmight.charactersheet.model.*;
import com.steelmight.charactersheet.gamedata.Dice;
import com.steelmight.charactersheet.gamedata.DiceFormula;
import com.steelmight.charactersheet.gamedata.GameDataProvider;
import com.steelmight.charactersheet.gamedata.ItemKind;
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
    private final EffectApplicationEngine effectEngine;
    private final TurnTickService turnTickService;
    private final GameDataProvider gameData;
    private final RandomSource randomSource;
    private final DeckTemplateService deckTemplates;
    private final EncounterService encounters;

    public CharacterService(CharacterRepository repo,
                            DamageResolutionPipeline damagePipeline,
                            HealingResolutionPipeline healingPipeline,
                            StatDerivationEngine statEngine,
                            EffectApplicationEngine effectEngine,
                            TurnTickService turnTickService,
                            GameDataProvider gameData,
                            RandomSource randomSource,
                            DeckTemplateService deckTemplates,
                            EncounterService encounters) {
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

        repo.save(c);
        return new CharacterCreatedResponse(id, buildCombatSnapshot(c));
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
                        e.isSilvered(), gameData.getItemSpace(e.getItemId()),
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
            // Scrolls hold a specific spell (2026-07-07) — the DM grant path validates it too.
            if (in.spellId() != null && !in.spellId().isBlank()) {
                var item = gameData.findItem(in.itemId());
                if (item == null || item.kind() != com.steelmight.charactersheet.gamedata.ItemKind.SCROLL) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "spellId only applies to scrolls (" + in.itemId() + ")");
                }
                ShopService.validateScrollSpell(gameData, item, in.spellId());
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

    public ActionResponse<CombatSnapshot> damage(String playerId, DamageRequest req) {
        var c = getCharacter(playerId);
        if (req.value() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "damage value must be positive");
        }
        var category = gameData.getDamageCategory(req.damageType());
        if (category == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "unknown damage type: " + req.damageType());
        }
        var tags = (req.tags() != null && !req.tags().isEmpty()) ? req.tags() : List.of("directAttack");
        var event = new DamageEvent(req.value(), req.damageType(), category, tags,
                req.ignoreResistance(), req.sourceId());
        event.setDuringOwnTurn(req.duringOwnTurn());
        event.setAttackerMight(req.attackerMight());
        var result = damagePipeline.resolve(event, c);
        repo.save(c);
        return new ActionResponse<>(result, buildCombatSnapshot(c));
    }

    public ActionResponse<CombatSnapshot> heal(String playerId, HealRequest req) {
        var c = getCharacter(playerId);
        if (req.value() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "heal value must be positive");
        }
        var event = new HealEvent(req.value());
        var result = healingPipeline.resolve(event, c);
        repo.save(c);
        return new ActionResponse<>(result, buildCombatSnapshot(c));
    }

    public ActionResponse<CombatSnapshot> applyEffect(String playerId, ApplyEffectRequest req) {
        var c = getCharacter(playerId);
        var result = effectEngine.apply(c, new EffectApplication(
                req.effectId(), req.source(), req.stacks(), req.value(),
                req.duration(), req.duringOwnTurn(), req.bypassImmunity(), req.replaceExistingShield(),
                req.durationType()));
        repo.save(c);
        return new ActionResponse<>(result, buildCombatSnapshot(c));
    }

    public ActionResponse<CombatSnapshot> removeEffect(String playerId, String effectId) {
        var c = getCharacter(playerId);
        var result = effectEngine.remove(c, effectId);
        repo.save(c);
        return new ActionResponse<>(result, buildCombatSnapshot(c));
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
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown resource: " + req.resource());
                }
            }
        }

        repo.save(c);
        return new ActionResponse<>(result, buildCombatSnapshot(c));
    }

    private static void requireSufficient(String resource, int have, int need) {
        if (have < need) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Insufficient " + resource + ": have " + have + ", need " + need);
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
                spell.apCost().resolve(c.getAp().getMax()));
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
                req.targetPlayerId(), req.applyEffectsToSelf(), new ResolutionResult());
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
        // not cost the caster anything (all-or-nothing).
        GameCharacter effectsTarget = null;
        if (targetPlayerId != null && !targetPlayerId.isBlank()) {
            effectsTarget = targetPlayerId.equals(playerId) ? c : getCharacter(targetPlayerId);
        } else if (Boolean.TRUE.equals(applyEffectsToSelf)) {
            effectsTarget = c;
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

        // Self/party effects (M4-C): the target's own protections resolve in apply().
        // Without a target, the payload carries ready-to-apply details (converted
        // durations) so the table sees exactly what lands on hit.
        if (spell.effects() != null && !spell.effects().isEmpty()) {
            if (effectsTarget != null) {
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
                    repo.save(effectsTarget);
                    result.putPayload("effectsAppliedTo", effectsTarget.getPlayerId());
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

        // Attack-type spells roll their d20 as part of the cast (Guide 1.3/4.1:
        // 1d20 + proficiency + spell stat). Nat 20 = crit, nat 1 = fumble ("cannot
        // be changed by modifiers"); critRange effects are PERCENT chance — base 5%
        // (the 20 face), each 5% widens the range by one face. The DM compares the
        // total to the target's AC until targeting exists (encounter round).
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
            result.putPayload("attackRoll", attackRoll);
            result.addStep("attack-roll",
                    "Spell attack: d20 " + roll + " + " + attackBonus + " = " + (roll + attackBonus)
                            + (criticalHit ? " — CRITICAL" : "")
                            + (fumble ? " — natural 1, automatic miss" : "")
                            + " (vs target AC)",
                    roll, roll + attackBonus);
        }

        // M4-B: roll damage/healing at castAtLevel. Healing rolls are numbers for the
        // DM — Cursed/Decaying only apply when healing is applied via /actions/heal.
        // M4-D (Q24, Shops p.19): the weapon's spellModifier raises the spell-modifier
        // stat inside damage formulas; spellDamage adds flat to the damage roll.
        var spellAttr = statEngine.getSpellcastingAttribute(c);
        int spellMod = spellAttr != null ? c.getStats().modifier(spellAttr) : 0;
        if (spell.damage() != null) {
            var increase = spell.scaling() != null ? spell.scaling().damageIncrease() : null;
            int damageMod = spellMod + (casterWeapon != null ? casterWeapon.spellModifier() : 0);
            int weaponFlat = casterWeapon != null ? casterWeapon.spellDamage() : 0;
            result.putPayload("damage",
                    rollFormula(spell.damage(), increase, upcastSteps, damageMod, weaponFlat,
                            criticalHit, result, "damage"));
        }
        if (spell.healing() != null) {
            var increase = spell.scaling() != null ? spell.scaling().healingIncrease() : null;
            result.putPayload("healing",
                    rollFormula(spell.healing(), increase, upcastSteps, spellMod, 0,
                            false, result, "healing"));
        }

        repo.save(c);
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
                    var it = gameData.findItem(e.getItemId());
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
        var item = gameData.findItem(entry.getItemId());
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
            result.putPayload("attackRoll", attackRoll);
            result.addStep("attack-roll",
                    "Weapon attack: d20 " + natural
                            + (rolls.size() > 1 ? " (rolled " + rolls + (advantage > 0 ? ", advantage" : ", disadvantage") + ")" : "")
                            + " + " + attackBonus + " = " + (natural + attackBonus)
                            + (critical ? " — CRITICAL" : "")
                            + (fumble ? " — natural 1, automatic miss" : "")
                            + " (vs target AC)",
                    natural, natural + attackBonus);

            // damage: dice + flat + per-level scaling (+ stat mod when proficient)
            int weaponLevel = Math.max(1, entry.getUpgradeTier());
            var damageNode = node.path("damage");
            var formula = new DiceFormula(
                    damageNode.path("modMultiplier").asDouble(1),
                    damageNode.path("flat").asInt(0)
                            + node.path("scaling").asInt(0) * (weaponLevel - 1),
                    Dice.from(damageNode.path("dice")));
            result.putPayload("damage", rollFormula(formula, null, 0,
                    proficient ? statMod : 0, 0, critical, result, "damage"));
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
                    c.getAp().getCurrent(), c.getAp().getMax(), v -> c.getAp().setCurrent(v));
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
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown resource: " + req.resource());
                }
            }
        }

        repo.save(c);
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
        encounters.validateAndMarkTurnStart(c);
        // M2-B: DoT ticks (Q13: before AP recovery) → AP recovery → startOfTurn triggers.
        var result = turnTickService.turnStart(c);
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
        String next = encounters.completeTurn(c);
        if (next != null) {
            result.addStep("turn-order", "Turn passes to " + next, 0, 0);
        }
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
        return new ActionResponse<>(result, buildCombatSnapshot(c));
    }

    /** Combat start (M2-D + Q18): AP resets to starting (= recovery), downs counter resets. */
    public ActionResponse<CombatSnapshot> combatStart(String playerId) {
        var c = getCharacter(playerId);
        var result = new ResolutionResult();

        int startingAp = Math.min(c.getAp().getMax(), c.getAp().getRecovery());
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
     *  deathStacks and AP are untouched (AP is combat-scoped, Q18). */
    public ActionResponse<CombatSnapshot> rest(String playerId, RestRequest req) {
        var c = getCharacter(playerId);
        int tier = req != null && req.tier() != null ? req.tier() : 100;
        if (tier != 25 && tier != 50 && tier != 75 && tier != 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "tier must be 25, 50, 75 or 100 (got " + tier + ")");
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

        // 5. N10: ANY rest clears every accumulated threshold stack, regardless of tier.
        var thresholdEffects = c.getActiveEffects().stream()
                .filter(e -> {
                    var def = gameData.getEffect(e.getEffectId());
                    return def != null && def.stackBased() && !def.multiInstance() && def.isNegative();
                })
                .map(ActiveEffect::getEffectId)
                .distinct()
                .toList();
        for (var effectId : thresholdEffects) {
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

        // 8. M5-C: usesPerLongRest item charges restore with the same floor+probability
        //    treatment as charge-style class resources at partial tiers.
        for (var entry : c.getInventory()) {
            var item = gameData.findItem(entry.getItemId());
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

    /** HP-state condition terms (M0-B R4) — pure derivation from effects.json → conditionTerms, never stored. */
    private List<String> deriveConditions(GameCharacter c) {
        int current = c.getHp().getCurrent();
        int max = statEngine.computeMaxHP(c);
        var conditions = new java.util.ArrayList<String>();
        if (current == 0) conditions.add("downed");
        var terms = gameData.getConditionTerms();
        if (terms != null && max > 0) {
            terms.fields().forEachRemaining(entry -> {
                double threshold = entry.getValue().path("threshold").asDouble(0);
                String comparison = entry.getValue().path("comparison").asText("below");
                if ("below".equals(comparison) && current < threshold * max) {
                    conditions.add(entry.getKey());
                }
            });
        }
        return conditions;
    }

    private CombatSnapshot buildCombatSnapshot(GameCharacter c) {
        var effectViews = c.getActiveEffects().stream()
                .map(e -> new CombatSnapshot.EffectView(
                        e.getEffectId(), e.getEffectId(), e.getStacks(),
                        e.getValue(), e.getRemainingRounds()))
                .toList();

        List<String> conditions = deriveConditions(c);

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
                new CombatSnapshot.ApView(c.getAp().getCurrent(), statEngine.computeAPRecovery(c), c.getAp().getMax()),
                new CombatSnapshot.ManaView(c.getMana().getCurrent(), statEngine.computeMaxMana(c)),
                resourceView,
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
                            var it = gameData.findItem(e.getItemId());
                            return it != null && it.kind() == ItemKind.WEAPON;
                        })
                        .map(InventoryEntry::getItemId)
                        .toList(),
                statEngine.findEquippedArmorId(c),
                conditions,
                deriveProficiencyPenalties(c),
                List.copyOf(c.getTalents()),
                List.copyOf(c.getSpecFeats())
        );
    }

    /** Q30 (M5-B): equipped items the character isn't proficient with, plus consequences. */
    private List<CombatSnapshot.PenaltyView> deriveProficiencyPenalties(GameCharacter c) {
        var penalties = new java.util.ArrayList<CombatSnapshot.PenaltyView>();
        for (var entry : c.getInventory()) {
            if (!entry.isEquipped()) continue;
            var item = gameData.findItem(entry.getItemId());
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
