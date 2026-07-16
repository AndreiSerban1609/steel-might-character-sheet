package com.steelmight.charactersheet.gamedata;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.steelmight.charactersheet.engine.DamageCategory;
import com.steelmight.charactersheet.engine.EffectDefinition;
import com.steelmight.charactersheet.engine.EffectMechanic;
import com.steelmight.charactersheet.engine.EffectPolarity;
import com.steelmight.charactersheet.model.DamageType;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Component
public class GameDataProvider {

    private static final Logger log = LoggerFactory.getLogger(GameDataProvider.class);

    private final ObjectMapper objectMapper;
    private final Path dataPath;

    private Map<String, EffectDefinition> effectsById;
    private JsonNode classes;
    private JsonNode classAbilities;
    private JsonNode weapons;
    private JsonNode armor;
    private JsonNode casterWeapons;
    private JsonNode consumables;
    private JsonNode races;
    private JsonNode characterCreation;
    private JsonNode spellcasting;
    private JsonNode pricing;
    private JsonNode skills;
    private JsonNode mounts;
    private JsonNode itemProperties;
    private JsonNode talents;
    private JsonNode specializations;

    /** itemId → inventorySpace, flattened across every purchasable-item source. */
    private Map<String, Double> itemSpaceById;

    /** itemId → resolved catalog entry (M5: findItem across all catalogs). */
    private Map<String, ResolvedItem> itemsById;

    /** DamageType → category (physical/magical/true), from damage-types.json. */
    private Map<DamageType, DamageCategory> damageCategoryByType;

    /** raceId → damage-taken multipliers (races.json damageTaken; 0.5 res / 2.0 vuln). */
    private Map<String, Map<DamageType, Double>> raceDamageTakenById;

    /** effects.json → conditionTerms (injured / severelyInjured thresholds). */
    private JsonNode conditionTerms;

    /** spellId → definition, from all ten spells-*.json files (M4-A). */
    private Map<String, SpellDefinition> spellsById;

    /** talentId → parsed mechanics for the (few) mechanical talents; most are free text. */
    private Map<String, List<EffectMechanic>> talentMechanicsById;

    /** casterWeaponId → typed definition (M4-A; the raw JsonNode stays for the item index). */
    private Map<String, CasterWeaponDefinition> casterWeaponsById;

    /** abilityId → definition, from all six abilities-*.json files (Epic 1 / Story 1.1). */
    private Map<String, AbilityDefinition> abilitiesById;
    private Map<String, List<AbilityDefinition>> abilitiesByClassId;
    private Map<String, List<PoolDefinition>> poolsByClassId;

    public GameDataProvider(ObjectMapper objectMapper,
                            @Value("${game.data-path}") String dataPath) {
        this.objectMapper = objectMapper;
        this.dataPath = Path.of(dataPath);
    }

    @PostConstruct
    public void load() throws IOException {
        log.info("Loading game data from {}", dataPath.toAbsolutePath());

        loadEffects();
        classes = loadFile("classes.json");
        classAbilities = loadFile("class-abilities.json");
        weapons = loadFile("weapons.json");
        armor = loadFile("armor.json");
        casterWeapons = loadFile("caster-weapons.json");
        consumables = loadFile("consumables.json");
        races = loadFile("races.json");
        characterCreation = loadFile("character-creation.json");
        spellcasting = loadFile("spellcasting.json");
        pricing = loadFile("pricing.json");
        skills = loadFile("skills.json");
        mounts = loadFile("mounts.json");
        itemProperties = loadFile("item-properties.json");
        talents = loadFile("talents.json");
        specializations = loadFile("specializations.json");

        buildItemIndex();
        buildResolvedItemIndex();
        loadDamageCategories();
        buildRaceDamageIndex();
        loadSpells();
        loadCasterWeaponDefinitions();
        parseTalentMechanics();
        loadAbilities();

        log.info("Game data loaded: {} effects, classes, weapons, armor, races, {} items, {} damage types, "
                        + "{} spells, {} caster weapons, {} abilities",
                effectsById.size(), itemSpaceById.size(), damageCategoryByType.size(),
                spellsById.size(), casterWeaponsById.size(), abilitiesById.size());
    }

    private static final List<String> SPELL_FILES = List.of(
            "spells-archer.json", "spells-battlemage.json", "spells-corruptor.json",
            "spells-disciple.json", "spells-musician.json", "spells-rogue.json",
            "spells-warrior.json", "spells-wildborn.json", "spells-wizard.json",
            "spells-wraith-hunter.json");

