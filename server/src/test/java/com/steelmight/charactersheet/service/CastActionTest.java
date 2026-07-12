package com.steelmight.charactersheet.service;

import com.steelmight.charactersheet.dto.ApplyEffectRequest;
import com.steelmight.charactersheet.dto.CastRequest;
import com.steelmight.charactersheet.dto.CombatSnapshot;
import com.steelmight.charactersheet.dto.DamageRequest;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M4-A acceptance criteria 2-5 (validation chain + all-or-nothing spend) and
 * M4-B criteria 1-3 (seeded dice + upcasting). Grows per M4 slice.
 */
@SpringBootTest
@Import(CastActionTest.FixedRandom.class)
class CastActionTest {

    @TestConfiguration
    static class FixedRandom {
        /** nextInt(bound) returns min(next, bound - 1); each die lands on next + 1. */
        static int next = 4;

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
        FixedRandom.next = 4; // every d10/d6 rolls a 5
    }

    /** INT 16 → +3 spell modifier; mana 100, AP 6/10. */
    private GameCharacter sorcerer(int level, String... knownSpells) {
        var c = new GameCharacter("sorcerer");
        c.setName("sorcerer");
        c.setLevel(level);
        c.setPathId("wizard");
        c.setClassId("sorcerer");
        c.setStats(new Stats(10, 10, 10, 16, 10, 14, 10));
        c.setHp(new HitPoints(100, 100, 0));
        c.setMana(new ManaPool(100, 100));
        c.setAp(new ActionPoints(6, 6, 10));
        c.getKnownSpells().addAll(List.of(knownSpells));
        return repo.save(c);
    }

    /** WILL 10 → +0 on the concentration save; mana 1000 covers bard spell costs. */
    private GameCharacter bard(int level, String... knownSpells) {
        var c = new GameCharacter("bard");
        c.setName("bard");
        c.setLevel(level);
        c.setPathId("musician");
        c.setClassId("bard");
        c.setStats(new Stats(10, 10, 10, 10, 10, 10, 16));
        c.setHp(new HitPoints(100, 100, 0));
        c.setMana(new ManaPool(1000, 1000));
        c.setAp(new ActionPoints(10, 6, 10));
        c.getKnownSpells().addAll(List.of(knownSpells));
        return repo.save(c);
    }

    private static List<String> effectIds(CombatSnapshot snapshot) {
        return snapshot.activeEffects().stream().map(CombatSnapshot.EffectView::id).toList();
    }

    private ResponseStatusException castError(String playerId, String spellId, Integer castAtLevel) {
        try {
            service.cast(playerId, new CastRequest(spellId, castAtLevel));
        } catch (ResponseStatusException e) {
            return e;
        }
        throw new AssertionError("expected the cast to be rejected");
    }

    @Nested
    class ValidationChain {

        @Test
        void unknownSpellIs404() {
            sorcerer(1, "magic-bolt");
            assertThat(castError("sorcerer", "no-such-spell", null).getStatusCode().value())
                    .isEqualTo(404);
        }

        // Criterion 3
        @Test
        void nonCasterIs400() {
            var c = new GameCharacter("barbarian");
            c.setName("barbarian");
            c.setLevel(5);
            c.setPathId("warrior");
            c.setClassId("barbarian");
            c.setStats(new Stats(16, 10, 14, 8, 10, 10, 10));
            c.setHp(new HitPoints(100, 100, 0));
            c.setMana(new ManaPool(0, 0));
            c.setAp(new ActionPoints(6, 6, 10));
            c.getKnownSpells().add("magic-bolt");
            repo.save(c);

            var e = castError("barbarian", "magic-bolt", null);
            assertThat(e.getStatusCode().value()).isEqualTo(400);
            assertThat(e.getReason()).contains("not a spellcaster");
        }

        @Test
        void spellNotKnownOrPreparedIs400() {
            sorcerer(1);
            var e = castError("sorcerer", "magic-bolt", null);
            assertThat(e.getStatusCode().value()).isEqualTo(400);
            assertThat(e.getReason()).contains("not known or prepared");
        }

        @Test
        void preparedSpellIsCastable() {
            var c = sorcerer(1);
            c.getPreparedSpells().add("magic-bolt");
            repo.save(c);
            var response = service.cast("sorcerer", new CastRequest("magic-bolt", null));
            assertThat(response.snapshot().ap().current()).isEqualTo(3);
        }

