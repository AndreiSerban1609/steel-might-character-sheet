package com.steelmight.charactersheet.service;

import com.steelmight.charactersheet.dto.ActionResponse;
import com.steelmight.charactersheet.dto.CastScrollRequest;
import com.steelmight.charactersheet.dto.CombatSnapshot;
import com.steelmight.charactersheet.dto.InventorySnapshot;
import com.steelmight.charactersheet.dto.PurchaseRequest;
import com.steelmight.charactersheet.dto.SellRequest;
import com.steelmight.charactersheet.dto.UpgradeRequest;
import com.steelmight.charactersheet.dto.UseConsumableRequest;
import com.steelmight.charactersheet.engine.ActiveMechanics;
import com.steelmight.charactersheet.engine.HealEvent;
import com.steelmight.charactersheet.engine.HealingResolutionPipeline;
import com.steelmight.charactersheet.engine.MechanicType;
import com.steelmight.charactersheet.engine.ModifiableStat;
import com.steelmight.charactersheet.engine.PreventableAction;
import com.steelmight.charactersheet.engine.RandomSource;
import com.steelmight.charactersheet.engine.ResolutionResult;
import com.steelmight.charactersheet.engine.StatDerivationEngine;
import com.steelmight.charactersheet.gamedata.GameDataProvider;
import com.steelmight.charactersheet.gamedata.ItemKind;
import com.steelmight.charactersheet.gamedata.ResolvedItem;
import com.steelmight.charactersheet.model.GameCharacter;
import com.steelmight.charactersheet.model.InventoryEntry;
import com.steelmight.charactersheet.repository.CharacterRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

/**
 * Purchase/sell against the item catalogs (M5-A). Money is ONE generic gold
 * currency (Game Owner 2026-07-06 — the copper/silver tier concept is gone);
 * every price in pricing.json and the item catalogs is already in it. Tiered
 * items price via pricing.json tier tables, potions via pricesBySize, everything
 * else via its explicit price field. Sell-back credits half
 * (pricing.json sellbackRatio); silvered weapons cost ×5 both ways.
 */
@Service
@Transactional
public class ShopService {

    private final CharacterRepository repo;
    private final GameDataProvider gameData;
    private final StatDerivationEngine statEngine;
    private final CharacterService characterService;
    private final HealingResolutionPipeline healingPipeline;
    private final RandomSource randomSource;
    private final AuditService audit;

    public ShopService(CharacterRepository repo, GameDataProvider gameData,
                       StatDerivationEngine statEngine, CharacterService characterService,
                       HealingResolutionPipeline healingPipeline, RandomSource randomSource,
                       AuditService audit) {
        this.audit = audit;
        this.repo = repo;
        this.gameData = gameData;
        this.statEngine = statEngine;
        this.characterService = characterService;
        this.healingPipeline = healingPipeline;
        this.randomSource = randomSource;
    }