    private void loadSpells() throws IOException {
        spellsById = new LinkedHashMap<>();
        var countByClassId = new TreeMap<String, Integer>();
        for (String filename : SPELL_FILES) {
            JsonNode root = loadFile(filename);
            if (!root.isArray()) {
                log.warn("{} is not a JSON array — skipping", filename);
                continue;
            }
            List<SpellDefinition> spells = objectMapper.convertValue(root, new TypeReference<>() {});
            for (var spell : spells) {
                var previous = spellsById.put(spell.id(), spell);
                if (previous != null) {
                    throw new IllegalStateException("Duplicate spell id '" + spell.id()
                            + "' in " + filename);
                }
                countByClassId.merge(spell.classId(), 1, Integer::sum);
            }
        }
        // Spell classId values are class ids (renamed "class" = old subclass, e.g. "sorcerer").
        log.info("Loaded {} spells across {} classes: {}", spellsById.size(),
                countByClassId.size(), countByClassId);
    }

    private static final List<String> ABILITY_FILES = List.of(
            "abilities-archer.json", "abilities-monk.json", "abilities-rogue.json",
            "abilities-warrior.json", "abilities-wildborn.json", "abilities-wraith-hunter.json");

    private static final Set<String> ABILITY_KINDS =
            Set.of("active", "reaction", "attack-enhancer", "passive");
    private static final Set<String> ABILITY_RESOLUTIONS = Set.of("auto", "manual");

    /** Non-caster class abilities + sub-resource pools (Epic 1). Fails fast on bad data. */
    private void loadAbilities() throws IOException {
        abilitiesById = new LinkedHashMap<>();
        abilitiesByClassId = new LinkedHashMap<>();
        poolsByClassId = new LinkedHashMap<>();
        var countByClassId = new TreeMap<String, Integer>();

        for (String filename : ABILITY_FILES) {
            JsonNode root = loadFile(filename);
            root.path("pools").fields().forEachRemaining(entry -> {
                List<PoolDefinition> defs = objectMapper.convertValue(
                        entry.getValue(), new TypeReference<>() {});
                if (!defs.isEmpty()) poolsByClassId.put(entry.getKey(), List.copyOf(defs));
            });

            List<AbilityDefinition> abilities = objectMapper.convertValue(
                    root.path("abilities"), new TypeReference<>() {});
            for (var ability : abilities) {
                validateAbility(ability, filename);
                var previous = abilitiesById.put(ability.id(), ability);
                if (previous != null) {
                    throw new IllegalStateException("Duplicate ability id '" + ability.id()
                            + "' in " + filename);
                }
                abilitiesByClassId.computeIfAbsent(ability.classId(), k -> new ArrayList<>())
                        .add(ability);
                countByClassId.merge(ability.classId(), 1, Integer::sum);
            }
        }
        abilitiesByClassId.replaceAll((k, v) -> List.copyOf(v));
        log.info("Loaded {} abilities across {} classes ({} pool classes): {}",
                abilitiesById.size(), countByClassId.size(), poolsByClassId.size(), countByClassId);
    }

    private void validateAbility(AbilityDefinition a, String filename) {
        if (a.id() == null || a.id().isBlank()) {
            throw new IllegalStateException(filename + ": ability without id ('" + a.name() + "')");
        }
        String where = filename + "/" + a.id();
        if (a.classId() == null || a.classId().isBlank()) {
            throw new IllegalStateException(where + ": missing classId");
        }
        if (!ABILITY_KINDS.contains(a.kind())) {
            throw new IllegalStateException(where + ": unknown kind '" + a.kind() + "'");
        }
        if (!ABILITY_RESOLUTIONS.contains(a.resolution())) {
            throw new IllegalStateException(where + ": unknown resolution '" + a.resolution() + "'");
        }
        if (a.targetEffect() != null && !effectsById.containsKey(a.targetEffect().effectId())) {
            throw new IllegalStateException(where + ": unknown targetEffect '"
                    + a.targetEffect().effectId() + "'");
        }
        if (a.selfEffect() != null && !effectsById.containsKey(a.selfEffect().effectId())) {
            throw new IllegalStateException(where + ": unknown selfEffect '"
                    + a.selfEffect().effectId() + "'");
        }
    }

    public AbilityDefinition getAbility(String id) {
        return abilitiesById.get(id);
    }

    /** All abilities (actives, reactions, enhancers, choice-passives) for one class. */
    public List<AbilityDefinition> getAbilitiesForClass(String classId) {
        return abilitiesByClassId.getOrDefault(classId, List.of());
    }

    /** Sub-resource pool definitions for one class (empty for classes without pools). */
    public List<PoolDefinition> getPoolsForClass(String classId) {
        return classId != null ? poolsByClassId.getOrDefault(classId, List.of()) : List.of();
    }

    private void loadCasterWeaponDefinitions() {
        casterWeaponsById = new LinkedHashMap<>();
        if (casterWeapons == null || !casterWeapons.isArray()) return;
        List<CasterWeaponDefinition> defs = objectMapper.convertValue(
                casterWeapons, new TypeReference<>() {});
        for (var def : defs) {
            var previous = casterWeaponsById.put(def.id(), def);
            if (previous != null) {
                throw new IllegalStateException("Duplicate caster weapon id '" + def.id() + "'");
            }
        }
    }

