package com.steelmight.charactersheet.service;

import com.steelmight.charactersheet.dto.PurchaseRequest;
import com.steelmight.charactersheet.dto.SellRequest;
import com.steelmight.charactersheet.gamedata.GameDataProvider;
import com.steelmight.charactersheet.gamedata.ItemKind;
import com.steelmight.charactersheet.model.*;
import com.steelmight.charactersheet.repository.CharacterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M5 acceptance criteria — A: tier pricing, insufficient funds untouched, sell-back
 * at half, equipped rejection, currency breakdown, findItem. B: equip slots +
 * proficiency penalties. C: upgrades (seeded rolls), consumables, charges.
 */
@SpringBootTest
@org.springframework.context.annotation.Import(InventoryActionsTest.FixedRandom.class)
class InventoryActionsTest {

    @org.springframework.boot.test.context.TestConfiguration
    static class FixedRandom {
        /** nextInt(bound) returns min(next, bound - 1); the d20 lands on next + 1. */
        static int next = 10;

        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        com.steelmight.charactersheet.engine.RandomSource fixedRandomSource() {
            return bound -> Math.min(next, bound - 1);
        }
    }

    @Autowired
    private ShopService shop;

    @Autowired
    private CharacterService service;

    @Autowired
    private GameDataProvider gameData;

    @Autowired
    private CharacterRepository repo;

    @BeforeEach
    void setUp() {
        repo.deleteAll();
        FixedRandom.next = 10;
    }

    /** STR 16 → carry capacity 10 + 2×3 = 16 slots; rich by default. */
    private GameCharacter buyer(int gold) {
        var c = new GameCharacter("buyer");
        c.setName("buyer");
        c.setLevel(5);
        c.setPathId("warrior");
        c.setClassId("barbarian");
        c.setStats(new Stats(16, 10, 12, 10, 10, 10, 10));
        c.setHp(new HitPoints(100, 100, 0));
        c.setMana(new ManaPool(0, 0));
        c.setAp(new ActionPoints(6, 6, 10));
        c.setGold(gold);
        return repo.save(c);
    }

    @Nested
    class FindItem {
        @Test
        void resolvesAcrossEveryCatalog() {
            assertThat(gameData.findItem("greataxe").kind()).isEqualTo(ItemKind.WEAPON);
            assertThat(gameData.findItem("light-armor").kind()).isEqualTo(ItemKind.ARMOR);
            assertThat(gameData.findItem("arcane-orb-3").kind()).isEqualTo(ItemKind.CASTER_WEAPON);
            assertThat(gameData.findItem("small").kind()).isEqualTo(ItemKind.POTION);
            assertThat(gameData.findItem("mask-of-disguise").kind()).isEqualTo(ItemKind.MAGIC_SHOP);
            assertThat(gameData.findItem("minor-scroll-1").kind()).isEqualTo(ItemKind.SCROLL);
            assertThat(gameData.findItem("food-ration").kind()).isEqualTo(ItemKind.GENERAL);
            assertThat(gameData.findItem("horse").kind()).isEqualTo(ItemKind.MOUNT);
            assertThat(gameData.findItem("nope")).isNull();
        }
    }

    @Nested
    class Purchase {

        // Criterion 1 — greataxe is two-handed: prices[0]=15, prices[4]=75
        @Test
        void tieredWeaponPricesByLevel() {
            buyer(1000);
            var r1 = shop.purchase("buyer", new PurchaseRequest("greataxe", null, 1, null));
            assertThat(r1.snapshot().gold()).isEqualTo(985);

            var r2 = shop.purchase("buyer", new PurchaseRequest("greataxe", null, 5, null));
            assertThat(r2.snapshot().gold()).isEqualTo(910);
            // two entries: same item at different levels
            assertThat(r2.snapshot().items()).hasSize(2);
        }

        @Test
        void insufficientFundsLeavesStateUnchanged() {
            buyer(10); // greataxe tier 1 costs 15
            assertThatThrownBy(() -> shop.purchase("buyer", new PurchaseRequest("greataxe", null, null, null)))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Insufficient gold");
            var snapshot = service.getInventorySnapshot("buyer");
            assertThat(snapshot.gold()).isEqualTo(10);
            assertThat(snapshot.items()).isEmpty();
        }

        @Test
        void silveredWeaponCostsFiveTimesMore() {
            buyer(1000);
            var r = shop.purchase("buyer", new PurchaseRequest("greataxe", null, 1, true));
            assertThat(r.snapshot().gold()).isEqualTo(1000 - 75); // 15 × 5
            assertThat(r.snapshot().items().get(0).silvered()).isTrue();
        }

