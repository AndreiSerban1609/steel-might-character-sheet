package com.steelmight.charactersheet.service;

import com.steelmight.charactersheet.dto.GainResourceRequest;
import com.steelmight.charactersheet.dto.RestRequest;
import com.steelmight.charactersheet.dto.SpendResourceRequest;
import com.steelmight.charactersheet.model.*;
import com.steelmight.charactersheet.repository.CharacterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Story 1.2 — generic sub-resource pools: materialization, spend/gain, tier-scaled rest. */
@SpringBootTest
class CharacterPoolsTest {

    @Autowired
    private CharacterService service;

    @Autowired
    private CharacterRepository repo;

    @BeforeEach
    void setUp() {
        repo.deleteAll();
    }

    private GameCharacter save(String id, String pathId, String classId, int level) {
        var c = new GameCharacter(id);
        c.setName("Test " + classId);
        c.setLevel(level);
        c.setPathId(pathId);
        c.setClassId(classId);
        c.setStats(new Stats(14, 12, 14, 10, 10, 12, 10));
        c.setHp(new HitPoints(100, 100, 0));
        c.setMana(new ManaPool(0, 0));
        c.setAp(new ActionPoints(6, 6, 10));
        c.setResource(new ClassResource("energy", 30, 30));
        return repo.save(c);
    }

    @Test
    void poolsMaterializeAtTheirUnlockLevel() {
        save("conq5", "warrior", "conqueror", 5);
        var snapshot = service.getCombatSnapshot("conq5");
        assertThat(snapshot.pools()).hasSize(1);
        assertThat(snapshot.pools().get(0).id()).isEqualTo("perseverance");
        assertThat(snapshot.pools().get(0).name()).isEqualTo("Perseverance");
        assertThat(snapshot.pools().get(0).current()).isEqualTo(2);
        assertThat(snapshot.pools().get(0).max()).isEqualTo(2);
    }

    @Test
    void poolsBelowUnlockLevelDoNotMaterialize() {
        save("conq1", "warrior", "conqueror", 1); // perseverance unlocks at 4
        assertThat(service.getCombatSnapshot("conq1").pools()).isEmpty();
    }

    @Test
    void formulaPoolsAreSkipped() {
        save("shape5", "wildborn", "shapeshifter", 5); // shapeshift-hp has maxFormula only
        assertThat(service.getCombatSnapshot("shape5").pools()).isEmpty();
    }

    @Test
    void spendValidatesAndGainCaps() {
        save("conq", "warrior", "conqueror", 5);
        var spent = service.spendResource("conq", new SpendResourceRequest("perseverance", 1));
        assertThat(spent.snapshot().pools().get(0).current()).isEqualTo(1);

        assertThatThrownBy(() -> service.spendResource("conq", new SpendResourceRequest("perseverance", 5)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Insufficient perseverance");

        var gained = service.gainResource("conq", new GainResourceRequest("perseverance", 10));
        assertThat(gained.snapshot().pools().get(0).current()).isEqualTo(2); // capped at max

        assertThatThrownBy(() -> service.spendResource("conq", new SpendResourceRequest("nonsense", 1)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unknown resource");
    }

    @Test
    void furyMayGoNegativeAndIsNotRestRestored() {
        save("barb", "warrior", "barbarian", 5);
        var c = repo.findById("barb").orElseThrow();
        c.setResource(new ClassResource("rages", 4, 4));
        repo.save(c);

        // min is declared on the fury def → the spend goes through and the pool goes negative (B12)
        var spent = service.spendResource("barb", new SpendResourceRequest("fury", 3));
        var fury = spent.snapshot().pools().stream()
                .filter(pool -> pool.id().equals("fury")).findFirst().orElseThrow();
        assertThat(fury.current()).isEqualTo(-3);

        // restore: "manual" → rest does not touch it
        var rested = service.rest("barb", new RestRequest(100));
        var afterRest = rested.snapshot().pools().stream()
                .filter(pool -> pool.id().equals("fury")).findFirst().orElseThrow();
        assertThat(afterRest.current()).isEqualTo(-3);
    }

    @Test
    void restRegainsCeilTierTimesMax() {
        save("conq", "warrior", "conqueror", 5);
        service.spendResource("conq", new SpendResourceRequest("perseverance", 1));
        service.spendResource("conq", new SpendResourceRequest("perseverance", 1)); // 0/2

        // 25% rest: ceil(0.25 × 2) = 1
        var poor = service.rest("conq", new RestRequest(25));
        assertThat(poolCurrent(poor.snapshot().pools(), "perseverance")).isEqualTo(1);
        assertThat(poor.resolution().getSteps())
                .anyMatch(s -> s.rule().equals("rest-pool"));

        // 100% rest from 1/2: ceil(1.0 × 2) = 2, capped at 2
        var full = service.rest("conq", new RestRequest(100));
        assertThat(poolCurrent(full.snapshot().pools(), "perseverance")).isEqualTo(2);
    }

    @Test
    void legacyCharactersMaterializeOnFirstRead() {
        // saved without any pool rows — first snapshot read heals the character
        save("legacy", "warrior", "conqueror", 8);
        var snapshot = service.getCombatSnapshot("legacy");
        assertThat(snapshot.pools()).hasSize(1);
        // and the materialization persisted
        assertThat(repo.findById("legacy").orElseThrow().getPools()).hasSize(1);
    }

    private static int poolCurrent(java.util.List<com.steelmight.charactersheet.dto.CombatSnapshot.PoolView> pools,
                                   String id) {
        return pools.stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow().current();
    }
}