    private void loadDamageCategories() throws IOException {
        damageCategoryByType = new HashMap<>();
        JsonNode root = loadFile("damage-types.json");
        root.fields().forEachRemaining(entry -> {
            DamageCategory category = DamageCategory.valueOf(entry.getKey().toUpperCase());
            for (var typeNode : entry.getValue()) {
                try {
                    damageCategoryByType.put(DamageType.valueOf(typeNode.asText().toUpperCase()), category);
                } catch (IllegalArgumentException e) {
                    log.warn("damage-types.json lists unknown damage type '{}'", typeNode.asText());
                }
            }
        });
    }

    /** Flatten every purchasable-item source into an itemId → inventorySpace index. */
    private void buildItemIndex() {
        itemSpaceById = new HashMap<>();
        indexItems(weapons);
        indexItems(armor);
        indexItems(casterWeapons);
        indexItems(mounts);
        if (consumables != null) {
            indexItems(consumables.path("healingPotions").path("sizes"));
            indexItems(consumables.path("magicShopItems"));
            indexItems(consumables.path("scrolls"));
            indexItems(consumables.path("generalShop"));
        }
    }

    /** itemId → catalog entry across every source (M5 shared: findItem). */
    private void buildResolvedItemIndex() {
        itemsById = new LinkedHashMap<>();
        indexResolved(weapons, ItemKind.WEAPON);
        indexResolved(armor, ItemKind.ARMOR);
        indexResolved(casterWeapons, ItemKind.CASTER_WEAPON);
        indexResolved(mounts, ItemKind.MOUNT);
        if (consumables != null) {
            indexResolved(consumables.path("healingPotions").path("sizes"), ItemKind.POTION);
            indexResolved(consumables.path("magicShopItems"), ItemKind.MAGIC_SHOP);
            indexResolved(consumables.path("scrolls"), ItemKind.SCROLL);
            indexResolved(consumables.path("generalShop"), ItemKind.GENERAL);
        }
    }

    private void indexResolved(JsonNode array, ItemKind kind) {
        if (array == null || !array.isArray()) return;
        for (var node : array) {
            String id = node.path("id").asText(null);
            if (id == null || id.isBlank()) continue;
            var item = new ResolvedItem(
                    id,
                    node.path("name").asText(id),
                    kind,
                    node.hasNonNull("priceTier") ? node.path("priceTier").asText() : null,
                    node.hasNonNull("itemLevel") ? node.path("itemLevel").asInt() : null,
                    node.hasNonNull("price") ? node.path("price").asInt() : null,
                    node);
            var previous = itemsById.put(id, item);
            if (previous != null) {
                throw new IllegalStateException("Duplicate item id '" + id + "' ("
                        + previous.kind() + " vs " + kind + ")");
            }
        }
    }

    private void indexItems(JsonNode array) {
        if (array == null || !array.isArray()) return;
        for (var item : array) {
            String id = item.path("id").asText(null);
            if (id == null || id.isBlank()) continue;
            // Items without an explicit inventorySpace (armor, caster weapons) default to 1 slot.
            double space = item.has("inventorySpace") ? item.path("inventorySpace").asDouble(1.0) : 1.0;
            itemSpaceById.put(id, space);
        }
    }

    private void loadEffects() throws IOException {
        effectsById = new HashMap<>();
        JsonNode root = loadFile("effects.json");

        List<EffectDefinition> negative = objectMapper.convertValue(
                root.get("negative"), new TypeReference<>() {});
        List<EffectDefinition> positive = objectMapper.convertValue(
                root.get("positive"), new TypeReference<>() {});

        for (var e : negative) effectsById.put(e.id(), e.withPolarity(EffectPolarity.NEGATIVE));
        for (var e : positive) effectsById.put(e.id(), e.withPolarity(EffectPolarity.POSITIVE));

        conditionTerms = root.path("conditionTerms");

        log.info("Loaded {} negative + {} positive effects", negative.size(), positive.size());
    }

