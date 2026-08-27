package com.steelmight.charactersheet.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.steelmight.charactersheet.gamedata.CustomItemNodes;
import com.steelmight.charactersheet.gamedata.GameDataProvider;
import com.steelmight.charactersheet.gamedata.ItemKind;
import com.steelmight.charactersheet.gamedata.ResolvedItem;
import com.steelmight.charactersheet.model.AbilityScore;
import com.steelmight.charactersheet.model.ActiveEffect;
import com.steelmight.charactersheet.model.CasterType;
import com.steelmight.charactersheet.model.Combatant;
import com.steelmight.charactersheet.model.GameCharacter;
import com.steelmight.charactersheet.model.OverridableStat;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;

@Component
public class StatDerivationEngine {

    private final GameDataProvider gameData;

    public StatDerivationEngine(GameDataProvider gameData) {
        this.gameData = gameData;
    }

    // ---- GM stat overrides (demo feedback #11/#12) ----

    /**
     * The GM's pinned value for a stat, or {@code derived} when none is set. Call this on
     * the FORMULA result and before effect modifiers, so an override swaps out the rules
     * the app doesn't model while the live combat layer keeps working on top.
     */
    private int overrideOr(Combatant combatant, OverridableStat stat, int derived) {
        Integer pinned = combatant.overrideFor(stat);
        return pinned != null ? pinned : derived;
    }

    /**
     * Lazy twin: the derivation only runs when nothing is pinned. This is the seam that
     * lets monsters through (ADR-001) — a monster's authored stat block answers
     * {@code overrideFor} for every combat stat, so the class/race/equipment derivation
     * (which only a {@link GameCharacter} can do) is never asked.
     */
    private int overrideOr(Combatant combatant, OverridableStat stat, IntSupplier derived) {
        Integer pinned = combatant.overrideFor(stat);
        return pinned != null ? pinned : derived.getAsInt();
    }

    /**
     * The ONE place a Combatant is narrowed back to a GameCharacter: derived base stats
     * need class/race/equipment data that only players carry. Reached only when nothing
     * authored answered first, so a monster landing here is a data bug, not a branch.
     */
    private static GameCharacter asCharacter(Combatant combatant, String what) {
        if (combatant instanceof GameCharacter character) return character;
        throw new IllegalStateException(combatant.getCombatantId() + " has no authored "
                + what + " and is not a character — cannot derive it");
    }

    // ---- Core modifier resolution ----

    public int resolveModifiedStat(Combatant character, ModifiableStat stat, int baseValue) {
        var modifiers = collectStatModifiers(character, stat);
        if (modifiers.isEmpty()) return baseValue;

        var overrides = modifiers.stream().filter(e -> e.mechanic.override()).toList();
        if (!overrides.isEmpty()) {
            return overrides.stream()
                    .mapToInt(e -> effectiveValue(e.mechanic, e.effect))
                    .min().orElse(baseValue);
        }

        int result = baseValue;
        for (var entry : modifiers) {
            if (entry.mechanic.multiplier() == null) {
                result += effectiveValue(entry.mechanic, entry.effect);
            }
        }
        for (var entry : modifiers) {
            if (entry.mechanic.multiplier() != null) {
                result = (int) (result * entry.mechanic.multiplier());
            }
        }
        return Math.max(0, result);
    }

    private record StatModEntry(EffectMechanic mechanic, ActiveEffect effect) {}

    /** Placeholder row for talent-sourced mechanics — talents have no stacks/value. */
    private static final ActiveEffect TALENT_SOURCE = new ActiveEffect("talent", "talent", 1, null, null, 0);

    private List<StatModEntry> collectStatModifiers(Combatant combatant, ModifiableStat stat) {
        // ActiveMechanics handles dormancy (M0-A), application tiers (M2-B), and
        // composite expansion (M2-C) — a stunned character gets exposed/poisoned
        // mechanics without those rows existing.
        var result = new ArrayList<StatModEntry>();
        int threshold = computeStackThreshold(combatant);
        for (var hit : ActiveMechanics.collect(combatant, gameData, threshold, MechanicType.STAT_MODIFIER)) {
            if (hit.mechanic().stat() == stat) {
                result.add(new StatModEntry(hit.mechanic(), hit.effect()));
            }
        }
        // Mechanical talents (glass-cannon's crit/armor rules) are permanent
        // stat-modifier sources — talents.json carries their mechanics arrays.
        // Players only: monsters have no talents (the one type check the effect layer allows).
        if (combatant instanceof GameCharacter character) {
            for (var talentId : character.getTalents()) {
                for (var mechanic : gameData.getTalentMechanics(talentId)) {
                    if (mechanic.type() == MechanicType.STAT_MODIFIER && mechanic.stat() == stat) {
                        result.add(new StatModEntry(mechanic, TALENT_SOURCE));
                    }
                }
            }
        }
        return result;
    }