        /** Q21: the spell's classId must match the caster's class. */
        @Test
        void otherClassSpellIs400() {
            sorcerer(5, "sacred-bolt"); // cleric spell
            var e = castError("sorcerer", "sacred-bolt", null);
            assertThat(e.getStatusCode().value()).isEqualTo(400);
            assertThat(e.getReason()).contains("belongs to class 'cleric'");
        }

        // Criterion 2 — spellLevelAccess.major[1] = 1
        @Test
        void level2SorcererCannotCastAtLevel2() {
            sorcerer(2, "magic-bolt");
            var e = castError("sorcerer", "magic-bolt", 2);
            assertThat(e.getStatusCode().value()).isEqualTo(400);
            assertThat(e.getReason()).contains("max spell level 1");
        }

        @Test
        void castBelowSpellLevelIs400() {
            sorcerer(5, "magic-bolt");
            assertThat(castError("sorcerer", "magic-bolt", 0).getReason())
                    .contains("below the spell's level");
        }

        @Test
        void upcastWithoutScalingIs400() {
            sorcerer(5, "create-minor-illusion"); // no scaling block
            var e = castError("sorcerer", "create-minor-illusion", 2);
            assertThat(e.getStatusCode().value()).isEqualTo(400);
            assertThat(e.getReason()).contains("does not upcast");
        }

        /** Step 5: preventAction all (stunned) blocks casting. */
        @Test
        void stunnedCasterCannotCast() {
            sorcerer(1, "magic-bolt");
            service.applyEffect("sorcerer", new ApplyEffectRequest(
                    "stunned", 1, null, 1, "test", false, false, false, null));
            var e = castError("sorcerer", "magic-bolt", null);
            assertThat(e.getStatusCode().value()).isEqualTo(400);
            assertThat(e.getReason()).contains("cannot cast while stunned");
        }
    }

    @Nested
    class Spend {

        // Criterion 4 — all-or-nothing
        @Test
        void insufficientManaLeavesApUntouched() {
            var c = sorcerer(1, "magic-bolt");
            c.getMana().setCurrent(3); // magic-bolt costs 5
            repo.save(c);

            var e = castError("sorcerer", "magic-bolt", null);
            assertThat(e.getStatusCode().value()).isEqualTo(400);
            assertThat(e.getReason()).contains("Insufficient mana");

            var snapshot = service.getCombatSnapshot("sorcerer");
            assertThat(snapshot.ap().current()).isEqualTo(6);
            assertThat(snapshot.mana().current()).isEqualTo(3);
        }

        // M4-A criterion 5 + M4-B criterion 1: 2d10 (5s) + 9 + INT mod 3 = 22;
        // attack-type spells roll their d20 as part of the cast (self-describing cast)
        @Test
        void validCastSpendsApAndManaWithPayload() {
            sorcerer(1, "magic-bolt");
            var response = service.cast("sorcerer", new CastRequest("magic-bolt", null));

            assertThat(response.snapshot().ap().current()).isEqualTo(3);
            assertThat(response.snapshot().mana().current()).isEqualTo(95);
            assertThat(response.resolution().getSteps())
                    .extracting("rule")
                    .containsExactly("spend-ap", "spend-mana", "attack-roll", "roll-damage");

            // level 1 → proficiency 2, INT 16 → +3
            var payload = response.resolution().getPayload();
            assertThat(payload.get("saveDC")).isEqualTo(13);
            assertThat(payload.get("attackBonus")).isEqualTo(5);
            assertThat(payload.get("damageType")).isEqualTo("pure");

            @SuppressWarnings("unchecked")
            var attack = (java.util.Map<String, Object>) payload.get("attackRoll");
            assertThat(attack.get("roll")).isEqualTo(5);   // fixed d20
            assertThat(attack.get("bonus")).isEqualTo(5);
            assertThat(attack.get("total")).isEqualTo(10);
            assertThat(attack).doesNotContainKeys("critical", "criticalFailure");

            @SuppressWarnings("unchecked")
            var damage = (java.util.Map<String, Object>) payload.get("damage");
            assertThat(damage.get("rolls")).isEqualTo(List.of(5, 5));
            assertThat(damage.get("flat")).isEqualTo(9);
            assertThat(damage.get("modifier")).isEqualTo(3);
            assertThat(damage.get("total")).isEqualTo(22);
        }