    public ActionResponse<InventorySnapshot> purchase(String playerId, PurchaseRequest req) {
        var c = characterService.getCharacter(playerId);
        var item = requireItem(req.itemId());

        int quantity = req.quantity() != null ? req.quantity() : 1;
        if (quantity < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quantity must be at least 1");
        }
        boolean silvered = Boolean.TRUE.equals(req.silvered());
        if (silvered && item.kind() != ItemKind.WEAPON) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "only weapons can be silvered");
        }
        int tier = resolvePurchaseTier(item, req.tier());
        // Same level cap as upgrades (Game Owner 2026-07-13): no buying weapons/armor above
        // your level. Potions/scrolls use tier as potency — uncapped until ruled otherwise.
        if ((item.kind() == ItemKind.WEAPON || item.kind() == ItemKind.ARMOR) && tier > c.getLevel()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "cannot buy level " + tier + " gear at character level " + c.getLevel());
        }

        // Scrolls are OF a specific spell (Game Owner 2026-07-07) — chosen when buying.
        com.steelmight.charactersheet.gamedata.SpellDefinition scrollSpell = null;
        if (item.kind() == ItemKind.SCROLL) {
            if (req.spellId() == null || req.spellId().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "scrolls are bought for a specific spell — spellId is required");
            }
            scrollSpell = validateScrollSpell(gameData, item, req.spellId());
        } else if (req.spellId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "spellId only applies to scrolls");
        }

        int unitPrice = priceOf(item, tier);
        if (silvered) {
            unitPrice *= gameData.getPricing().path("silveringMultiplier").asInt(5);
        }
        int total = unitPrice * quantity;
        if (c.getGold() < total) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Insufficient gold: have " + c.getGold() + ", need " + total);
        }

        // Q33: purchases respect carrying capacity like every other inventory write.
        double projected = statEngine.computeCarriedSpace(c) + gameData.getItemSpace(item.id()) * quantity;
        int capacity = statEngine.computeCarryCapacity(c);
        if (Math.round(projected * 100.0) / 100.0 > capacity) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    String.format("over carrying capacity: %.1f / %d slots", projected, capacity));
        }

        var result = new ResolutionResult();
        int before = c.getGold();
        c.setGold(before - total);
        result.addStep("purchase", "Bought " + quantity + "× "
                        + (scrollSpell != null ? "Scroll of " + scrollSpell.name() : item.name())
                        + (silvered ? " (silvered)" : "")
                        + (usesTierPricing(item) ? " at level " + tier : "")
                        + " for " + total + "g",
                before, c.getGold());

        int storedTier = usesTierPricing(item) ? tier : 0;
        String spellId = scrollSpell != null ? scrollSpell.id() : null;
        var existing = c.getInventory().stream()
                .filter(e -> e.getItemId().equals(item.id())
                        && e.getUpgradeTier() == storedTier
                        && e.isSilvered() == silvered
                        && Objects.equals(e.getStoredSpellId(), spellId)
                        && !e.isEquipped())
                .findFirst()
                .orElse(null);
        if (existing != null) {
            existing.setQuantity(existing.getQuantity() + quantity);
            result.addStep("add-item", item.id() + " quantity increased",
                    existing.getQuantity() - quantity, existing.getQuantity());
        } else {
            var entry = new InventoryEntry(item.id(), quantity, storedTier, false);
            entry.setSilvered(silvered);
            entry.setStoredSpellId(spellId);
            c.addItem(entry);
            result.addStep("add-item", item.id() + " added to inventory", 0, quantity);
        }

        repo.save(c);
        audit.log(c, "purchase", "Bought " + quantity + "× "
                + (scrollSpell != null ? "Scroll of " + scrollSpell.name() : item.name())
                + " for " + total + "g (gold " + before + "→" + c.getGold() + ")");
        return new ActionResponse<>(result, characterService.buildInventorySnapshot(c));
    }

    public ActionResponse<InventorySnapshot> sell(String playerId, SellRequest req) {
        var c = characterService.getCharacter(playerId);
        var item = requireItem(req.itemId());

        int quantity = req.quantity() != null ? req.quantity() : 1;
        if (quantity < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quantity must be at least 1");
        }

        var entry = findCarriedEntry(c, item.id(), req.tier(), req.spellId());
        if (entry.isEquipped()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "unequip " + item.id() + " before selling it");
        }
        if (entry.getQuantity() < quantity) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "only carrying " + entry.getQuantity() + "× " + item.id());
        }

        // Half the CURRENT price — tiered items at their own level (legacy tier-0 rows price as 1).
        int unitPrice = priceOf(item, Math.max(1, entry.getUpgradeTier()));
        if (entry.isSilvered()) {
            unitPrice *= gameData.getPricing().path("silveringMultiplier").asInt(5);
        }
        double ratio = gameData.getPricing().path("sellbackRatio").asDouble(0.5);
        int credit = (int) Math.floor(unitPrice * ratio) * quantity;

        var result = new ResolutionResult();
        if (entry.getQuantity() == quantity) {
            c.getInventory().remove(entry);
        } else {
            entry.setQuantity(entry.getQuantity() - quantity);
        }
        result.addStep("remove-item", "Sold " + quantity + "× " + item.name(),
                quantity, entry.getQuantity() == quantity ? 0 : entry.getQuantity());

        int before = c.getGold();
        c.setGold(before + credit);
        result.addStep("sell", "Credited " + credit + "g (half of " + unitPrice + "g each)",
                before, c.getGold());

        repo.save(c);
        audit.log(c, "sell", "Sold " + quantity + "× " + item.name()
                + " for " + credit + "g (gold " + before + "→" + c.getGold() + ")");
        return new ActionResponse<>(result, characterService.buildInventorySnapshot(c));
    }

    // ---- Upgrades & consumables (M5-C) ----

    /** Kit: one-handed tier price at target / 3, d20 roll vs 5 + current level, cost
     *  lost on failure (Q31). Blacksmith: price delta + 5% of target, guaranteed. */
    public ActionResponse<InventorySnapshot> upgrade(String playerId, UpgradeRequest req) {
        var c = characterService.getCharacter(playerId);
        var item = requireItem(req.itemId());
        if (item.kind() != ItemKind.WEAPON && item.kind() != ItemKind.ARMOR) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "only weapons and armor can be upgraded");
        }
        if (!"kit".equals(req.mode()) && !"blacksmith".equals(req.mode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "mode must be 'kit' or 'blacksmith'");
        }
        var entry = findCarriedEntry(c, item.id(), req.tier());
        int current = Math.max(1, entry.getUpgradeTier());
        if (current >= 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    item.id() + " is already at the maximum level (20)");
        }
        int target = current + 1;
        // Items cannot be upgraded past the character's level (Game Owner 2026-07-13):
        // a level 3 conqueror cannot carry a level 4 pike.
        if (target > c.getLevel()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "cannot upgrade past your level (" + c.getLevel() + ")");
        }

        var result = new ResolutionResult();
        boolean success;
        if ("kit".equals(req.mode())) {
            var kit = gameData.getPricing().path("upgradeKit");
            String costTier = kit.path("costTier").asText("one-handed");
            int divisor = kit.path("costDivisor").asInt(3);
            int cost = tierPrice(costTier, target, item.id()) / divisor;
            spendGold(c, cost, result, "upgrade-cost",
                    "Upgrade kit for level " + target + ": " + cost + "c");

            // Q32: no smith-tools proficiency representation exists yet — flat d20.
            int dc = kit.path("checkDC").path("base").asInt(5)
                    + kit.path("checkDC").path("perLevel").asInt(1) * current;
            int roll = 1 + randomSource.nextInt(20);
            success = roll >= dc;
            result.addStep("upgrade-roll",
                    "d20 " + roll + " vs DC " + dc + " — " + (success ? "success" : "failure"
                            + " (kit and cost consumed, Q31)"), roll, roll);
        } else {
            int priceCurrent = priceOf(item, current);
            int priceTarget = priceOf(item, target);
            int surcharge = gameData.getPricing().path("blacksmithUpgrade")
                    .path("surchargePercent").asInt(5);
            int cost = (priceTarget - priceCurrent) + priceTarget * surcharge / 100;
            spendGold(c, cost, result, "upgrade-cost",
                    "Blacksmith upgrade to level " + target + ": " + cost + "c");
            success = true;
        }

        if (success) {
            if (entry.getQuantity() > 1) {
                // upgrade one item out of the stack
                entry.setQuantity(entry.getQuantity() - 1);
                var upgraded = new InventoryEntry(item.id(), 1, target, false);
                upgraded.setSilvered(entry.isSilvered());
                c.addItem(upgraded);
            } else {
                entry.setUpgradeTier(target);
            }
            result.addStep("upgrade", item.name() + " upgraded", current, target);
        }

        repo.save(c);
        audit.log(c, "upgrade", item.name() + " " + req.mode() + " upgrade to level " + target
                + (success ? "" : " FAILED — cost lost"));
        return new ActionResponse<>(result, characterService.buildInventorySnapshot(c));
    }

    /** Potions heal healPerLevel × the POTION's level (N5) through the healing pipeline;
     *  charge items decrement chargesRemaining; general goods just get consumed. */
    public ActionResponse<CombatSnapshot> useConsumable(String playerId, UseConsumableRequest req) {
        var c = characterService.getCharacter(playerId);
        var item = requireItem(req.itemId());
        var entry = findCarriedEntry(c, item.id(), req.tier());
        var result = new ResolutionResult();

        switch (item.kind()) {
            case POTION -> {
                int level = Math.max(1, entry.getUpgradeTier());
                int heal = item.node().path("healPerLevel").asInt(0) * level;
                spendAp(c, item.node().path("apCost").asInt(1), result, item.name());
                // Cursed/Decaying/Maimed resolve inside the pipeline — a cursed
                // character wastes the potion by design.
                var healResult = healingPipeline.resolve(new HealEvent(heal), c);
                healResult.getSteps().forEach(s ->
                        result.addStep(s.rule(), s.note(), s.valueBefore(), s.valueAfter()));
                healResult.getEffectsTriggered().forEach(result::addTriggeredEffect);
                consumeOne(c, entry, result, item);
            }
            case MAGIC_SHOP -> {
                Integer maxCharges = chargeCapacity(item);
                if (maxCharges == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            item.id() + " has no tracked uses — the DM adjudicates it");
                }
                int charges = entry.getChargesRemaining() != null
                        ? entry.getChargesRemaining() : maxCharges;
                if (charges <= 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            item.id() + " has no charges left (restored by resting)");
                }
                int apCost = item.node().path("apCost").asInt(0);
                if (apCost > 0) spendAp(c, apCost, result, item.name());
                entry.setChargesRemaining(charges - 1);
                result.addStep("use-charge", item.name() + " activated — effect per its description",
                        charges, charges - 1);
            }
            case GENERAL -> consumeOne(c, entry, result, item);
            case SCROLL -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "scrolls are cast, not used — POST /actions/cast-scroll with the spell on it");
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    item.id() + " is not consumable");
        }

        repo.save(c);
        audit.log(c, "use-consumable", "Used " + item.name());
        return new ActionResponse<>(result, characterService.getCombatSnapshot(playerId));
    }

    /**
     * Scroll casting (Shops p.17): ANY character may cast the scroll's spell —
     * no caster requirement, no known/prepared check, NO mana. The spell was
     * written on the scroll at purchase (Game Owner 2026-07-07); minCharLevel
     * gates the reader; the spell's AP cost applies and the scroll is consumed.
     * Reading is still casting: prevent-action effects and non-proficient armor
     * block it.
     */
    public ActionResponse<CombatSnapshot> castScroll(String playerId, CastScrollRequest req) {
        var c = characterService.getCharacter(playerId);
        var item = requireItem(req.itemId());
        if (item.kind() != ItemKind.SCROLL) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    item.id() + " is not a spell scroll");
        }
        var entry = findCarriedEntry(c, item.id(), req.tier(), req.spellId());

        int minLevel = item.node().path("minCharLevel").asInt(1);
        if (c.getLevel() < minLevel) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    item.name() + " requires character level " + minLevel);
        }

        if (entry.getStoredSpellId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "no spell is written on this scroll — the DM can set one via the inventory editor");
        }
        var spell = gameData.getSpell(entry.getStoredSpellId());
        if (spell == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "the scroll's spell '" + entry.getStoredSpellId() + "' no longer exists in the data");
        }

        // Reading the scroll is still an action: stunned/frozen block it, and
        // non-proficient armor blocks spellcasting of any kind (Q30).
        int threshold = statEngine.computeStackThreshold(c);
        var prevented = ActiveMechanics.collect(c, gameData, threshold, MechanicType.PREVENT_ACTION).stream()
                .filter(h -> h.mechanic().action() == PreventableAction.ALL)
                .findFirst();
        if (prevented.isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "cannot cast while " + prevented.get().def().name().toLowerCase());
        }
        if (statEngine.hasNonProficientArmorEquipped(c)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "cannot cast spells in non-proficient armor");
        }

        if (spell.apCost().isSpecial()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "spell has a non-numeric AP cost ('" + spell.apCost().special()
                            + "') — DM adjudicates; spend AP via spend-resource");
        }
        int apCost = statEngine.resolveModifiedStat(c, ModifiableStat.SPELL_AP_COST,
                spell.apCost().resolve(statEngine.computeMaxAP(c)));
        if (c.getAp().getCurrent() < apCost) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Insufficient ap: have " + c.getAp().getCurrent() + ", need " + apCost);
        }

        var result = new ResolutionResult();
        consumeOne(c, entry, result, item);

        // No mana (Shops p.17); the scroll casts at the spell's own level (no upcast).
        return characterService.resolveCast(playerId, c, spell, spell.level(), apCost, 0,
                req.targetPlayerId(), req.applyEffectsToSelf(), result);
    }

    /** usesPerLongRest (rest-restored) or charges (permanent pool); null = untracked. */
    public Integer chargeCapacity(ResolvedItem item) {
        if (item.node().path("usesPerLongRest").isInt()) {
            return item.node().path("usesPerLongRest").asInt();
        }
        if (item.node().path("charges").isInt()) {
            return item.node().path("charges").asInt();
        }
        return null;
    }

    private void consumeOne(GameCharacter c, InventoryEntry entry, ResolutionResult result,
                            ResolvedItem item) {
        int before = entry.getQuantity();
        if (before <= 1) {
            c.getInventory().remove(entry);
        } else {
            entry.setQuantity(before - 1);
        }
        result.addStep("consume", item.name() + " consumed", before, Math.max(0, before - 1));
    }

    private void spendGold(GameCharacter c, int cost, ResolutionResult result,
                             String rule, String note) {
        if (c.getGold() < cost) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Insufficient gold: have " + c.getGold() + ", need " + cost);
        }
        int before = c.getGold();
        c.setGold(before - cost);
        result.addStep(rule, note, before, c.getGold());
    }

    private void spendAp(GameCharacter c, int cost, ResolutionResult result, String what) {
        int before = c.getAp().getCurrent();
        if (before < cost) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Insufficient ap: have " + before + ", need " + cost);
        }
        c.getAp().setCurrent(before - cost);
        result.addStep("spend-ap", "Spent " + cost + " AP using " + what, before, before - cost);
    }

    private InventoryEntry findCarriedEntry(GameCharacter c, String itemId, Integer tier) {
        return findCarriedEntry(c, itemId, tier, null);
    }

    private InventoryEntry findCarriedEntry(GameCharacter c, String itemId, Integer tier,
                                            String spellId) {
        var matches = c.getInventory().stream()
                .filter(e -> e.getItemId().equals(itemId))
                .filter(e -> tier == null || e.getUpgradeTier() == tier)
                .filter(e -> spellId == null || spellId.equals(e.getStoredSpellId()))
                .toList();
        if (matches.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "not carrying " + itemId
                            + (tier != null ? " at level " + tier : "")
                            + (spellId != null ? " with spell " + spellId : ""));
        }
        if (tier == null
                && matches.stream().map(InventoryEntry::getUpgradeTier).distinct().count() > 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "carrying " + itemId + " at several levels — specify tier");
        }
        if (spellId == null
                && matches.stream().map(InventoryEntry::getStoredSpellId).distinct().count() > 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "carrying " + itemId + " with different spells — specify spellId");
        }
        return matches.get(0);
    }

    /** Scrolls hold a specific spell — it must exist and match the scroll's spell
     *  level and caster tier. Shared with the DM inventory-edit path. */
    static com.steelmight.charactersheet.gamedata.SpellDefinition validateScrollSpell(
            GameDataProvider gameData, ResolvedItem scroll, String spellId) {
        var spell = gameData.getSpell(spellId);
        if (spell == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown spell: " + spellId);
        }
        int scrollLevel = scroll.node().path("spellLevel").asInt(1);
        if (spell.level() != scrollLevel) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    scroll.name() + " holds a level-" + scrollLevel + " spell — '"
                            + spell.id() + "' is level " + spell.level());
        }
        String scrollTier = scroll.node().path("casterType").asText();
        String spellTier = gameData.getClassAbilities().path(spell.classId())
                .path("casterType").asText("none");
        if (!scrollTier.equals(spellTier)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    scroll.name() + " holds a " + scrollTier + "-caster spell — '"
                            + spell.id() + "' belongs to a " + spellTier + " class");
        }
        return spell;
    }

    // ---- Pricing (M5-A) ----

    /** Current price (generic gold) of one item at the given level (tiered kinds only use it). */
    public int priceOf(ResolvedItem item, int tier) {
        return switch (item.kind()) {
            case WEAPON, ARMOR -> tierPrice(item.priceTier(), tier, item.id());
            case CASTER_WEAPON -> tierPrice(item.priceTier(),
                    Objects.requireNonNullElse(item.intrinsicLevel(), 1), item.id());
            case POTION -> potionPrice(item.id(), tier);
            default -> {
                if (item.price() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            item.id() + " has no price");
                }
                yield item.price();
            }
        };
    }

    /** Tier pricing depends on the buyer's chosen level (weapons/armor) or potion level. */
    private boolean usesTierPricing(ResolvedItem item) {
        return item.kind() == ItemKind.WEAPON || item.kind() == ItemKind.ARMOR
                || item.kind() == ItemKind.POTION;
    }

    private int resolvePurchaseTier(ResolvedItem item, Integer requested) {
        if (!usesTierPricing(item)) {
            if (requested != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        item.id() + " has a fixed price — tier does not apply");
            }
            return 1;
        }
        int tier = requested != null ? requested : 1;
        if (tier < 1 || tier > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "tier out of range (1-20): " + tier);
        }
        return tier;
    }

    private int tierPrice(String priceTier, int level, String itemId) {
        var prices = gameData.getPricing().path("tiers").path(priceTier).path("prices");
        if (!prices.isArray() || level < 1 || level > prices.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "no price for " + itemId + " at level " + level);
        }
        return prices.get(level - 1).asInt();
    }

    private int potionPrice(String sizeId, int level) {
        var prices = gameData.getConsumables().path("healingPotions").path("pricesBySize").path(sizeId);
        if (!prices.isArray() || level < 1 || level > prices.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "no price for " + sizeId + " potion at level " + level);
        }
        return prices.get(level - 1).asInt();
    }

    private ResolvedItem requireItem(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "itemId is required");
        }
        var item = gameData.findItem(itemId);
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown item: " + itemId);
        }
        return item;
    }
}