    // ---- Threshold system ----

    private static final String EXPECTED_THRESHOLD_FORMULA = "ceil(level / 2)";

    /** Stack threshold for negative effects: character-creation.json → stackThreshold.player = ceil(level/2). */
    public int computeStackThreshold(Combatant character) {
        // Ruling E2: an authored threshold (boss stat block) replaces the formula outright.
        Integer authored = character.authoredStackThreshold();
        if (authored != null) return Math.max(1, authored);

        String formula = gameData.getCharacterCreation()
                .path("stackThreshold").path("player").asText(EXPECTED_THRESHOLD_FORMULA);
        if (!EXPECTED_THRESHOLD_FORMULA.equals(formula)) {
            // The data carries the formula as a string; we implement the known one and refuse to guess.
            throw new IllegalStateException("Unsupported stackThreshold formula in character-creation.json: "
                    + formula + " — engine implements only '" + EXPECTED_THRESHOLD_FORMULA + "'");
        }
        return (character.getLevel() + 1) / 2; // integer ceil(level/2)
    }

    private int effectiveValue(EffectMechanic mechanic, ActiveEffect effect) {
        int raw;
        if (mechanic.valueFromStacks()) {
            if (effect.getValue() != null) {
                // hasValue effects (mana-cost-reduction) carry their magnitude in value,
                // not stacks — same convention as EffectMechanic.resolveValue.
                raw = effect.getValue();
            } else {
                int perStack = mechanic.value() != null ? mechanic.value() : 1;
                raw = perStack * effect.getStacks();
            }
        } else {
            raw = mechanic.value() != null ? mechanic.value() : 0;
        }
        return mechanic.negate() ? -raw : raw;
    }

    // ---- Equipment lookups ----

    /**
     * Catalog node for an item id, or the character's own custom item rendered into the
     * same shape (demo feedback #19). Every equipment lookup goes through here, so custom
     * gear participates in AC/PA/MA, attacks and penalties without any downstream branch.
     */
    public JsonNode itemNode(GameCharacter character, String itemId) {
        var custom = character.customItem(itemId);
        if (custom != null) return CustomItemNodes.toNode(custom);
        var resolved = gameData.findItem(itemId);
        return resolved != null ? resolved.node() : null;
    }

    /** Resolved entry (kind + pricing + node) honoring the character's custom items. */
    public ResolvedItem resolveItem(GameCharacter character, String itemId) {
        var custom = character.customItem(itemId);
        if (custom != null) return CustomItemNodes.resolve(custom);
        return gameData.findItem(itemId);
    }

    private JsonNode findEquippedArmorNode(GameCharacter character, boolean wantShield) {
        for (var item : character.getInventory()) {
            if (!item.isEquipped()) continue;
            var resolved = resolveItem(character, item.getItemId());
            if (resolved == null || resolved.kind() != ItemKind.ARMOR) continue;
            boolean isShield = "shield".equals(resolved.node().path("type").asText());
            if (isShield == wantShield) return resolved.node();
        }
        return null;
    }

    private JsonNode findEquippedBodyArmor(GameCharacter character) {
        return findEquippedArmorNode(character, false);
    }

    private JsonNode findEquippedShield(GameCharacter character) {
        return findEquippedArmorNode(character, true);
    }

    public JsonNode findEquippedWeapon(GameCharacter character) {
        for (var item : character.getInventory()) {
            if (!item.isEquipped()) continue;
            var resolved = resolveItem(character, item.getItemId());
            if (resolved != null && resolved.kind() == ItemKind.WEAPON) return resolved.node();
        }
        return null;
    }

    private JsonNode getClassData(GameCharacter character) {
        if (character.getClassId() == null) return null;
        var abilities = gameData.getClassAbilities();
        if (abilities == null) return null;
        return abilities.path(character.getClassId());
    }

    public String findEquippedArmorId(GameCharacter character) {
        var armor = findEquippedBodyArmor(character);
        return armor != null ? armor.path("id").asText(null) : null;
    }