        @SuppressWarnings("unchecked")
        private java.util.Map<String, Object> attackRollOf(String spellId) {
            var response = service.cast("sorcerer", new CastRequest(spellId, null));
            return (java.util.Map<String, Object>) response.resolution().getPayload().get("attackRoll");
        }

        /** Nat 20 crits — DOUBLING the damage (Game Owner 2026-07-07); nat 1 is an
         *  automatic miss regardless of modifiers. */
        @Test
        void naturalTwentyCritsDoublingDamageAndNaturalOneFumbles() {
            sorcerer(5, "magic-bolt");

            FixedRandom.next = 19; // d20 = 20; every d10 rolls a 10
            var response = service.cast("sorcerer", new CastRequest("magic-bolt", null));
            var payload = response.resolution().getPayload();
            @SuppressWarnings("unchecked")
            var attack = (java.util.Map<String, Object>) payload.get("attackRoll");
            assertThat(attack.get("critical")).isEqualTo(true);
            @SuppressWarnings("unchecked")
            var damage = (java.util.Map<String, Object>) payload.get("damage");
            // (2×10 dice + 9 flat + 3 mod) × 2 = 64
            assertThat(damage.get("critMultiplier")).isEqualTo(2);
            assertThat(damage.get("total")).isEqualTo(64);

            FixedRandom.next = 0; // d20 = 1
            var fumble = attackRollOf("magic-bolt");
            assertThat(fumble.get("criticalFailure")).isEqualTo(true);
            assertThat(fumble).doesNotContainKey("critical");
        }

        /** Non-crit damage carries no multiplier. */
        @Test
        void normalHitsAreNotDoubled() {
            sorcerer(1, "magic-bolt");
            var response = service.cast("sorcerer", new CastRequest("magic-bolt", null));
            @SuppressWarnings("unchecked")
            var damage = (java.util.Map<String, Object>) response.resolution()
                    .getPayload().get("damage");
            assertThat(damage).doesNotContainKey("critMultiplier");
            assertThat(damage.get("total")).isEqualTo(22);
        }

        /** Crit talents feed the range: glass-cannon (+20% = 4 faces) crits at 16-20
         *  and forces AC/PA/MA to 0 (talent mechanics wired into the stat engine). */
        @Test
        void glassCannonTalentWidensCritRangeAndZeroesArmor() {
            var c = sorcerer(5, "magic-bolt");
            c.getTalents().add("glass-cannon");
            repo.save(c);

            var snapshot = service.getCombatSnapshot("sorcerer");
            assertThat(snapshot.ac()).isEqualTo(0);
            assertThat(snapshot.pa()).isEqualTo(0);
            assertThat(snapshot.ma()).isEqualTo(0);

            FixedRandom.next = 15; // d20 = 16 — a crit only with the widened range
            assertThat(attackRollOf("magic-bolt").get("critical")).isEqualTo(true);

            FixedRandom.next = 14; // d20 = 15 — below even the widened range
            assertThat(attackRollOf("magic-bolt")).doesNotContainKey("critical");
        }

        /** Saving-throw spells present the DC instead of rolling an attack. */
        @Test
        void saveSpellsHaveNoAttackRoll() {
            sorcerer(1, "nether-zone");
            var response = service.cast("sorcerer", new CastRequest("nether-zone", null));
            var payload = response.resolution().getPayload();
            assertThat(payload).doesNotContainKey("attackRoll");
            assertThat(payload.get("saveDC")).isEqualTo(13);
        }

        /** M4-B criterion 2: 2 upcast steps add +50 mana and +2×(2d10+8+mod).
         *  Dice 6×5=30, flat 9+16=25, modifier 3×3=9 → 64. */
        @Test
        void upcastRollsAddScalingDiceFlatAndMod() {
            sorcerer(5, "magic-bolt");
            var response = service.cast("sorcerer", new CastRequest("magic-bolt", 3));

            assertThat(response.snapshot().mana().current()).isEqualTo(45);
            @SuppressWarnings("unchecked")
            var damage = (java.util.Map<String, Object>) response.resolution().getPayload().get("damage");
            assertThat(damage.get("rolls")).isEqualTo(List.of(5, 5, 5, 5, 5, 5));
            assertThat(damage.get("flat")).isEqualTo(25);
            assertThat(damage.get("modifier")).isEqualTo(9);
            assertThat(damage.get("total")).isEqualTo(64);
        }

