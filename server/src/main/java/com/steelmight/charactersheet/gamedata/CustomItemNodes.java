package com.steelmight.charactersheet.gamedata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.steelmight.charactersheet.model.CustomItem;

/**
 * Renders a {@link CustomItem} into the exact JSON shape weapons.json / armor.json use.
 *
 * This is the whole trick behind custom items (demo feedback #19): every consumer —
 * the damage roll, AC/PA/MA derivation, proficiency penalties, the equip rules — already
 * reads a catalog {@code JsonNode}, so a custom item that renders into the same shape
 * needs no special-casing anywhere downstream. Get this mapping right and custom gear
 * is just gear.
 */
public final class CustomItemNodes {

    private static final JsonNodeFactory F = JsonNodeFactory.instance;

    private CustomItemNodes() {}

    public static ResolvedItem resolve(CustomItem item) {
        if (item == null) return null;
        ItemKind kind = item.isWeapon() ? ItemKind.WEAPON : ItemKind.ARMOR;
        // priceTier and price stay null: custom gear is granted, never priced, which is
        // what keeps it out of the shop's purchase/sell/upgrade paths.
        return new ResolvedItem(item.getItemId(), item.getName(), kind, null, null, null, toNode(item));
    }

    public static JsonNode toNode(CustomItem item) {
        ObjectNode node = F.objectNode();
        node.put("id", item.getItemId());
        node.put("name", item.getName());
        node.put("inventorySpace", item.getInventorySpace());
        node.set("properties", propertiesNode(item));

        // isProficientWith treats a MISSING proficientClasses as "everyone is proficient"
        // and an EMPTY array as "nobody is" — exactly the two cases the flag expresses.
        if (!item.isProficient()) node.set("proficientClasses", F.arrayNode());

        if (item.isWeapon()) {
            ObjectNode damage = node.putObject("damage");
            damage.put("modMultiplier", 1);
            damage.put("flat", item.getDamageFlat());
            if (item.getDamageDice() != null && !item.getDamageDice().isBlank()) {
                damage.put("dice", item.getDamageDice());
            }
            node.put("damageType", item.getDamageType());
            node.put("scaling", item.getDamageScaling());
            node.put("apCost", item.getApCost());
            ArrayNode stat = node.putArray("stat");
            if (item.getWeaponStat() != null && !item.getWeaponStat().isBlank()) {
                stat.add(item.getWeaponStat());
            }
        } else {
            node.put("type", item.getArmorType());
            ObjectNode ac = node.putObject("ac");
            ac.put("base", item.getAcBase());
            ac.put("dexMod", item.isAcDexMod());
            ac.put("dexMultiplier", 1);
            if (item.isShield()) ac.put("isBonus", true);
            node.put("pa", item.getPa());
            node.put("ma", item.getMa());
            node.put("paScaling", item.getPaScaling());
            node.put("maScaling", item.getMaScaling());
            // No acBonusLevels: milestone AC growth is a catalog-armor property, and
            // silently granting it to homebrew would be a balance decision, not a mapping.
        }
        return node;
    }

    private static ArrayNode propertiesNode(CustomItem item) {
        ArrayNode props = F.arrayNode();
        for (String p : item.getProperties().split(",")) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty()) props.add(trimmed);
        }
        return props;
    }
}
