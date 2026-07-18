package com.steelmight.charactersheet.service;

import com.steelmight.charactersheet.dto.ApplyEffectRequest;
import com.steelmight.charactersheet.dto.GainResourceRequest;
import com.steelmight.charactersheet.dto.RestRequest;
import com.steelmight.charactersheet.dto.SpendResourceRequest;
import com.steelmight.charactersheet.engine.RandomSource;
import com.steelmight.charactersheet.model.*;
import com.steelmight.charactersheet.repository.CharacterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M3 Part B/C acceptance criteria 6-9. The RandomSource is fixed so the
 * fractional-charge roll (criterion 7) is deterministic in both directions.
 */
@SpringBootTest
@Import(RestActionsTest.FixedRandom.class)
class RestActionsTest {

    @TestConfiguration
    static class FixedRandom {
        /** nextInt(bound) returns this value; set per test. */
        static int next = 0;

        @Bean
        @Primary
        RandomSource fixedRandomSource() {
            return bound -> Math.min(next, bound - 1);
        }
    }

    @Autowired
    private CharacterService service;

    @Autowired
    private CharacterRepository repo;

    @BeforeEach
    void setUp() {
        repo.deleteAll();
        FixedRandom.next = 0;
    }

    private GameCharacter seed(String id, String pathId, String classId, int level) {
        var c = new GameCharacter(id);
        c.setName(id);
        c.setLevel(level);
        c.setPathId(pathId);
        c.setClassId(classId);
        c.setStats(new Stats(10, 10, 10, 10, 10, 14, 10));
        c.setHp(new HitPoints(1, 1, 0)); // hp.max column is stale by design; derived max rules
        c.setMana(new ManaPool(0, 0));
        c.setAp(new ActionPoints(6, 6, 10));
        return repo.save(c);
    }

    private void applyEffect(String id, String effectId, Integer stacks, Integer duration, DurationType type) {
        service.applyEffect(id, new ApplyEffectRequest(effectId, stacks, null, duration, "test",
                false, false, false, type));
    }

    // Criterion 6 — full rest

    @Test
    void anyRestClearsMultiInstanceDotStacks() {
        // N10: burning/envenomed are exempt from threshold consumption (multiInstance)
        // but their accumulated stacks still dissipate on ANY rest — even a poor one.
        seed("barb", "warrior", "barbarian", 5);
        applyEffect("barb", "burning", 3, null, null);
        applyEffect("barb", "envenomed", 2, null, null);

        var snap = service.rest("barb", new RestRequest(25)).snapshot();

        assertThat(snap.activeEffects()).noneMatch(e -> e.id().equals("burning"));
        assertThat(snap.activeEffects()).noneMatch(e -> e.id().equals("envenomed"));
    }

    @Nested
    class FullRest {
        @Test
        void restoresHpManaClearsEffectsStacksTempAndPreparedSpells() {
            var c = seed("bard", "musician", "bard", 5); // derived max HP 125, mana 275
            c.getHp().setCurrent(50);
            c.getMana().setCurrent(100);
            c.getPreparedSpells().add("some-spell");
            c.setDeathStacks(2);
            repo.save(c);

            service.applyEffect("bard", new ApplyEffectRequest("temporary-hp", 1, 10, null, "test",
                    false, true, false, null));
            applyEffect("bard", "haste", 1, null, DurationType.UNTIL_LONG_REST);
            applyEffect("bard", "taunted", 1, 3, null); // ROUNDS — survives the rest
            applyEffect("bard", "dazed", 2, null, null); // dormant threshold stacks — cleared (N10)

            var snap = service.rest("bard", new RestRequest(100)).snapshot();

            assertThat(snap.hp().current()).isEqualTo(125);
            assertThat(snap.hp().temp()).isZero();
            assertThat(snap.mana().current()).isEqualTo(275);
            assertThat(snap.activeEffects()).noneMatch(e -> e.id().equals("temporary-hp"));
            assertThat(snap.activeEffects()).noneMatch(e -> e.id().equals("haste"));
            assertThat(snap.activeEffects()).noneMatch(e -> e.id().equals("dazed"));
            assertThat(snap.activeEffects()).anyMatch(e -> e.id().equals("taunted"));
            assertThat(snap.deathStacks()).isEqualTo(2); // never reset by rest
            assertThat(service.getSpellbookSnapshot("bard").preparedSpells()).isEmpty();
        }