        @Test
        void casterWeaponPricesByItsIntrinsicLevel() {
            buyer(1000);
            // arcane-orb-3: priceTier one-handed, itemLevel 3 → prices[2]
            int expected = gameData.getPricing().path("tiers").path("one-handed")
                    .path("prices").get(2).asInt();
            var r = shop.purchase("buyer", new PurchaseRequest("arcane-orb-3", null, null, null));
            assertThat(r.snapshot().gold()).isEqualTo(1000 - expected);
        }

        @Test
        void potionPricesBySizeAndLevel() {
            buyer(100);
            // small potion at level 3 → pricesBySize.small[2] = 6
            var r = shop.purchase("buyer", new PurchaseRequest("small", 2, 3, null));
            assertThat(r.snapshot().gold()).isEqualTo(100 - 12);
            assertThat(r.snapshot().items().get(0).upgradeTier()).isEqualTo(3);
            assertThat(r.snapshot().items().get(0).quantity()).isEqualTo(2);
        }

        @Test
        void explicitPriceItemRejectsTier() {
            buyer(100);
            assertThatThrownBy(() -> shop.purchase("buyer", new PurchaseRequest("food-ration", null, 3, null)))
                    .hasMessageContaining("fixed price");
            var r = shop.purchase("buyer", new PurchaseRequest("food-ration", null, null, null));
            assertThat(r.snapshot().gold()).isEqualTo(98);
        }

        @Test
        void overCapacityPurchaseIs400() {
            buyer(100000);
            // greataxe takes 2 slots; capacity 16 → 9 axes (18 slots) won't fit
            assertThatThrownBy(() -> shop.purchase("buyer", new PurchaseRequest("greataxe", 9, 1, null)))
                    .hasMessageContaining("carrying capacity");
        }

        @Test
        void unknownItemIs404() {
            buyer(100);
            assertThatThrownBy(() -> shop.purchase("buyer", new PurchaseRequest("nope", null, null, null)))
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
                    .isEqualTo(404);
        }
    }

    @Nested
    class Sell {

        // Criterion 2
        @Test
        void sellingCreditsHalfTheCurrentPrice() {
            buyer(1000);
            shop.purchase("buyer", new PurchaseRequest("greataxe", null, 5, null)); // 75c
            var r = shop.sell("buyer", new SellRequest("greataxe", null, null));
            assertThat(r.snapshot().gold()).isEqualTo(1000 - 75 + 37); // floor(75/2)
            assertThat(r.snapshot().items()).isEmpty();
        }

        @Test
        void equippedItemCannotBeSold() {
            var c = buyer(1000);
            var entry = new InventoryEntry("greataxe", 1, 1, true);
            c.addItem(entry);
            repo.save(c);
            assertThatThrownBy(() -> shop.sell("buyer", new SellRequest("greataxe", null, null)))
                    .hasMessageContaining("unequip");
        }

        @Test
        void ambiguousTierRequiresDisambiguation() {
            buyer(1000);
            shop.purchase("buyer", new PurchaseRequest("greataxe", null, 1, null));
            shop.purchase("buyer", new PurchaseRequest("greataxe", null, 5, null));
            assertThatThrownBy(() -> shop.sell("buyer", new SellRequest("greataxe", null, null)))
                    .hasMessageContaining("specify tier");
            var r = shop.sell("buyer", new SellRequest("greataxe", null, 5));
            assertThat(r.snapshot().items()).hasSize(1);
            assertThat(r.snapshot().items().get(0).upgradeTier()).isEqualTo(1);
        }
    }

    @Nested
    class Equip {

        @Autowired
        private EquipmentService equipment;

        private GameCharacter withItems(String classId, String pathId, String... itemIds) {
            var c = new GameCharacter("hero");
            c.setName("hero");
            c.setLevel(5);
            c.setPathId(pathId);
            c.setClassId(classId);
            c.setStats(new Stats(14, 14, 12, 16, 10, 10, 10));
            c.setHp(new HitPoints(100, 100, 0));
            c.setMana(new ManaPool(100, 100));
            c.setAp(new ActionPoints(6, 6, 10));
            for (var id : itemIds) {
                c.addItem(new InventoryEntry(id, 1, 1, false));
            }
            return repo.save(c);
        }

        private com.steelmight.charactersheet.dto.EquipRequest eq(String itemId) {
            return new com.steelmight.charactersheet.dto.EquipRequest(itemId, null);
        }