        /** M4-B criterion 3: a cursed/decaying caster still gets a normal healing
         *  roll in the payload — the debuffs bite when healing is APPLIED. */
        @Test
        void cursedCasterStillGetsNormalHealingRoll() {
            var c = new GameCharacter("cleric");
            c.setName("cleric");
            c.setLevel(1);
            c.setPathId("disciple");
            c.setClassId("cleric");
            c.setStats(new Stats(10, 10, 10, 10, 10, 16, 10)); // WILL 16 → +3 (cleric spellStat)
            c.setHp(new HitPoints(50, 50, 0));
            c.setMana(new ManaPool(50, 50));
            c.setAp(new ActionPoints(6, 6, 10));
            c.getKnownSpells().add("healing-word");
            repo.save(c);
            service.applyEffect("cleric", new ApplyEffectRequest(
                    "cursed", 1, null, 2, "test", false, false, false, null));

            var response = service.cast("cleric", new CastRequest("healing-word", null));
            @SuppressWarnings("unchecked")
            var healing = (java.util.Map<String, Object>) response.resolution().getPayload().get("healing");
            assertThat(healing.get("rolls")).isEqualTo(List.of(5, 5)); // 2d6 at fixed 5s
            assertThat(healing.get("flat")).isEqualTo(4);
            assertThat(healing.get("modifier")).isEqualTo(3);
            assertThat(healing.get("total")).isEqualTo(17);
        }

        /** Upcast mana math: base 5 + 2 steps × 25 = 55 (level-5 major accesses level 3). */
        @Test
        void upcastAddsScaledManaCost() {
            sorcerer(5, "magic-bolt");
            var response = service.cast("sorcerer", new CastRequest("magic-bolt", 3));
            assertThat(response.snapshot().mana().current()).isEqualTo(45);
        }

        /** Q23: flat mana-cost reduction applies before the floor at 0. */
        @Test
        void manaCostReductionEffectLowersTheSpend() {
            sorcerer(1, "magic-bolt");
            service.applyEffect("sorcerer", new ApplyEffectRequest(
                    "mana-cost-reduction", null, 3, null, "test", false, false, false, null));
            var response = service.cast("sorcerer", new CastRequest("magic-bolt", null));
            assertThat(response.snapshot().mana().current()).isEqualTo(98); // 100 - (5-3)
        }
    }

    @Nested
    class ConcentrationAndChanneling {

        // M4-C criterion 1
        @Test
        void secondConcentrationSpellDropsTheFirst() {
            bard(5, "sad-story", "looks-of-the-artist");
            var first = service.cast("bard",
                    new CastRequest("sad-story", null, true, null));
            assertThat(effectIds(first.snapshot()))
                    .contains("concentrating", "difficult-terrain", "obscured-vision");
            assertThat(first.resolution().getPayload().get("concentrationDropped")).isEqualTo(false);

            var second = service.cast("bard",
                    new CastRequest("looks-of-the-artist", null, null, null));
            assertThat(second.resolution().getPayload().get("concentrationDropped")).isEqualTo(true);
            assertThat(second.resolution().getSteps())
                    .extracting("rule")
                    .contains("drop-concentrating");
            // sad-story's marker and its applied effects went with it; the new marker stands.
            assertThat(effectIds(second.snapshot()))
                    .contains("concentrating")
                    .doesNotContain("difficult-terrain", "obscured-vision");
        }

        // M4-C criterion 2
        @Test
        void channelingBlocksCastingUntilWillinglyEnded() {
            bard(5, "increasing-pitch", "sad-story");
            service.cast("bard", new CastRequest("increasing-pitch", null));

            var e = castError("bard", "sad-story", null);
            assertThat(e.getStatusCode().value()).isEqualTo(400);
            assertThat(e.getReason()).contains("cannot cast while channeling");

            // Willingly ending the channel is free — plain remove-effect.
            service.removeEffect("bard", "channeling");
            var response = service.cast("bard", new CastRequest("sad-story", null));
            assertThat(effectIds(response.snapshot())).contains("concentrating");
        }

        // M4-C criterion 3 — failed save (roll 1 + WILL 0 = 1 vs DC 9)
        @Test
        void failedWillSaveBreaksConcentrationAndSweepsEffects() {
            bard(5, "sad-story");
            service.cast("bard", new CastRequest("sad-story", null, true, null));

            FixedRandom.next = 0;
            var response = service.damage("bard", new DamageRequest(
                    20, DamageType.FIRE, null, false, null, false, 4));

            assertThat(response.resolution().getEffectsTriggered()).contains("broken:concentrating");
            assertThat(effectIds(response.snapshot()))
                    .doesNotContain("concentrating", "difficult-terrain", "obscured-vision");
        }

