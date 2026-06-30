package com.steelmight.charactersheet.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.steelmight.charactersheet.gamedata.GameDataProvider;
import com.steelmight.charactersheet.model.AbilityScore;
import com.steelmight.charactersheet.model.ActiveEffect;
import com.steelmight.charactersheet.model.GameCharacter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class StatDerivationEngine {

    private final GameDataProvider gameData;

    public StatDerivationEngine(GameDataProvider gameData) {
        this.gameData = gameData;
    }

    // ---- Core modifier resolution ----

    public int resolveModifiedStat(GameCharacter character, ModifiableStat stat, int baseValue) {
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

    private List<StatModEntry> collectStatModifiers(GameCharacter character, ModifiableStat stat) {
        var result = new ArrayList<StatModEntry>();
        for (var active : character.getActiveEffects()) {
            var def = gameData.getEffect(active.getEffectId());
            if (def == null) continue;
            for (var mech : def.mechanicsOfType(MechanicType.STAT_MODIFIER)) {
                if (mech.stat() == stat) {
                    result.add(new StatModEntry(mech, active));
                }
            }
        }
        return result;
    }

    private int effectiveValue(EffectMechanic mechanic, ActiveEffect effect) {
        int raw;
        if (mechanic.valueFromStacks()) {
            int perStack = mechanic.value() != null ? mechanic.value() : 1;
            raw = perStack * effect.getStacks();
        } else {
            raw = mechanic.value() != null ? mechanic.value() : 0;
        }
        return mechanic.negate() ? -raw : raw;
    }

    // ---- Equipment lookups ----

    private JsonNode findEquippedBodyArmor(GameCharacter character) {
        var armor = gameData.getArmor();
        if (armor == null || !armor.isArray()) return null;
        for (var item : character.getInventory()) {
            if (!item.isEquipped()) continue;
            for (var entry : armor) {
                if (entry.path("id").asText().equals(item.getItemId())
                        && !"shield".equals(entry.path("type").asText())) {
                    return entry;
                }
            }
        }
        return null;
    }

    private JsonNode findEquippedShield(GameCharacter character) {
        var armor = gameData.getArmor();
        if (armor == null || !armor.isArray()) return null;
        for (var item : character.getInventory()) {
            if (!item.isEquipped()) continue;
            for (var entry : armor) {
                if (entry.path("id").asText().equals(item.getItemId())
                        && "shield".equals(entry.path("type").asText())) {
                    return entry;
                }
            }
        }
        return null;
    }

    public JsonNode findEquippedWeapon(GameCharacter character) {
        var weapons = gameData.getWeapons();
        if (weapons == null || !weapons.isArray()) return null;
        for (var item : character.getInventory()) {
            if (!item.isEquipped()) continue;
            for (var entry : weapons) {
                if (entry.path("id").asText().equals(item.getItemId())) {
                    return entry;
                }
            }
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

    public String findEquippedWeaponId(GameCharacter character) {
        var weapon = findEquippedWeapon(character);
        return weapon != null ? weapon.path("id").asText(null) : null;
    }

    // ---- AC / PA / MA ----

    public int computeAC(GameCharacter character) {
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

        return resolveModifiedStat(character, ModifiableStat.AC, baseAC);
    }

    public int computePA(GameCharacter character) {
        int basePA = 0;
        var bodyArmor = findEquippedBodyArmor(character);
        if (bodyArmor != null) {
            int pa = bodyArmor.path("pa").asInt(0);
            int scaling = bodyArmor.path("paScaling").asInt(0);
            basePA = pa + scaling * (character.getLevel() - 1);
        }
        return resolveModifiedStat(character, ModifiableStat.PA, basePA);
    }

    public int computeMA(GameCharacter character) {
        int baseMA = 0;
        var bodyArmor = findEquippedBodyArmor(character);
        if (bodyArmor != null) {
            int ma = bodyArmor.path("ma").asInt(0);
            int scaling = bodyArmor.path("maScaling").asInt(0);
            baseMA = ma + scaling * (character.getLevel() - 1);
        }
        return resolveModifiedStat(character, ModifiableStat.MA, baseMA);
    }

    // ---- HP / Mana ----

    // (hpPerLevel + 3 * CON_mod) * level
    public int computeMaxHP(GameCharacter character) {
        var classData = getClassData(character);
        int hpPerLevel = (classData != null && classData.has("hpPerLevel"))
                ? classData.path("hpPerLevel").asInt(20) : 20;
        int conMod = character.getStats().modifier(AbilityScore.CON);
        return (hpPerLevel + 3 * conMod) * character.getLevel();
    }

    public int computeMaxMana(GameCharacter character) {
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

    public int computeSpeed(GameCharacter character) {
        return resolveModifiedStat(character, ModifiableStat.SPEED, character.getSpeed());
    }

    public int computeAPRecovery(GameCharacter character) {
        return resolveModifiedStat(character, ModifiableStat.AP_RECOVERY,
                character.getAp().getRecovery());
    }

    // ---- Proficiency ----

    public int computeProficiencyBonus(GameCharacter character) {
        var progression = gameData.getSpellcasting().path("proficiencyProgression");
        int level = character.getLevel();
        if (progression.isArray() && level > 0 && level <= progression.size()) {
            return progression.get(level - 1).asInt(2);
        }
        return 2;
    }

    // ---- Spellcasting ----

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

    // ---- Inventory ----

    // Q33: carrying capacity, in inventory-space slots, = 10 + 2 * STR modifier.
    public int computeCarryCapacity(GameCharacter character) {
        return 10 + 2 * character.getStats().modifier(AbilityScore.STR);
    }

    /** Sum of inventorySpace * quantity across every carried item (rounded to 2 dp). */
    public double computeCarriedSpace(GameCharacter character) {
        double total = 0;
        for (var item : character.getInventory()) {
            total += gameData.getItemSpace(item.getItemId()) * item.getQuantity();
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