        // Criterion 1 — armor drives AC/PA/MA; a second body armor swaps
        @Test
        void bodyArmorUpdatesDefensesAndSwaps() {
            withItems("barbarian", "warrior", "light-armor", "medium-armor");
            var bare = service.getCombatSnapshot("hero");

            var light = equipment.equip("hero", eq("light-armor"));
            assertThat(light.snapshot().ac()).isNotEqualTo(bare.ac());
            assertThat(light.snapshot().equippedArmor()).isEqualTo("light-armor");

            var medium = equipment.equip("hero", eq("medium-armor"));
            assertThat(medium.snapshot().equippedArmor()).isEqualTo("medium-armor");
            assertThat(medium.resolution().getSteps())
                    .anyMatch(s -> s.rule().equals("unequip") && s.note().contains("light-armor"));
        }

        // Criterion 2 — two-handed ↔ shield blocked both ways; dual-wield rules
        @Test
        void twoHandedWeaponAndShieldBlockEachOther() {
            withItems("barbarian", "warrior", "greataxe", "shield");
            equipment.equip("hero", eq("greataxe"));
            assertThatThrownBy(() -> equipment.equip("hero", eq("shield")))
                    .hasMessageContaining("blocks a shield");

            equipment.unequip("hero", eq("greataxe"));
            equipment.equip("hero", eq("shield"));
            assertThatThrownBy(() -> equipment.equip("hero", eq("greataxe")))
                    .hasMessageContaining("blocks a two-handed weapon");
        }

        @Test
        void twoLightWeaponsDualWieldButMixedSetsReplace() {
            withItems("barbarian", "warrior", "hand-axe", "dagger", "longsword");
            equipment.equip("hero", eq("hand-axe"));
            equipment.equip("hero", eq("dagger"));
            long equippedWeapons = service.getInventorySnapshot("hero").items().stream()
                    .filter(i -> i.equipped()).count();
            assertThat(equippedWeapons).isEqualTo(2); // dual-wield holds both

            // a non-light weapon replaces the whole set — light + non-light never coexist
            equipment.equip("hero", eq("longsword"));
            var items = service.getInventorySnapshot("hero").items();
            assertThat(items.stream().filter(i -> i.equipped()).count()).isEqualTo(1);
            assertThat(items.stream().filter(i -> i.equipped()).findFirst().orElseThrow().itemId())
                    .isEqualTo("longsword");
        }

        // Criterion 3 — non-proficient equip succeeds, penalties surface, casting blocked
        @Test
        void nonProficientArmorListsPenaltiesAndBlocksCasting() {
            var c = withItems("sorcerer", "wizard", "heavy-armor");
            c.getKnownSpells().add("magic-bolt");
            repo.save(c);

            var equipped = equipment.equip("hero", eq("heavy-armor"));
            assertThat(equipped.snapshot().proficiencyPenalties())
                    .anyMatch(p -> p.itemId().equals("heavy-armor")
                            && p.penalty().contains("cannot cast"));

            assertThatThrownBy(() -> service.cast("hero",
                    new com.steelmight.charactersheet.dto.CastRequest("magic-bolt", null)))
                    .hasMessageContaining("non-proficient armor");

            equipment.unequip("hero", eq("heavy-armor"));
            var response = service.cast("hero",
                    new com.steelmight.charactersheet.dto.CastRequest("magic-bolt", null));
            assertThat(response.snapshot().mana().current()).isEqualTo(95);
        }
    }

    @Nested
    class UpgradesAndConsumables {

        @Autowired
        private EquipmentService equipment;

        private GameCharacter carrier(String... itemIds) {
            var c = buyer(1000);
            for (var id : itemIds) {
                c.addItem(new InventoryEntry(id, 1, 1, false));
            }
            return repo.save(c);
        }

        // M5-C criterion 1 — kit: cost one-handed[target-1]/3 = 16/3 = 5; DC 5 + current(1) = 6
        @Test
        void kitUpgradeConsumesCostEvenOnFailure() {
            carrier("longsword");

            FixedRandom.next = 4; // d20 = 5 < DC 6 → failure
            var failed = shop.upgrade("buyer", new com.steelmight.charactersheet.dto.UpgradeRequest(
                    "longsword", null, "kit"));
            assertThat(failed.snapshot().gold()).isEqualTo(995); // Q31: cost lost
            assertThat(failed.snapshot().items().get(0).upgradeTier()).isEqualTo(1);

            FixedRandom.next = 5; // d20 = 6 >= DC 6 → success
            var ok = shop.upgrade("buyer", new com.steelmight.charactersheet.dto.UpgradeRequest(
                    "longsword", null, "kit"));
            assertThat(ok.snapshot().gold()).isEqualTo(990);
            assertThat(ok.snapshot().items().get(0).upgradeTier()).isEqualTo(2);
        }

