package com.steelmight.charactersheet.service;

import com.steelmight.charactersheet.dto.ActionResponse;
import com.steelmight.charactersheet.dto.CombatSnapshot;
import com.steelmight.charactersheet.dto.EquipRequest;
import com.steelmight.charactersheet.engine.ResolutionResult;
import com.steelmight.charactersheet.engine.StatDerivationEngine;
import com.steelmight.charactersheet.gamedata.GameDataProvider;
import com.steelmight.charactersheet.gamedata.ItemKind;
import com.steelmight.charactersheet.gamedata.ResolvedItem;
import com.steelmight.charactersheet.model.GameCharacter;
import com.steelmight.charactersheet.model.InventoryEntry;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.steelmight.charactersheet.repository.CharacterRepository;

import java.util.List;

/**
 * Equip slot rules (M5-B): one body armor, one shield, one weapon set —
 * two light weapons (dual-wield, Q29) OR one other weapon OR one caster weapon.
 * Same-slot equips auto-unequip the previous item (steps note it); the
 * two-handed-weapon ↔ shield conflict is a hard 400 both ways. Non-proficient
 * gear equips fine (Q30) — the penalties surface in CombatSnapshot and
 * non-proficient armor blocks casting (enforced in M4-A's validation).
 */
@Service
@Transactional
public class EquipmentService {

    private final CharacterRepository repo;
    private final GameDataProvider gameData;
    private final StatDerivationEngine statEngine;
    private final CharacterService characterService;

    public EquipmentService(CharacterRepository repo, GameDataProvider gameData,
                            StatDerivationEngine statEngine, CharacterService characterService) {
        this.repo = repo;
        this.gameData = gameData;
        this.statEngine = statEngine;
        this.characterService = characterService;
    }

