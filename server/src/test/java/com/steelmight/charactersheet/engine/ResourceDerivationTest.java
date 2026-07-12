package com.steelmight.charactersheet.engine;

import com.steelmight.charactersheet.TestCharacterFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M3 Part A acceptance criteria 1-4 (+ martyr sentinel): class-resource maxima are
 * derived from class-abilities.json tables (N8: tables over prose), never hand-set.
 */
@SpringBootTest
class ResourceDerivationTest {

    @Autowired
    private StatDerivationEngine engine;

    private Integer maxFor(int level, String pathId, String classId) {
        return engine.computeClassResourceMax(TestCharacterFactory.character(level, pathId, classId));
    }

    // Criterion 1 — barbarian rages from resourcePerLevel

    @Test
    void barbarianRagesFollowTheTable() {
        assertThat(maxFor(7, "warrior", "barbarian")).isEqualTo(5); // resourcePerLevel[6]
        assertThat(maxFor(13, "warrior", "barbarian")).isEqualTo(6);
    }

    // Criterion 2 — tormentor curses (added table, C&S p.95)

    @Test
    void tormentorCursesFollowTheLadder() {
        assertThat(maxFor(1, "wraith-hunter", "tormentor")).isZero(); // L1: none
        assertThat(maxFor(2, "wraith-hunter", "tormentor")).isEqualTo(2);
        assertThat(maxFor(19, "wraith-hunter", "tormentor")).isEqualTo(8);
    }

    // Criterion 3 — phantom energy (added table, C&S p.102)

    @Test
    void phantomEnergyIsZeroAtOneThenFlatHundred() {
        assertThat(maxFor(1, "wraith-hunter", "phantom")).isZero();
        assertThat(maxFor(2, "wraith-hunter", "phantom")).isEqualTo(100);
        assertThat(maxFor(20, "wraith-hunter", "phantom")).isEqualTo(100);
    }

    // Criterion 4 — shapeshifter pool derives from HP, doubles at 20

    @Test
    void shapeshifterPoolIsHalfMaxHpDoubledAtTwenty() {
        var atFive = TestCharacterFactory.character(5, "wildborn", "shapeshifter");
        int expected = engine.computeMaxHP(atFive) / 2;
        assertThat(engine.computeClassResourceMax(atFive)).isEqualTo(expected);

        var atTwenty = TestCharacterFactory.character(20, "wildborn", "shapeshifter");
        assertThat(engine.computeClassResourceMax(atTwenty))
                .isEqualTo((engine.computeMaxHP(atTwenty) / 2) * 2); // Metamorph
    }

    // Criterion 5 (derivation half) — martyr focus is an unbounded builder

    @Test
    void martyrFocusIsUnbounded() {
        assertThat(maxFor(5, "monk", "martyr")).isEqualTo(StatDerivationEngine.UNBOUNDED_RESOURCE);
        assertThat(engine.isBuilderResource("focus")).isTrue();
    }

    // Mana casters and resourceless classes derive nothing here

    @Test
    void manaCastersAndResourcelessClassesHaveNoClassResource() {
        assertThat(maxFor(5, "musician", "bard")).isNull(); // mana → computeMaxMana
        assertThat(maxFor(5, "archer", "marksman")).isNull();
    }
}
