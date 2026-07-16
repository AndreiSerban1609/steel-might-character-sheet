package com.steelmight.charactersheet.gamedata;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/** Story 1.1 — the six abilities-*.json files load into typed definitions at startup. */
@SpringBootTest
class AbilityDataTest {

    @Autowired
    private GameDataProvider gameData;

    @Test
    void allThirteenNonCasterClassesHaveAbilities() {
        var classes = new String[]{"conqueror", "barbarian", "shapeshifter", "shaolin", "martyr",
                "hierophant", "marksman", "hunter", "assassin", "burglar",
                "runekeeper", "tormentor", "phantom"};
        for (var classId : classes) {
            assertThat(gameData.getAbilitiesForClass(classId))
                    .as("abilities for %s", classId)
                    .isNotEmpty();
        }
    }

    @Test
    void whirlwindLoadsFullyTyped() {
        var whirlwind = gameData.getAbility("whirlwind");
        assertThat(whirlwind).isNotNull();
        assertThat(whirlwind.classId()).isEqualTo("conqueror");
        assertThat(whirlwind.minLevel()).isEqualTo(2);
        assertThat(whirlwind.group()).isEqualTo("offensive-maneuver-minor");
        assertThat(whirlwind.kind()).isEqualTo("attack-enhancer");
        assertThat(whirlwind.resolution()).isEqualTo("manual");
        assertThat(whirlwind.costs()).hasSize(1);
        assertThat(whirlwind.costs().get(0).resource()).isEqualTo("energy");
        assertThat(whirlwind.costs().get(0).amount()).isEqualTo(15);
    }

    @Test
    void formulaFieldsParse() {
        // (level)d10 + level × CON heal, perseverance cost, level-10 energy rider
        var perseverance = gameData.getAbility("indomitable-perseverance");
        assertThat(perseverance.healing().dice().countPerLevel()).isEqualTo(1);
        assertThat(perseverance.healing().dice().sides()).isEqualTo(10);
        assertThat(perseverance.healing().statFlat().stat()).isEqualTo("CON");
        assertThat(perseverance.healing().statFlat().perLevel()).isTrue();
        assertThat(perseverance.riders()).hasSize(1);
        assertThat(perseverance.riders().get(0).minLevel()).isEqualTo(10);

        // stack formula with divisor + rounding
        var cripplingStrike = gameData.getAbility("crippling-strike");
        assertThat(cripplingStrike.targetEffect().effectId()).isEqualTo("rooted");
        assertThat(cripplingStrike.targetEffect().stacks().levelDivisor()).isEqualTo(3);
        assertThat(cripplingStrike.targetEffect().stacks().round()).isEqualTo("up");

        // uses-per-rest by stat with minimum + per-turn limit
        var adrenaline = gameData.getAbility("adrenaline");
        assertThat(adrenaline.usesPerRest().stat()).isEqualTo("WILL");
        assertThat(adrenaline.usesPerRest().min()).isEqualTo(1);
        assertThat(adrenaline.usesPerTurn()).isEqualTo(1);
        assertThat(adrenaline.nextTurnApPenalty()).isEqualTo(3);
    }

    @Test
    void poolDefinitionsLoad() {
        var conquerorPools = gameData.getPoolsForClass("conqueror");
        assertThat(conquerorPools).hasSize(1);
        var perseverance = conquerorPools.get(0);
        assertThat(perseverance.id()).isEqualTo("perseverance");
        assertThat(perseverance.unlockLevel()).isEqualTo(4);
        assertThat(perseverance.initial()).isEqualTo(2);
        assertThat(perseverance.max()).isEqualTo(2);
        assertThat(perseverance.restore()).isEqualTo("on-rest");

        var fury = gameData.getPoolsForClass("barbarian").get(0);
        assertThat(fury.id()).isEqualTo("fury");
        assertThat(fury.min()).isEqualTo(0);
        assertThat(fury.restore()).isEqualTo("manual");

        // formula pool (shapeshift-hp) loads but has no numeric initial
        var shapeshifterPools = gameData.getPoolsForClass("shapeshifter");
        assertThat(shapeshifterPools).hasSize(1);
        assertThat(shapeshifterPools.get(0).maxFormula()).isNotNull();
        assertThat(shapeshifterPools.get(0).initial()).isNull();

        assertThat(gameData.getPoolsForClass("bard")).isEmpty();
        assertThat(gameData.getPoolsForClass(null)).isEmpty();
    }
}