        // M4-C criterion 3 — held save (roll 10 + WILL 0 = 10 vs DC 9)
        @Test
        void successfulWillSaveKeepsConcentration() {
            bard(5, "sad-story");
            service.cast("bard", new CastRequest("sad-story", null, true, null));

            FixedRandom.next = 9;
            var response = service.damage("bard", new DamageRequest(
                    20, DamageType.FIRE, null, false, null, false, 4));

            assertThat(response.resolution().getEffectsTriggered()).isEmpty();
            assertThat(effectIds(response.snapshot()))
                    .contains("concentrating", "difficult-terrain", "obscured-vision");
        }

        // M4-C criterion 3 — no attackerMight: manual-resolution step, nothing removed
        @Test
        void missingAttackerMightEmitsManualStep() {
            bard(5, "sad-story");
            service.cast("bard", new CastRequest("sad-story", null, true, null));

            var response = service.damage("bard", new DamageRequest(
                    20, DamageType.FIRE, null, false, null, false));

            assertThat(response.resolution().getSteps())
                    .anyMatch(s -> s.rule().equals("concentration-check")
                            && s.note().contains("manually"));
            assertThat(effectIds(response.snapshot())).contains("concentrating");
        }

        /** Damage fully absorbed by temp HP never reduces HP → no concentration check. */
        @Test
        void absorbedDamageDoesNotForceTheSave() {
            bard(5, "sad-story");
            service.cast("bard", new CastRequest("sad-story", null, true, null));
            service.applyEffect("bard", new ApplyEffectRequest(
                    "temporary-hp", null, 50, null, "test", false, true, false, null));

            FixedRandom.next = 0; // would fail the save if it were rolled
            var response = service.damage("bard", new DamageRequest(
                    20, DamageType.FIRE, null, false, null, false, 4));

            assertThat(response.resolution().getSteps())
                    .noneMatch(s -> s.rule().startsWith("concentration"));
            assertThat(effectIds(response.snapshot())).contains("concentrating");
        }
    }

    @Nested
    class SelfAndPartyEffects {

        // M4-C criterion 4 — "1 minute" converts to 6 rounds
        @Test
        void applyEffectsToSelfConvertsDurations() {
            bard(3, "hasting-trill");
            var response = service.cast("bard",
                    new CastRequest("hasting-trill", null, true, null));

            var haste = response.snapshot().activeEffects().stream()
                    .filter(e -> e.id().equals("haste"))
                    .findFirst().orElseThrow();
            assertThat(haste.rounds()).isEqualTo(6);
            // hasting-trill is not a concentration spell — no marker.
            assertThat(effectIds(response.snapshot())).doesNotContain("concentrating");
        }

        // M4-C criterion 4 — without a target the on-hit effects are described in
        // full (name + converted duration) so the table applies them exactly
        @Test
        void withoutTargetEffectsAreListedForTheDm() {
            bard(3, "hasting-trill");
            var response = service.cast("bard", new CastRequest("hasting-trill", null));

            @SuppressWarnings("unchecked")
            var onHit = (List<java.util.Map<String, Object>>) response.resolution()
                    .getPayload().get("effectsOnHit");
            assertThat(onHit).hasSize(1);
            assertThat(onHit.get(0).get("id")).isEqualTo("haste");
            assertThat(onHit.get(0).get("name")).isEqualTo("Haste");
            assertThat(onHit.get(0).get("rounds")).isEqualTo(6); // "1 minute" → 6 rounds
            assertThat(effectIds(response.snapshot())).doesNotContain("haste");
        }

        /** Party targeting: caster pays, the target receives in the same transaction. */
        @Test
        void targetPlayerIdAppliesEffectsToTheTarget() {
            bard(3, "hasting-trill");
            sorcerer(1);

            var response = service.cast("bard",
                    new CastRequest("hasting-trill", null, null, "sorcerer"));

            assertThat(response.resolution().getPayload().get("effectsAppliedTo")).isEqualTo("sorcerer");
            assertThat(effectIds(response.snapshot())).doesNotContain("haste");
            var target = service.getCombatSnapshot("sorcerer");
            assertThat(effectIds(target)).contains("haste");
        }

