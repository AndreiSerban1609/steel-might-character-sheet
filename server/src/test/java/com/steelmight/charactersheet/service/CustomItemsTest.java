package com.steelmight.charactersheet.service;

import com.steelmight.charactersheet.dto.CustomItemView;
import com.steelmight.charactersheet.dto.EquipRequest;
import com.steelmight.charactersheet.dto.UpdateCustomItemsRequest;
import com.steelmight.charactersheet.dto.UpdateInventoryRequest;
import com.steelmight.charactersheet.model.*;
import com.steelmight.charactersheet.repository.CharacterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Custom weapons & armor (demo feedback #19). The point of the design is that homebrew
 * gear is indistinguishable from catalog gear downstream, so these tests check the
 * MECHANICS (AC, PA, equip rules, attacks), not just that rows persist.
 */
@SpringBootTest
class CustomItemsTest {

    @Autowired private CharacterService service;
    @Autowired private EquipmentService equipment;
    @Autowired private CharacterRepository repo;

    @BeforeEach
    void setUp() {
        repo.deleteAll();
        var c = new GameCharacter("p1");
        c.setName("Homebrewer");
        c.setRoomName("custom-room");
        c.setLevel(5);
        c.setPathId("warrior");
        c.setClassId("guardian");
        c.setStats(new Stats(10, 10, 10, 10, 10, 10, 10));
        c.setHp(new HitPoints(100, 125, 0));
        c.setMana(new ManaPool(0, 0));
        c.setAp(new ActionPoints(6, 6, 10));
        c.setSpeed(30);
        repo.save(c);
    }

    private static CustomItemView weapon(String name, String dice, int flat, String type) {
        return new CustomItemView(null, name, "WEAPON", 2.0, "two-handed", true,
                dice, flat, type, 0, "str", 3,
                null, null, null, null, null, null, null);
    }

    private static CustomItemView armor(String name, String armorType, int acBase, int pa, int ma) {
        return new CustomItemView(null, name, "ARMOR", 3.0, "", true,
                null, null, null, null, null, null,
                armorType, acBase, false, pa, ma, 0, 0);
    }

    /** Adds one item, keeping what's already carried (updateInventory is a full replace). */
    private void carry(String itemId) {
        var existing = service.getInventorySnapshot("p1").items().stream()
                .map(e -> new UpdateInventoryRequest.ItemInput(
                        e.itemId(), e.quantity(), e.upgradeTier(), e.equipped(), null))
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        existing.add(new UpdateInventoryRequest.ItemInput(itemId, 1, 0, false, null));
        service.updateInventory("p1", new UpdateInventoryRequest(existing, null));
    }

    @Test
    void definingAnItemAssignsAStableSlugId() {
        var saved = service.updateCustomItems("p1", new UpdateCustomItemsRequest(
                List.of(weapon("Sword of the Thing", "1d12", 7, "slashing"))));

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).id()).isEqualTo("custom-sword-of-the-thing");

        // Re-saving with the id keeps it — inventory rows referencing it must not dangle.
        var renamed = service.updateCustomItems("p1", new UpdateCustomItemsRequest(
                List.of(new CustomItemView(saved.get(0).id(), "Renamed Blade", "WEAPON",
                        2.0, "", true, "1d12", 7, "slashing", 0, "str", 3,
                        null, null, null, null, null, null, null))));
        assertThat(renamed.get(0).id()).isEqualTo("custom-sword-of-the-thing");
        assertThat(renamed.get(0).name()).isEqualTo("Renamed Blade");
    }

    @Test
    void customArmorFeedsAcPaAndMa() {
        var saved = service.updateCustomItems("p1", new UpdateCustomItemsRequest(
                List.of(armor("Dragonplate", "heavy", 17, 6, 5))));
        carry(saved.get(0).id());
        equipment.equip("p1", new EquipRequest(saved.get(0).id(), null));

        var snap = service.getCombatSnapshot("p1");
        assertThat(snap.ac()).isEqualTo(17);
        assertThat(snap.pa()).isEqualTo(6);
        assertThat(snap.ma()).isEqualTo(5);
        assertThat(snap.equippedArmor()).isEqualTo(saved.get(0).id());
    }

    @Test
    void customWeaponIsAttackableAndCarriesItsOwnWeight() {
        var saved = service.updateCustomItems("p1", new UpdateCustomItemsRequest(
                List.of(weapon("Thunderaxe", "1d12", 7, "thunder"))));
        String id = saved.get(0).id();
        carry(id);
        equipment.equip("p1", new EquipRequest(id, null));

        var snap = service.getCombatSnapshot("p1");
        assertThat(snap.equippedWeapons()).contains(id);

        var attack = service.weaponAttack("p1", null);
        assertThat(attack.resolution().getSteps())
                .anyMatch(s -> s.rule().equals("attack-roll"));

        // inventorySpace 2.0 came from the definition, not the unknown-item default of 1.0
        assertThat(service.getInventorySnapshot("p1").carriedSpace()).isEqualTo(2.0);
    }

    @Test
    void customTwoHandedWeaponStillBlocksAShield() {
        var saved = service.updateCustomItems("p1", new UpdateCustomItemsRequest(
                List.of(weapon("Big Stick", "1d12", 7, "crushing"))));
        carry(saved.get(0).id());
        carry("shield");
        equipment.equip("p1", new EquipRequest(saved.get(0).id(), null));

        assertThatThrownBy(() -> equipment.equip("p1", new EquipRequest("shield", null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("two-handed");
    }

    @Test
    void nonProficientCustomGearStillEarnsThePenalty() {
        var view = new CustomItemView(null, "Alien Rifle", "WEAPON", 1.0, "", false,
                "1d10", 4, "piercing", 0, "dex", 3,
                null, null, null, null, null, null, null);
        var saved = service.updateCustomItems("p1", new UpdateCustomItemsRequest(List.of(view)));
        carry(saved.get(0).id());
        equipment.equip("p1", new EquipRequest(saved.get(0).id(), null));

        assertThat(service.getCombatSnapshot("p1").proficiencyPenalties())
                .anyMatch(p -> p.itemId().equals(saved.get(0).id()));
    }

    @Test
    void deletingACarriedDefinitionIsRejected() {
        var saved = service.updateCustomItems("p1", new UpdateCustomItemsRequest(
                List.of(weapon("Keeper", "1d6", 2, "slashing"))));
        carry(saved.get(0).id());

        assertThatThrownBy(() -> service.updateCustomItems("p1", new UpdateCustomItemsRequest(List.of())))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("still in the inventory");
    }

    @Test
    void rejectsJunkDefinitions() {
        assertThatThrownBy(() -> service.updateCustomItems("p1", new UpdateCustomItemsRequest(
                List.of(weapon("Bad Type", "1d6", 1, "sonic")))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("damage type");

        assertThatThrownBy(() -> service.updateCustomItems("p1", new UpdateCustomItemsRequest(
                List.of(armor("Bad Armor", "plaid", 12, 1, 1)))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("light/medium/heavy/shield");

        assertThatThrownBy(() -> service.updateCustomItems("p1", new UpdateCustomItemsRequest(
                List.of(new CustomItemView(null, "", "WEAPON", 1.0, "", true, "1d6", 1,
                        "slashing", 0, "str", 3, null, null, null, null, null, null, null)))))
                .isInstanceOf(ResponseStatusException.class);
    }
}
