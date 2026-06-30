package com.steelmight.charactersheet.gamedata;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.steelmight.charactersheet.engine.EffectDefinition;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    /** itemId → inventorySpace, flattened across every purchasable-item source. */
    private Map<String, Double> itemSpaceById;

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

        buildItemIndex();

        log.info("Game data loaded: {} effects, classes, weapons, armor, races, {} items",
                effectsById.size(), itemSpaceById.size());
    }

    /** Flatten every purchasable-item source into an itemId → inventorySpace index. */
    private void buildItemIndex() {
        itemSpaceById = new HashMap<>();
        indexItems(weapons);
        indexItems(armor);
        indexItems(casterWeapons);
        if (consumables != null) {
            indexItems(consumables.path("healingPotions").path("sizes"));
            indexItems(consumables.path("magicShopItems"));
            indexItems(consumables.path("scrolls"));
            indexItems(consumables.path("generalShop"));
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

        for (var e : negative) effectsById.put(e.id(), e);
        for (var e : positive) effectsById.put(e.id(), e);

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

    /** inventorySpace for an item id; unknown items count as 1 slot. */
    public double getItemSpace(String itemId) {
        Double space = itemSpaceById.get(itemId);
        return space != null ? space : 1.0;
    }

    public boolean isKnownItem(String itemId) {
        return itemSpaceById.containsKey(itemId);
    }
}