        @Test
        void unknownTargetIs404AndCostsNothing() {
            bard(3, "hasting-trill");
            try {
                service.cast("bard", new CastRequest("hasting-trill", null, null, "nobody"));
                throw new AssertionError("expected 404");
            } catch (ResponseStatusException e) {
                assertThat(e.getStatusCode().value()).isEqualTo(404);
            }
            assertThat(service.getCombatSnapshot("bard").mana().current()).isEqualTo(1000);
        }
    }

    @Nested
    class ComponentsAndCasterWeapons {

        // M4-D criterion 1
        @Test
        void silencedBlocksVerbalComponentsUnlessGranted() {
            sorcerer(1, "magic-bolt");
            service.applyEffect("sorcerer", new ApplyEffectRequest(
                    "silenced", 1, null, 1, "test", false, false, false, null));

            var e = castError("sorcerer", "magic-bolt", null);
            assertThat(e.getStatusCode().value()).isEqualTo(400);
            assertThat(e.getReason()).contains("silenced");

            service.applyEffect("sorcerer", new ApplyEffectRequest(
                    "no-verbal-components", null, null, null, "test", false, false, false, null));
            var response = service.cast("sorcerer", new CastRequest("magic-bolt", null));
            assertThat(response.snapshot().mana().current()).isEqualTo(95);
        }

        // M4-D criterion 2 — the data's W spells are arcane-ranger shots; any
        // equipped weapon (martial or caster) satisfies the component.
        @Test
        void wComponentRequiresAnEquippedWeapon() {
            var c = new GameCharacter("ranger");
            c.setName("ranger");
            c.setLevel(1);
            c.setPathId("archer");
            c.setClassId("arcane-ranger");
            c.setStats(new Stats(10, 14, 10, 10, 16, 10, 10));
            c.setHp(new HitPoints(80, 80, 0));
            c.setMana(new ManaPool(50, 50));
            c.setAp(new ActionPoints(6, 6, 10));
            c.getKnownSpells().add("aimed-shot");
            repo.save(c);

            var e = castError("ranger", "aimed-shot", null);
            assertThat(e.getStatusCode().value()).isEqualTo(400);
            assertThat(e.getReason()).contains("equipped weapon");

            // the original instance keeps a plain (non-proxy) inventory list
            c.addItem(new InventoryEntry("shortbow", 1, 0, true));
            repo.save(c);
            var response = service.cast("ranger", new CastRequest("aimed-shot", null));
            assertThat(response.snapshot().mana().current()).isEqualTo(45);
        }

        // M4-D criterion 3 — arcane-staff-10: spellModifier 3, spellDamage 5,
        // manaCostReduction 5, staffAccuracy +4 attack / +2 DC.
        @Test
        void casterWeaponBonusesVisiblyChangeTheMath() {
            var c = sorcerer(1, "magic-bolt");
            c.addItem(new InventoryEntry("arcane-staff-10", 1, 0, true));
            repo.save(c);

            var response = service.cast("sorcerer", new CastRequest("magic-bolt", null));

            // mana: 5 - 5 = 0 → nothing spent, no spend-mana step
            assertThat(response.snapshot().mana().current()).isEqualTo(100);
            assertThat(response.resolution().getSteps())
                    .extracting("rule")
                    .doesNotContain("spend-mana");

            var payload = response.resolution().getPayload();
            assertThat(payload.get("saveDC")).isEqualTo(15);      // 13 + 2
            assertThat(payload.get("attackBonus")).isEqualTo(9);  // 5 + 4

            @SuppressWarnings("unchecked")
            var damage = (java.util.Map<String, Object>) payload.get("damage");
            assertThat(damage.get("rolls")).isEqualTo(List.of(5, 5));
            assertThat(damage.get("flat")).isEqualTo(9);
            assertThat(damage.get("modifier")).isEqualTo(6);      // (INT 3 + staff 3) × 1
            assertThat(damage.get("weaponDamage")).isEqualTo(5);
            assertThat(damage.get("total")).isEqualTo(30);
        }

        /** componentsAvailable narrows what the caster can provide. */
        @Test
        void missingComponentInOverrideListIs400() {
            sorcerer(1, "magic-bolt");
            var e = new AssertionError("expected 400");
            try {
                service.cast("sorcerer", new CastRequest(
                        "magic-bolt", null, null, null, List.of("S")));
            } catch (ResponseStatusException ex) {
                assertThat(ex.getStatusCode().value()).isEqualTo(400);
                assertThat(ex.getReason()).contains("component V");
                return;
            }
            throw e;
        }
    }