    public ActionResponse<CombatSnapshot> equip(String playerId, EquipRequest req) {
        var c = characterService.getCharacter(playerId);
        var entry = findEntry(c, req);
        var item = gameData.findItem(entry.getItemId());
        var result = new ResolutionResult();

        if (entry.isEquipped()) {
            result.addStep("equip", entry.getItemId() + " is already equipped", 1, 1);
            return new ActionResponse<>(result, characterService.getCombatSnapshot(playerId));
        }

        switch (item.kind()) {
            case ARMOR -> {
                if (isShield(item)) {
                    if (equippedTwoHandedWeapon(c) != null) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "a two-handed weapon is equipped — it blocks a shield");
                    }
                    unequipWhere(c, result, e -> isShield(gameData.findItem(e.getItemId())));
                } else {
                    unequipWhere(c, result, e -> {
                        var other = gameData.findItem(e.getItemId());
                        return other != null && other.kind() == ItemKind.ARMOR && !isShield(other);
                    });
                }
            }
            case WEAPON -> {
                if (hasProperty(item, "two-handed") && equippedShield(c) != null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "a shield is equipped — it blocks a two-handed weapon");
                }
                var set = equippedWeaponSet(c);
                boolean dualWield = hasProperty(item, "light")
                        && set.size() == 1
                        && isLightWeapon(gameData.findItem(set.get(0).getItemId()));
                if (!dualWield) {
                    // the new weapon replaces the whole set (light+non-light never coexist, Q29)
                    unequipWhere(c, result, e -> weaponSetMember(gameData.findItem(e.getItemId())));
                }
            }
            case CASTER_WEAPON ->
                    unequipWhere(c, result, e -> weaponSetMember(gameData.findItem(e.getItemId())));
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    item.id() + " is not equippable");
        }

        entry.setEquipped(true);
        result.addStep("equip", "Equipped " + item.name(), 0, 1);
        if (!statEngine.isProficientWith(c, item)) {
            result.addStep("proficiency", item.id() + " without proficiency — "
                    + penaltyText(item), 0, 0);
        }

        repo.save(c);
        return new ActionResponse<>(result, characterService.getCombatSnapshot(playerId));
    }

    public ActionResponse<CombatSnapshot> unequip(String playerId, EquipRequest req) {
        var c = characterService.getCharacter(playerId);
        var entry = findEntry(c, req);
        var result = new ResolutionResult();
        if (!entry.isEquipped()) {
            result.addStep("unequip", entry.getItemId() + " was not equipped", 0, 0);
        } else {
            entry.setEquipped(false);
            result.addStep("unequip", "Unequipped " + entry.getItemId(), 1, 0);
            repo.save(c);
        }
        return new ActionResponse<>(result, characterService.getCombatSnapshot(playerId));
    }

    /** Non-proficiency consequences (Q30, Guide pp.18-20) — display data for the DM. */
    public static String penaltyText(ResolvedItem item) {
        return item.kind() == ItemKind.WEAPON
                ? "no proficiency or stat mod on attack & damage; no weapon properties"
                : "cannot cast spells; attacks at disadvantage; stacked disadvantage = auto-miss";
    }

    // ---- Helpers ----

    private InventoryEntry findEntry(GameCharacter c, EquipRequest req) {
        if (req.itemId() == null || req.itemId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "itemId is required");
        }
        var item = gameData.findItem(req.itemId());
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown item: " + req.itemId());
        }
        var matches = c.getInventory().stream()
                .filter(e -> e.getItemId().equals(req.itemId()))
                .filter(e -> req.tier() == null || e.getUpgradeTier() == req.tier())
                .toList();
        if (matches.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "not carrying " + req.itemId());
        }
        if (req.tier() == null
                && matches.stream().map(InventoryEntry::getUpgradeTier).distinct().count() > 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "carrying " + req.itemId() + " at several levels — specify tier");
        }
        return matches.get(0);
    }

    private void unequipWhere(GameCharacter c, ResolutionResult result,
                              java.util.function.Predicate<InventoryEntry> slotMatch) {
        for (var e : c.getInventory()) {
            if (e.isEquipped() && slotMatch.test(e)) {
                e.setEquipped(false);
                result.addStep("unequip", e.getItemId() + " auto-unequipped (slot occupied)", 1, 0);
            }
        }
    }

    private List<InventoryEntry> equippedWeaponSet(GameCharacter c) {
        return c.getInventory().stream()
                .filter(InventoryEntry::isEquipped)
                .filter(e -> weaponSetMember(gameData.findItem(e.getItemId())))
                .toList();
    }

    private InventoryEntry equippedShield(GameCharacter c) {
        return c.getInventory().stream()
                .filter(InventoryEntry::isEquipped)
                .filter(e -> isShield(gameData.findItem(e.getItemId())))
                .findFirst().orElse(null);
    }

    private InventoryEntry equippedTwoHandedWeapon(GameCharacter c) {
        return c.getInventory().stream()
                .filter(InventoryEntry::isEquipped)
                .filter(e -> {
                    var item = gameData.findItem(e.getItemId());
                    return item != null && item.kind() == ItemKind.WEAPON
                            && hasProperty(item, "two-handed");
                })
                .findFirst().orElse(null);
    }

    private static boolean weaponSetMember(ResolvedItem item) {
        return item != null && (item.kind() == ItemKind.WEAPON || item.kind() == ItemKind.CASTER_WEAPON);
    }

    /** Shields are armor-kind items with type "shield" (also used at creation). */
    public static boolean isShield(ResolvedItem item) {
        return item != null && item.kind() == ItemKind.ARMOR
                && "shield".equals(item.node().path("type").asText());
    }

    private static boolean isLightWeapon(ResolvedItem item) {
        return item != null && item.kind() == ItemKind.WEAPON && hasProperty(item, "light");
    }

    public static boolean hasProperty(ResolvedItem item, String property) {
        for (var p : item.node().path("properties")) {
            if (property.equals(p.asText())) return true;
        }
        return false;
    }
}