    /** The equipped caster weapon (spellbook/orb/wand/staff), or null (M4-D). */
    public com.steelmight.charactersheet.gamedata.CasterWeaponDefinition findEquippedCasterWeapon(
            GameCharacter character) {
        for (var item : character.getInventory()) {
            if (!item.isEquipped()) continue;
            var def = gameData.getCasterWeapon(item.getItemId());
            if (def != null) return def;
        }
        return null;
    }

    /**
     * Q30 (M5-B): item proficiency. proficientClasses mixes class AND path ids in the
     * data ("warrior"/"archer" are paths, "bard"/"paladin" are classes) — accept either;
     * armor additionally honors the path's armorProficiencies list in classes.json.
     * Items without proficiency data (caster weapons, consumables) count as proficient.
     */
    public boolean isProficientWith(GameCharacter character,
                                    com.steelmight.charactersheet.gamedata.ResolvedItem item) {
        var profs = item.node().path("proficientClasses");
        if (!profs.isArray()) return true;
        for (var p : profs) {
            String id = p.asText();
            if ("all".equals(id) || id.equals(character.getClassId()) || id.equals(character.getPathId())) {
                return true;
            }
        }
        if (item.kind() == com.steelmight.charactersheet.gamedata.ItemKind.ARMOR) {
            var paths = gameData.getClasses();
            if (paths != null && paths.isArray()) {
                for (var path : paths) {
                    if (!path.path("id").asText().equals(character.getPathId())) continue;
                    for (var armorId : path.path("armorProficiencies")) {
                        if (armorId.asText().equals(item.id())) return true;
                    }
                }
            }
        }
        return false;
    }

    /** True when any equipped armor/shield lacks proficiency — blocks casting (Q30, M5-B). */
    public boolean hasNonProficientArmorEquipped(GameCharacter character) {
        for (var entry : character.getInventory()) {
            if (!entry.isEquipped()) continue;
            var item = resolveItem(character, entry.getItemId());
            if (item == null || item.kind() != ItemKind.ARMOR) continue;
            if (!isProficientWith(character, item)) return true;
        }
        return false;
    }

    public String findEquippedWeaponId(GameCharacter character) {
        var weapon = findEquippedWeapon(character);
        return weapon != null ? weapon.path("id").asText(null) : null;
    }

    // ---- AC / PA / MA ----

    // Combat stats take a Combatant: an authored/pinned value answers first; otherwise
    // the derivation runs against the character's class/race/equipment (asCharacter).

    public int computeAC(Combatant combatant) {
        int baseAC = overrideOr(combatant, OverridableStat.AC, () -> deriveAC(asCharacter(combatant, "AC")));
        return resolveModifiedStat(combatant, ModifiableStat.AC, baseAC);
    }

    private int deriveAC(GameCharacter character) {
        int dexMod = character.getStats().modifier(AbilityScore.DEX);
        int baseAC;

        var bodyArmor = findEquippedBodyArmor(character);
        if (bodyArmor != null) {
            var acNode = bodyArmor.path("ac");
            baseAC = acNode.path("base").asInt(10);
            if (acNode.path("dexMod").asBoolean(false)) {
                int mult = acNode.path("dexMultiplier").asInt(1);
                baseAC += dexMod * mult;
            }
            var bonusLevels = bodyArmor.path("acBonusLevels");
            int perLevel = bodyArmor.path("acBonusPerLevel").asInt(0);
            if (bonusLevels.isArray()) {
                for (var lvl : bonusLevels) {
                    if (character.getLevel() >= lvl.asInt()) baseAC += perLevel;
                }
            }
        } else {
            baseAC = 10 + dexMod;
        }

        var shield = findEquippedShield(character);
        if (shield != null) {
            baseAC += shield.path("ac").path("base").asInt(0);
        }
        return baseAC;
    }

    public int computePA(Combatant combatant) {
        int basePA = overrideOr(combatant, OverridableStat.PA, () -> derivePA(asCharacter(combatant, "PA")));
        return resolveModifiedStat(combatant, ModifiableStat.PA, basePA);
    }

    private int derivePA(GameCharacter character) {
        var bodyArmor = findEquippedBodyArmor(character);
        if (bodyArmor == null) return 0;
        int pa = bodyArmor.path("pa").asInt(0);
        int scaling = bodyArmor.path("paScaling").asInt(0);
        return pa + scaling * (character.getLevel() - 1);
    }

    public int computeMA(Combatant combatant) {
        int baseMA = overrideOr(combatant, OverridableStat.MA, () -> deriveMA(asCharacter(combatant, "MA")));
        return resolveModifiedStat(combatant, ModifiableStat.MA, baseMA);
    }