        @Test
        void chargeAndPoolResourcesRefillAndBuilderResets() {
            var barb = seed("barb", "warrior", "barbarian", 7); // rages max 5
            barb.setResource(new ClassResource("rages", 1, 0));
            repo.save(barb);
            var snapBarb = service.rest("barb", new RestRequest(100));
            assertThat(repo.findById("barb").orElseThrow().getResource().getCurrent()).isEqualTo(5);
            assertThat(repo.findById("barb").orElseThrow().getResource().getMax()).isEqualTo(5); // synced

            var monk = seed("shaolin", "monk", "shaolin", 5); // chakra max 10
            monk.setResource(new ClassResource("chakra", 3, 0));
            repo.save(monk);
            service.rest("shaolin", new RestRequest(100));
            assertThat(repo.findById("shaolin").orElseThrow().getResource().getCurrent()).isEqualTo(10);

            var tormentor = seed("tormentor", "wraith-hunter", "tormentor", 2); // curses max 2
            tormentor.setResource(new ClassResource("curses", 0, 0));
            repo.save(tormentor);
            service.rest("tormentor", new RestRequest(100));
            assertThat(repo.findById("tormentor").orElseThrow().getResource().getCurrent()).isEqualTo(2);

            var martyr = seed("martyr", "monk", "martyr", 5); // focus: builder → 0
            martyr.setResource(new ClassResource("focus", 40, 0));
            repo.save(martyr);
            service.rest("martyr", new RestRequest(100));
            assertThat(repo.findById("martyr").orElseThrow().getResource().getCurrent()).isZero();
        }
    }

    // Criterion 5 (action half) — focus spends on current only, gains uncapped

    @Nested
    class MartyrFocus {
        @Test
        void focusSpendsAgainstCurrentAndGainsWithoutCap() {
            var martyr = seed("martyr", "monk", "martyr", 5);
            martyr.setResource(new ClassResource("focus", 0, 0));
            repo.save(martyr);

            service.gainResource("martyr", new GainResourceRequest("focus", 250));
            assertThat(repo.findById("martyr").orElseThrow().getResource().getCurrent()).isEqualTo(250);

            service.spendResource("martyr", new SpendResourceRequest("focus", 200));
            assertThat(repo.findById("martyr").orElseThrow().getResource().getCurrent()).isEqualTo(50);

            assertThatThrownBy(() -> service.spendResource("martyr", new SpendResourceRequest("focus", 51)))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Insufficient focus");
        }
    }

    // Criterion 7 — fractional charge restoration via the seeded RandomSource

    @Nested
    class FractionalCharges {
        @Test
        void tierFiftyWithThreeChargesGivesOneCertainAndOneProbabilistic() {
            // Barbarian level 3 → rages max 3; tier 50 → 1.5: 1 certain + 50% odds.
            var barb = seed("barb", "warrior", "barbarian", 3);
            barb.setResource(new ClassResource("rages", 0, 0));
            repo.save(barb);

            FixedRandom.next = 10; // 10 < 50 → the extra charge lands
            service.rest("barb", new RestRequest(50));
            assertThat(repo.findById("barb").orElseThrow().getResource().getCurrent()).isEqualTo(2);

            var barb2 = seed("barb2", "warrior", "barbarian", 3);
            barb2.setResource(new ClassResource("rages", 0, 0));
            repo.save(barb2);

            FixedRandom.next = 90; // 90 >= 50 → no extra
            service.rest("barb2", new RestRequest(50));
            assertThat(repo.findById("barb2").orElseThrow().getResource().getCurrent()).isEqualTo(1);
        }
    }

    // Criterion 8 — tier → percent mapping and validation

    @Nested
    class Tiers {
        @Test
        void tierPercentagesMapAndInvalidTierRejected() {
            var c = seed("bard", "musician", "bard", 5); // max HP 125
            c.getHp().setCurrent(0);
            repo.save(c);
            var snap = service.rest("bard", new RestRequest(25)).snapshot();
            assertThat(snap.hp().current()).isEqualTo(31); // floor(125 × 0.25)

            snap = service.rest("bard", new RestRequest(75)).snapshot();
            assertThat(snap.hp().current()).isEqualTo(31 + 93); // +floor(125 × 0.75)

            snap = service.rest("bard", null).snapshot(); // omitted → 100%
            assertThat(snap.hp().current()).isEqualTo(125);

            assertThatThrownBy(() -> service.rest("bard", new RestRequest(40)))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("tier");
        }
    }

    // Criterion 9 — durationType migration defaults

    @Nested
    class Migration {
        @Test
        void rowsWithoutExplicitTypeDeriveFromRemainingRounds() {
            var timed = new ActiveEffect("haste", "spell", 1, null, 3, 0);
            assertThat(timed.getDurationType()).isEqualTo(DurationType.ROUNDS);

            var untimed = new ActiveEffect("haste", "spell", 1, null, null, 0);
            assertThat(untimed.getDurationType()).isEqualTo(DurationType.UNTIL_DISPELLED);

            var explicit = new ActiveEffect("haste", "spell", 1, null, null, 0);
            explicit.setDurationType(DurationType.UNTIL_LONG_REST);
            assertThat(explicit.getDurationType()).isEqualTo(DurationType.UNTIL_LONG_REST);
        }
    }
}