    @Nested
    class PrepareSpells {

        private ResponseStatusException prepareError(String playerId, List<String> ids) {
            try {
                service.prepareSpells(playerId, new com.steelmight.charactersheet.dto.PrepareSpellsRequest(ids));
            } catch (ResponseStatusException e) {
                return e;
            }
            throw new AssertionError("expected the preparation to be rejected");
        }

        // M4-E criterion 1 — INT 16 → modifier 3
        @Test
        void countIsCappedAtIntModifierAndValidListReplaces() {
            sorcerer(1);
            var e = prepareError("sorcerer",
                    List.of("magic-bolt", "nether-zone", "magic-flame", "minor-burst"));
            assertThat(e.getStatusCode().value()).isEqualTo(400);
            assertThat(e.getReason()).contains("at most 3");

            service.prepareSpells("sorcerer", new com.steelmight.charactersheet.dto.PrepareSpellsRequest(
                    List.of("magic-bolt", "nether-zone", "magic-flame")));
            assertThat(service.getSpellbookSnapshot("sorcerer").preparedSpells())
                    .containsExactly("magic-bolt", "nether-zone", "magic-flame");

            // re-preparing replaces wholesale
            service.prepareSpells("sorcerer", new com.steelmight.charactersheet.dto.PrepareSpellsRequest(
                    List.of("minor-burst")));
            assertThat(service.getSpellbookSnapshot("sorcerer").preparedSpells())
                    .containsExactly("minor-burst");
        }

        // M4-E criterion 2 — minors never get 5th-level spells this way
        @Test
        void minorCasterCannotPrepareFifthLevelSpells() {
            var c = new GameCharacter("ranger");
            c.setName("ranger");
            c.setLevel(20); // minor access at 20 is level 5 — the restriction still bites
            c.setPathId("archer");
            c.setClassId("arcane-ranger");
            c.setStats(new Stats(10, 14, 10, 16, 14, 10, 10));
            c.setHp(new HitPoints(80, 80, 0));
            c.setMana(new ManaPool(50, 50));
            c.setAp(new ActionPoints(6, 6, 10));
            repo.save(c);

            var e = prepareError("ranger", List.of("soul-shot"));
            assertThat(e.getStatusCode().value()).isEqualTo(400);
            assertThat(e.getReason()).contains("5th-level");

            // minors: max 2 of the same level (except 1st)
            var e2 = prepareError("ranger", List.of("focused-aim", "volatile-fire-arrow", "cobra-shot"));
            assertThat(e2.getReason()).contains("at most 2");

            // 2 of level 2 is fine
            service.prepareSpells("ranger", new com.steelmight.charactersheet.dto.PrepareSpellsRequest(
                    List.of("focused-aim", "volatile-fire-arrow")));
            assertThat(service.getSpellbookSnapshot("ranger").preparedSpells()).hasSize(2);
        }

        // majors: max 1 per level (except 1st)
        @Test
        void majorCasterCarriesAtMostOnePerLevel() {
            sorcerer(5); // level 5 → access 3
            var e = prepareError("sorcerer", List.of("dual-arcana", "rooting-magic"));
            assertThat(e.getStatusCode().value()).isEqualTo(400);
            assertThat(e.getReason()).contains("at most 1");
        }

        @Test
        void levelAccessAndClassListAreEnforced() {
            sorcerer(1); // access 1
            assertThat(prepareError("sorcerer", List.of("dual-arcana")).getReason())
                    .contains("no access to level-2");
            assertThat(prepareError("sorcerer", List.of("sacred-bolt")).getReason())
                    .contains("belongs to class 'cleric'");
        }

        @Test
        void nonCasterIs400() {
            var c = new GameCharacter("barbarian");
            c.setName("barbarian");
            c.setLevel(5);
            c.setPathId("warrior");
            c.setClassId("barbarian");
            c.setStats(new Stats(16, 10, 14, 16, 10, 10, 10));
            c.setHp(new HitPoints(100, 100, 0));
            c.setMana(new ManaPool(0, 0));
            c.setAp(new ActionPoints(6, 6, 10));
            repo.save(c);

            assertThat(prepareError("barbarian", List.of("magic-bolt")).getReason())
                    .contains("not a spellcaster");
        }
    }
}