    private int deriveMA(GameCharacter character) {
        var bodyArmor = findEquippedBodyArmor(character);
        if (bodyArmor == null) return 0;
        int ma = bodyArmor.path("ma").asInt(0);
        int scaling = bodyArmor.path("maScaling").asInt(0);
        return ma + scaling * (character.getLevel() - 1);
    }

    // ---- HP / Mana ----

    // (hpPerLevel + 3 * CON_mod) * level
    public int computeMaxHP(Combatant combatant) {
        return overrideOr(combatant, OverridableStat.MAX_HP, () -> deriveMaxHP(asCharacter(combatant, "max HP")));
    }

    private int deriveMaxHP(GameCharacter character) {
        var classData = getClassData(character);
        int hpPerLevel = (classData != null && classData.has("hpPerLevel"))
                ? classData.path("hpPerLevel").asInt(20) : 20;
        int conMod = character.getStats().modifier(AbilityScore.CON);
        return (hpPerLevel + 3 * conMod) * character.getLevel();
    }

    public int computeMaxMana(GameCharacter character) {
        // Checked before the non-caster early-returns: pinning mana onto a class that has
        // none is exactly the case the escape hatch exists for.
        Integer pinned = character.overrideFor(OverridableStat.MAX_MANA);
        if (pinned != null) return pinned;

        var classData = getClassData(character);
        if (classData == null) return 0;
        int manaPerLevel = classData.path("manaPerLevel").asInt(0);
        if (manaPerLevel == 0) return 0;

        int base = manaPerLevel * character.getLevel();

        // milestone mana bonuses at proficiency breakpoints [5, 9, 13, 17]
        var increases = classData.path("manaIncreases");
        if (increases.isArray()) {
            int[] milestones = {5, 9, 13, 17};
            for (int i = 0; i < Math.min(increases.size(), milestones.length); i++) {
                if (character.getLevel() >= milestones[i]) {
                    base += increases.get(i).asInt(0);
                }
            }
        }
        return base;
    }

    // ---- Movement / AP ----

    public int computeSpeed(Combatant combatant) {
        return resolveModifiedStat(combatant, ModifiableStat.SPEED,
                overrideOr(combatant, OverridableStat.SPEED, combatant.getSpeed()));
    }

    /** Requires an AP economy ({@code getAp() != null}) unless the value is pinned. */
    public int computeAPRecovery(Combatant combatant) {
        return resolveModifiedStat(combatant, ModifiableStat.AP_RECOVERY,
                overrideOr(combatant, OverridableStat.AP_RECOVERY, () -> combatant.getAp().getRecovery()));
    }

    /** AP ceiling; the model stores it, so the override simply replaces the stored value. */
    public int computeMaxAP(Combatant combatant) {
        return overrideOr(combatant, OverridableStat.MAX_AP, () -> combatant.getAp().getMax());
    }

    // ---- Proficiency ----

    public int computeProficiencyBonus(Combatant character) {
        var progression = gameData.getSpellcasting().path("proficiencyProgression");
        int level = character.getLevel();
        if (progression.isArray() && level > 0 && level <= progression.size()) {
            return progression.get(level - 1).asInt(2);
        }
        return 2;
    }

    // ---- Spellcasting ----