        // M5-C criterion 2 — greataxe 1→2: (23-15) + 5%×23 = 9
        @Test
        void blacksmithUpgradeMathMatchesTheFormula() {
            carrier("greataxe");
            var r = shop.upgrade("buyer", new com.steelmight.charactersheet.dto.UpgradeRequest(
                    "greataxe", null, "blacksmith"));
            assertThat(r.snapshot().gold()).isEqualTo(1000 - 9);
            assertThat(r.snapshot().items().get(0).upgradeTier()).isEqualTo(2);
        }

        // M5-C criterion 3 — potion on a cursed character: consumed, 0 healed
        @Test
        void healingPotionOnCursedCharacterIsWasted() {
            var c = buyer(100);
            c.getHp().setCurrent(50);
            var potion = new InventoryEntry("small", 2, 3, false); // level 3 → 15 HP normally
            c.addItem(potion);
            repo.save(c);
            service.applyEffect("buyer", new com.steelmight.charactersheet.dto.ApplyEffectRequest(
                    "cursed", 1, null, 2, "test", false, false, false, null));

            var r = shop.useConsumable("buyer", new com.steelmight.charactersheet.dto.UseConsumableRequest(
                    "small", null));

            assertThat(r.snapshot().hp().current()).isEqualTo(50); // cursed: healing × 0
            assertThat(r.snapshot().ap().current()).isEqualTo(5);  // small potion costs 1 AP
            assertThat(r.resolution().getSteps())
                    .anyMatch(s -> s.rule().equals("healing-blocked") && s.note().contains("cursed"));
            assertThat(service.getInventorySnapshot("buyer").items().get(0).quantity()).isEqualTo(1);
        }

        @Test
        void healingPotionHealsByItsOwnLevel() {
            var c = buyer(100);
            c.getHp().setCurrent(50);
            c.addItem(new InventoryEntry("medium", 1, 4, false)); // 10 × 4 = 40
            repo.save(c);

            var r = shop.useConsumable("buyer", new com.steelmight.charactersheet.dto.UseConsumableRequest(
                    "medium", null));
            assertThat(r.snapshot().hp().current()).isEqualTo(90);
            assertThat(service.getInventorySnapshot("buyer").items()).isEmpty(); // last one consumed
        }

        // M5-C criterion 4 — charges decrement on use and restore on rest
        @Test
        void chargesDecrementAndRestoreOnRest() {
            carrier("cloak-of-invisibility"); // usesPerLongRest 3, apCost 2

            shop.useConsumable("buyer", new com.steelmight.charactersheet.dto.UseConsumableRequest(
                    "cloak-of-invisibility", null));
            var second = shop.useConsumable("buyer", new com.steelmight.charactersheet.dto.UseConsumableRequest(
                    "cloak-of-invisibility", null));
            assertThat(second.resolution().getSteps())
                    .anyMatch(s -> s.rule().equals("use-charge") && s.valueAfter() == 1);

            var rested = service.rest("buyer", new com.steelmight.charactersheet.dto.RestRequest(100));
            assertThat(rested.resolution().getSteps())
                    .anyMatch(s -> s.rule().equals("rest-charges") && s.valueAfter() == 3);

            // charges full again: two more uses succeed, a third after depletion fails
            shop.useConsumable("buyer", new com.steelmight.charactersheet.dto.UseConsumableRequest(
                    "cloak-of-invisibility", null));
        }
    }

    @Nested
    class ScrollCasting {

        private com.steelmight.charactersheet.dto.CastScrollRequest read(String itemId, String spellId) {
            return new com.steelmight.charactersheet.dto.CastScrollRequest(itemId, null, spellId, null, null);
        }

        private void buyScroll(String scrollId, int quantity, String spellId) {
            shop.purchase("buyer", new PurchaseRequest(scrollId, quantity, null, null, spellId));
        }