    private JsonNode loadFile(String filename) throws IOException {
        Path file = dataPath.resolve(filename);
        if (!Files.exists(file)) {
            log.warn("Game data file not found: {}", file);
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(Files.readString(file));
    }

    public EffectDefinition getEffect(String effectId) {
        return effectsById.get(effectId);
    }

    public SpellDefinition getSpell(String spellId) {
        return spellsById.get(spellId);
    }

    public Map<String, SpellDefinition> getAllSpells() {
        return spellsById;
    }

    public CasterWeaponDefinition getCasterWeapon(String casterWeaponId) {
        return casterWeaponsById.get(casterWeaponId);
    }

    public Map<String, EffectDefinition> getAllEffects() {
        return effectsById;
    }

    public JsonNode getClasses() { return classes; }
    public JsonNode getClassAbilities() { return classAbilities; }
    public JsonNode getWeapons() { return weapons; }
    public JsonNode getArmor() { return armor; }
    public JsonNode getCasterWeapons() { return casterWeapons; }
    public JsonNode getConsumables() { return consumables; }
    public JsonNode getRaces() { return races; }
    public JsonNode getCharacterCreation() { return characterCreation; }
    public JsonNode getSpellcasting() { return spellcasting; }
    public JsonNode getPricing() { return pricing; }
    public JsonNode getSkills() { return skills; }
    public JsonNode getMounts() { return mounts; }
    public JsonNode getItemProperties() { return itemProperties; }
    public JsonNode getTalents() { return talents; }
    public JsonNode getSpecializations() { return specializations; }

    /** talents.json entry by id; null when unknown. */
    public JsonNode getTalent(String talentId) {
        if (talents == null || !talents.isArray()) return null;
        for (var t : talents) {
            if (t.path("id").asText().equals(talentId)) return t;
        }
        return null;
    }

    /** Mechanical talents carry a mechanics array like effects do (glass-cannon's
     *  crit range / armor overrides); parsed once so the stat engine can read them. */
    private void parseTalentMechanics() {
        talentMechanicsById = new HashMap<>();
        if (talents == null || !talents.isArray()) return;
        for (var t : talents) {
            var mechanics = t.path("mechanics");
            if (!mechanics.isArray() || mechanics.isEmpty()) continue;
            List<EffectMechanic> parsed = objectMapper.convertValue(mechanics, new TypeReference<>() {});
            talentMechanicsById.put(t.path("id").asText(), parsed);
        }
        if (!talentMechanicsById.isEmpty()) {
            log.info("Parsed mechanics for {} talents: {}", talentMechanicsById.size(),
                    talentMechanicsById.keySet());
        }
    }

    /** Parsed mechanics for a talent id; empty for free-text talents. */
    public List<EffectMechanic> getTalentMechanics(String talentId) {
        var mechanics = talentMechanicsById.get(talentId);
        return mechanics != null ? mechanics : List.of();
    }

    /** kebab-case identifier for named-but-id-less data (specializations, spec talents/feats). */
    public static String slug(String name) {
        return name == null ? "" : name.trim().toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }

    /** A class's specialization matched by slug(name); null when unknown. */
    public JsonNode findSpecialization(String classId, String specializationId) {
        if (specializations == null || specializationId == null) return null;
        var list = specializations.path(classId);
        if (!list.isArray()) return null;
        String wanted = slug(specializationId);
        for (var spec : list) {
            if (slug(spec.path("name").asText()).equals(wanted)) return spec;
        }
        return null;
    }

    /** Catalog entry for an item id across all sources; null when unknown. */
    public ResolvedItem findItem(String itemId) {
        return itemId != null ? itemsById.get(itemId) : null;
    }

    /** inventorySpace for an item id; unknown items count as 1 slot. */
    public double getItemSpace(String itemId) {
        Double space = itemSpaceById.get(itemId);
        return space != null ? space : 1.0;
    }

    public boolean isKnownItem(String itemId) {
        return itemSpaceById.containsKey(itemId);
    }

    /** Racial damage-taken multipliers (races.json damageTaken); Dragonborn's parent
     *  element is a per-character choice and intentionally not in the data yet. */
    private void buildRaceDamageIndex() {
        raceDamageTakenById = new HashMap<>();
        // races.json root is { _note, races: [...] }.
        var list = races != null ? races.path("races") : null;
        if (list == null || !list.isArray()) return;
        for (var race : list) {
            var node = race.path("damageTaken");
            if (!node.isObject()) continue;
            var map = new HashMap<DamageType, Double>();
            node.fields().forEachRemaining(entry -> {
                try {
                    map.put(DamageType.valueOf(entry.getKey().toUpperCase()), entry.getValue().asDouble());
                } catch (IllegalArgumentException e) {
                    log.warn("races.json damageTaken lists unknown damage type '{}'", entry.getKey());
                }
            });
            raceDamageTakenById.put(race.path("id").asText(), map);
        }
    }

    /** Category per damage-types.json; null if the type is missing from the file. */
    public DamageCategory getDamageCategory(DamageType type) {
        return damageCategoryByType.get(type);
    }

    /** Racial damage-taken multipliers for a race id; empty map when none. */
    public Map<DamageType, Double> getRaceDamageTaken(String raceId) {
        var map = raceId != null ? raceDamageTakenById.get(raceId) : null;
        return map != null ? map : Map.of();
    }

    public JsonNode getConditionTerms() {
        return conditionTerms;
    }
}