    /** class-abilities.json → casterType (major | minor | none); NONE when absent. */
    public CasterType getCasterType(GameCharacter character) {
        var classData = getClassData(character);
        if (classData == null) return CasterType.NONE;
        String type = classData.path("casterType").asText(null);
        if (type == null) return CasterType.NONE;
        try {
            return CasterType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Unknown casterType '" + type
                    + "' for class '" + character.getClassId() + "'");
        }
    }

    public AbilityScore getSpellcastingAttribute(GameCharacter character) {
        var classData = getClassData(character);
        if (classData == null) return null;
        String spellStat = classData.path("spellStat").asText(null);
        if (spellStat == null) return null;
        return parseAbilityScore(spellStat);
    }

    // 8 + proficiency + spellStat modifier + effects
    public int computeSpellSaveDC(GameCharacter character) {
        var attr = getSpellcastingAttribute(character);
        if (attr == null) return 0;
        int base = 8 + computeProficiencyBonus(character) + character.getStats().modifier(attr);
        return resolveModifiedStat(character, ModifiableStat.SPELL_DC, base);
    }

    // proficiency + spellStat modifier + effects
    public int computeSpellAttackBonus(GameCharacter character) {
        var attr = getSpellcastingAttribute(character);
        if (attr == null) return 0;
        int base = computeProficiencyBonus(character) + character.getStats().modifier(attr);
        return resolveModifiedStat(character, ModifiableStat.ATTACK_BONUS, base);
    }

    // ---- Class resources (M3 Part A) ----

    /** Sentinel: the resource is a combat-scoped builder with no maximum (martyr focus). */
    public static final int UNBOUNDED_RESOURCE = Integer.MAX_VALUE;

    /** The class's non-mana resource type id (rages, chakra, focus…); null for mana casters and resourceless classes. */
    public String getClassResourceType(GameCharacter character) {
        var classData = getClassData(character);
        if (classData == null) return null;
        String type = classData.path("resourceType").asText(null);
        return (type == null || "mana".equals(type)) ? null : type;
    }

    /**
     * Derived class-resource maximum (N8: tables are authoritative over prose):
     * - resourcePerLevel / sacredEnergyPerLevel tables → value at [level-1]
     * - focus (martyr) → {@link #UNBOUNDED_RESOURCE} (combat-scoped builder, no max)
     * - shapeshiftHp → floor(maxHP/2), ×2 at level 20 (Metamorph)
     * - mana casters / no resource → null (mana derives via computeMaxMana)
     */
    public Integer computeClassResourceMax(GameCharacter character) {
        String type = getClassResourceType(character);
        if (type == null) return null;
        var classData = getClassData(character);
        int level = Math.max(1, character.getLevel());

        if ("focus".equals(type)) return UNBOUNDED_RESOURCE;
        if ("shapeshiftHp".equals(type)) {
            int pool = computeMaxHP(character) / 2;
            return character.getLevel() >= 20 ? pool * 2 : pool;
        }

        var table = classData.path("resourcePerLevel");
        if (!table.isArray()) table = classData.path("sacredEnergyPerLevel");
        if (table.isArray() && table.size() >= level) {
            return table.get(level - 1).asInt();
        }
        // Data gap — the type exists but no progression table; treat as underived.
        return null;
    }

    /** Builders accumulate in play and reset to 0 on rest (focus; sorcerer empowerment is play-state). */
    public boolean isBuilderResource(String resourceType) {
        return "focus".equals(resourceType) || "empowerment".equals(resourceType);
    }

    /** Charge-style counts (rages, curses) restore with floor + probability at partial rest tiers (Q19). */
    public boolean isChargeStyleResource(String resourceType) {
        return "rages".equals(resourceType) || "curses".equals(resourceType);
    }

    // ---- Death & dying (M2-D) ----

    /** Medicine-check revive DC (N11b): 3 + ceil(level/2), +2 per additional down this combat. */
    public int computeReviveDC(GameCharacter character) {
        int escalation = Math.max(0, character.getDownsThisCombat() - 1);
        return 3 + (character.getLevel() + 1) / 2 + 2 * escalation;
    }

    // ---- Inventory ----

    // Q33: carrying capacity, in inventory-space slots, = 10 + 2 * STR modifier.
    public int computeCarryCapacity(GameCharacter character) {
        return overrideOr(character, OverridableStat.CARRY_CAPACITY,
                10 + 2 * character.getStats().modifier(AbilityScore.STR));
    }

    /** inventorySpace for an id, honoring the character's own custom gear. */
    public double itemSpace(GameCharacter character, String itemId) {
        var custom = character.customItem(itemId);
        return custom != null ? custom.getInventorySpace() : gameData.getItemSpace(itemId);
    }

    /** Sum of inventorySpace * quantity across every carried item (rounded to 2 dp). */
    public double computeCarriedSpace(GameCharacter character) {
        double total = 0;
        for (var item : character.getInventory()) {
            total += itemSpace(character, item.getItemId()) * item.getQuantity();
        }
        return Math.round(total * 100.0) / 100.0;
    }

    // ---- Weapon ----

    public int computeWeaponApCost(GameCharacter character) {
        var weapon = findEquippedWeapon(character);
        int baseCost = weapon != null ? weapon.path("apCost").asInt(3) : 3;
        return Math.max(1, resolveModifiedStat(character, ModifiableStat.WEAPON_AP_COST, baseCost));
    }

    // ---- Utility ----

    private AbilityScore parseAbilityScore(String key) {
        return switch (key.toLowerCase()) {
            case "str" -> AbilityScore.STR;
            case "dex" -> AbilityScore.DEX;
            case "con", "const" -> AbilityScore.CON;
            case "int" -> AbilityScore.INT;
            case "wis" -> AbilityScore.WIS;
            case "will" -> AbilityScore.WILL;
            case "cha" -> AbilityScore.CHA;
            default -> null;
        };
    }
}
