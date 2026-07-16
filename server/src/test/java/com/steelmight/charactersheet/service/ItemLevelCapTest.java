package com.steelmight.charactersheet.service;

import com.steelmight.charactersheet.dto.PurchaseRequest;
import com.steelmight.charactersheet.dto.UpgradeRequest;
import com.steelmight.charactersheet.model.*;
import com.steelmight.charactersheet.repository.CharacterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Gear level cap (Game Owner 2026-07-13): a level 3 conqueror cannot have a level 4 pike. */
@SpringBootTest
class ItemLevelCapTest {

    @Autowired
    private ShopService shop;

    @Autowired
    private CharacterRepository repo;

    @BeforeEach
    void setUp() {
        repo.deleteAll();
        var c = new GameCharacter("lvl3");
        c.setName("Level Three");
        c.setLevel(3);
        c.setPathId("warrior");
        c.setClassId("conqueror");
        c.setStats(new Stats(16, 10, 12, 10, 10, 10, 10));
        c.setHp(new HitPoints(90, 90, 0));
        c.setMana(new ManaPool(0, 0));
        c.setAp(new ActionPoints(6, 6, 10));
        c.setGold(1_000_000);
        repo.save(c);
    }

    @Test
    void cannotBuyGearAboveCharacterLevel() {
        assertThatThrownBy(() -> shop.purchase("lvl3", new PurchaseRequest("pike", 1, 4, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("cannot buy level 4 gear at character level 3");
        // at-level gear is fine
        var bought = shop.purchase("lvl3", new PurchaseRequest("pike", 1, 3, null));
        assertThat(bought.snapshot().items()).anyMatch(i -> i.itemId().equals("pike"));
    }

    @Test
    void cannotUpgradeGearAboveCharacterLevel() {
        shop.purchase("lvl3", new PurchaseRequest("pike", 1, 3, null));
        assertThatThrownBy(() -> shop.upgrade("lvl3",
                new UpgradeRequest("pike", 3, "blacksmith")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("cannot upgrade past your level (3)");
    }
}