        /** Shops p.17 + 2026-07-07 ruling: the scroll is OF a spell, chosen at purchase;
         *  any character then casts it — no caster gate, no mana. */
        @Test
        void nonCasterCastsTheScrollsBoundSpell() {
            buyer(100); // barbarian — casterType none, mana 0
            buyScroll("major-scroll-1", 2, "magic-bolt"); // 2 × 8g

            var bought = service.getInventorySnapshot("buyer").items().get(0);
            assertThat(bought.spellId()).isEqualTo("magic-bolt");

            FixedRandom.next = 4; // every d10 rolls a 5
            var r = shop.castScroll("buyer", read("major-scroll-1", null)); // spell comes from the scroll

            assertThat(r.snapshot().ap().current()).isEqualTo(3); // magic-bolt costs 3 AP
            assertThat(r.snapshot().mana().current()).isEqualTo(0); // no mana involved
            assertThat(r.resolution().getSteps())
                    .extracting("rule")
                    .contains("consume", "spend-ap", "roll-damage");
            @SuppressWarnings("unchecked")
            var damage = (java.util.Map<String, Object>) r.resolution().getPayload().get("damage");
            assertThat(damage.get("total")).isEqualTo(19); // 2×5 + 9 + spell mod 0 (non-caster)

            // one of the two scrolls consumed
            assertThat(service.getInventorySnapshot("buyer").items().get(0).quantity()).isEqualTo(1);
        }

        /** The spell is validated when BUYING the scroll. */
        @Test
        void purchaseValidatesTheScrollSpell() {
            buyer(1000);

            assertThatThrownBy(() -> buyScroll("major-scroll-1", 1, null))
                    .hasMessageContaining("spellId is required");

            // level mismatch: veil-crevasse is a level-2 sorcerer spell
            assertThatThrownBy(() -> buyScroll("major-scroll-1", 1, "veil-crevasse"))
                    .hasMessageContaining("level-1 spell");

            // tier mismatch: aimed-shot belongs to the arcane-ranger (minor caster)
            assertThatThrownBy(() -> buyScroll("major-scroll-1", 1, "aimed-shot"))
                    .hasMessageContaining("major-caster spell");

            // spellId is scroll-only
            assertThatThrownBy(() -> shop.purchase("buyer",
                    new PurchaseRequest("greataxe", 1, 1, null, "magic-bolt")))
                    .hasMessageContaining("only applies to scrolls");

            assertThat(service.getInventorySnapshot("buyer").gold()).isEqualTo(1000); // nothing spent
        }

        /** Scrolls of different spells are separate stacks and need disambiguation. */
        @Test
        void differentSpellsStackSeparatelyAndDisambiguate() {
            buyer(100);
            buyScroll("major-scroll-1", 1, "magic-bolt");
            buyScroll("major-scroll-1", 1, "nether-zone");
            assertThat(service.getInventorySnapshot("buyer").items()).hasSize(2);

            assertThatThrownBy(() -> shop.castScroll("buyer", read("major-scroll-1", null)))
                    .hasMessageContaining("specify spellId");

            var r = shop.castScroll("buyer", read("major-scroll-1", "nether-zone"));
            assertThat(r.resolution().getSteps())
                    .anyMatch(s -> s.note().contains("nether-zone")
                            || s.note().contains("Nether zone"));
            // the magic-bolt scroll is untouched
            var left = service.getInventorySnapshot("buyer").items();
            assertThat(left).hasSize(1);
            assertThat(left.get(0).spellId()).isEqualTo("magic-bolt");
        }

        /** DM-granted entries without a written spell cannot be cast (set via inventory edit). */
        @Test
        void unwrittenScrollCannotBeCast() {
            var c = buyer(100);
            c.addItem(new InventoryEntry("major-scroll-1", 1, 0, false));
            repo.save(c);
            assertThatThrownBy(() -> shop.castScroll("buyer", read("major-scroll-1", null)))
                    .hasMessageContaining("no spell is written");
        }

        @Test
        void minCharLevelGates() {
            var c = buyer(100); // level 5
            c.addItem(new InventoryEntry("minor-scroll-3", 1, 0, false)); // minCharLevel 9
            repo.save(c);
            assertThatThrownBy(() -> shop.castScroll("buyer", read("minor-scroll-3", null)))
                    .hasMessageContaining("requires character level 9");
        }

        @Test
        void notCarryingTheScrollIs400() {
            buyer(100);
            assertThatThrownBy(() -> shop.castScroll("buyer", read("major-scroll-1", null)))
                    .hasMessageContaining("not carrying");
        }

        @Test
        void nonScrollItemIsRejected() {
            var c = buyer(100);
            c.addItem(new InventoryEntry("greataxe", 1, 1, false));
            repo.save(c);
            assertThatThrownBy(() -> shop.castScroll("buyer", read("greataxe", null)))
                    .hasMessageContaining("not a spell scroll");
        }
    }

    @Nested
    class Currency {

        /** ONE generic gold currency (Game Owner 2026-07-06) — the snapshot carries
         *  the stored value as-is, no denomination breakdown. */
        @Test
        void singleCurrencyPassesThroughUnchanged() {
            buyer(1234);
            assertThat(service.getInventorySnapshot("buyer").gold()).isEqualTo(1234);
        }
    }
}
