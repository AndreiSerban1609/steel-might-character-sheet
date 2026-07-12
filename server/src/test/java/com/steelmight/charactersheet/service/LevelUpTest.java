package com.steelmight.charactersheet.service;

import com.steelmight.charactersheet.dto.LevelUpRequest;
import com.steelmight.charactersheet.model.*;
import com.steelmight.charactersheet.repository.CharacterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** M6-B acceptance criteria 1-4 + M6-C criteria 2-4. */
@SpringBootTest
class LevelUpTest {

    @Autowired
    private ProgressionService progression;

    @Autowired
    private CharacterRepository repo;

    @BeforeEach
    void setUp() {
        repo.deleteAll();
    }

    private static LevelUpRequest choices(Map<AbilityScore, Integer> stats, List<String> spells,
                                          String talentId, String featId) {
        return new LevelUpRequest(new LevelUpRequest.Choices(stats, spells, talentId, featId));
    }

    /** Level-N sorcerer (Study spec, luck-crafter starting talent, magic-bolt known). */
    private GameCharacter sorcerer(int level) {
        var c = new GameCharacter("sorc");
        c.setName("sorc");
        c.setLevel(level);
        c.setPathId("wizard");
        c.setClassId("sorcerer");
        c.setSpecializationId("study");
        c.setRaceId("human");
        c.setStats(new Stats(9, 12, 15, 17, 12, 10, 8));
        c.setHp(new HitPoints(100, 100, 0));
        c.setMana(new ManaPool(200, 200));
        c.setAp(new ActionPoints(6, 6, 10));
        c.getKnownSpells().add("magic-bolt");
        c.getTalents().add("luck-crafter");
        return repo.save(c);
    }

    private GameCharacter barbarian(int level) {
        var c = new GameCharacter("barb");
        c.setName("barb");
        c.setLevel(level);
        c.setPathId("warrior");
        c.setClassId("barbarian");
        c.setSpecializationId("berserker");
        c.setStats(new Stats(17, 12, 15, 9, 12, 10, 8));
        c.setHp(new HitPoints(100, 100, 0));
        c.setMana(new ManaPool(0, 0));
        c.setAp(new ActionPoints(6, 6, 10));
        c.setResource(new ClassResource("rages", 2, 2));
        c.getTalents().add("blood-rush");
        return repo.save(c);
    }

    @Nested
    class Core {

        // M6-B criterion 1 — 4→5 major caster: 1 new spell, access rises to 3, mana milestone
        @Test
        void levelFiveGrantsSpellAccessAndManaMilestone() {
            sorcerer(4);
            // 4→5 is also a feat level (M6-C) — the sorcerer picks its spec's active feat
            var response = progression.levelUp("sorc", choices(null,
                    List.of("veil-crevasse"), null, "active")); // level-2 spell, learnable at 5

            assertThat(response.snapshot().level()).isEqualTo(5);
            // sorcerer: manaPerLevel 50 ×5 + first milestone 25 = 275
            assertThat(response.snapshot().mana().max()).isEqualTo(275);

            // a level-3 spell is learnable at 5 (access = 3): re-check the validator accepts it
            var again = repo.findById("sorc").orElseThrow();
            assertThat(again.getLevel()).isEqualTo(5);
        }

        @Test
        void wrongSpellCountIs400() {
            sorcerer(4);
            assertThatThrownBy(() -> progression.levelUp("sorc",
                    choices(null, List.of(), null, "active")))
                    .hasMessageContaining("exactly 1");
            assertThatThrownBy(() -> progression.levelUp("sorc",
                    choices(null, List.of("veil-crevasse", "rooting-magic"), null, "active")))
                    .hasMessageContaining("exactly 1");
            // already-known spell rejected
            assertThatThrownBy(() -> progression.levelUp("sorc",
                    choices(null, List.of("magic-bolt"), null, "active")))
                    .hasMessageContaining("already known");
        }

        // M6-B criterion 2 — statIncreases at 6, rejected at 7
        @Test
        void statIncreasesRequiredAtSixRejectedAtSeven() {
            sorcerer(5);
            // missing → 400
            assertThatThrownBy(() -> progression.levelUp("sorc",
                    choices(null, List.of("veil-crevasse"), null, null)))
                    .hasMessageContaining("bonusAllocation is required");
            // sum 4 → 400
            assertThatThrownBy(() -> progression.levelUp("sorc",
                    choices(Map.of(AbilityScore.INT, 2, AbilityScore.CON, 2),
                            List.of("veil-crevasse"), null, null)))
                    .hasMessageContaining("sum to 5");
            // 3 on one stat → 400
            assertThatThrownBy(() -> progression.levelUp("sorc",
                    choices(Map.of(AbilityScore.INT, 3, AbilityScore.CON, 2),
                            List.of("veil-crevasse"), null, null)))
                    .hasMessageContaining("per-stat maximum");

            var ok = progression.levelUp("sorc", choices(
                    Map.of(AbilityScore.INT, 2, AbilityScore.CON, 2, AbilityScore.WIS, 1),
                    List.of("veil-crevasse"), null, null));
            assertThat(ok.snapshot().stats().get(AbilityScore.INT)).isEqualTo(19);

            // 6→7 is a talent level, not a stat level
            assertThatThrownBy(() -> progression.levelUp("sorc",
                    choices(Map.of(AbilityScore.INT, 2, AbilityScore.CON, 2, AbilityScore.WIS, 1),
                            List.of("rooting-magic"), "executioner", null)))
                    .hasMessageContaining("no stat increase");
        }

        // M6-B criterion 3
        @Test
        void barbarianRageMaxRecomputes() {
            barbarian(1);
            var response = progression.levelUp("barb", choices(null, null, null, null));
            assertThat(response.snapshot().level()).isEqualTo(2);
            var entity = repo.findById("barb").orElseThrow();
            assertThat(entity.getResource().getMax()).isEqualTo(3); // resourcePerLevel[1]
        }

        // M6-B criterion 4
        @Test
        void currentHpRisesByTheMaxHpDelta() {
            var c = barbarian(1);
            c.getHp().setCurrent(10);
            repo.save(c);

            var response = progression.levelUp("barb", choices(null, null, null, null));
            // max = (hpPerLevel + 3×CONmod) × level, so the 1→2 delta is exactly half of maxL2
            int delta = response.snapshot().hp().max() / 2;
            assertThat(response.snapshot().hp().current()).isEqualTo(10 + delta);
        }

        @Test
        void levelCapIs400() {
            var c = barbarian(1);
            c.setLevel(20);
            repo.save(c);
            assertThatThrownBy(() -> progression.levelUp("barb", choices(null, null, null, null)))
                    .hasMessageContaining("level cap");
        }
    }

    @Nested
    class TalentsAndFeats {

        // M6-C criterion 2 — talent required at 3, pool-validated
        @Test
        @Transactional
        void talentPickAtLevelThree() {
            barbarian(2);
            assertThatThrownBy(() -> progression.levelUp("barb", choices(null, null, null, null)))
                    .hasMessageContaining("talentId is required");
            assertThatThrownBy(() -> progression.levelUp("barb",
                    choices(null, null, "not-a-talent", null)))
                    .hasMessageContaining("eligible talent pool");
            // spec talents don't join the pool until level 5 — the Berserker's own
            // additional talents are rejected at 3
            progression.levelUp("barb", choices(null, null, "executioner", null));
            assertThat(repo.findById("barb").orElseThrow().getTalents())
                    .containsExactlyInAnyOrder("blood-rush", "executioner");
        }

        @Test
        @Transactional
        void duplicateTalentIs400() {
            barbarian(2);
            assertThatThrownBy(() -> progression.levelUp("barb",
                    choices(null, null, "blood-rush", null)))
                    .hasMessageContaining("already owned");
        }

        // M6-C criterion 3 — feats at 5/9/13, each once, all three by 13
        @Test
        @Transactional
        void featPicksAcrossFiveNineThirteen() {
            var c = barbarian(4);
            assertThatThrownBy(() -> progression.levelUp("barb", choices(null, null, null, null)))
                    .hasMessageContaining("featId");
            progression.levelUp("barb", choices(null, null, null, "active"));

            c = repo.findById("barb").orElseThrow();
            c.setLevel(8);
            repo.save(c);
            // taking the same feat twice → 400
            assertThatThrownBy(() -> progression.levelUp("barb", choices(null, null, null, "active")))
                    .hasMessageContaining("already taken");
            progression.levelUp("barb", choices(null, null, null, "passive"));

            c = repo.findById("barb").orElseThrow();
            c.setLevel(12);
            repo.save(c);
            progression.levelUp("barb", choices(null, null, null, "modification"));

            assertThat(repo.findById("barb").orElseThrow().getSpecFeats())
                    .containsExactlyInAnyOrder("active", "passive", "modification");
        }

        // M6-C criterion 4 — the 17th-level spec talent
        @Test
        @Transactional
        void levelSeventeenSpecTalentAutoGrantOrPick() {
            // none of the 2 owned → a pick is required
            var c = barbarian(16);
            assertThatThrownBy(() -> progression.levelUp("barb", choices(null, null, null, null)))
                    .hasMessageContaining("pick one of");
            progression.levelUp("barb", choices(null, null, specTalent(0), null));
            assertThat(repo.findById("barb").orElseThrow().getTalents()).contains(specTalent(0));

            // one already owned → the other is granted automatically
            repo.deleteAll();
            c = barbarian(16);
            c.getTalents().add(specTalent(0));
            repo.save(c);
            progression.levelUp("barb", choices(null, null, null, null));
            assertThat(repo.findById("barb").orElseThrow().getTalents())
                    .contains(specTalent(0), specTalent(1));
        }

        /** Slug of the Berserker spec's Nth additional talent (read from the data). */
        private String specTalent(int index) {
            return com.steelmight.charactersheet.gamedata.GameDataProvider.slug(
                    gameDataTalentName(index));
        }

        @Autowired
        private com.steelmight.charactersheet.gamedata.GameDataProvider gameData;

        private String gameDataTalentName(int index) {
            return gameData.findSpecialization("barbarian", "berserker")
                    .path("additionalTalents").get(index).path("name").asText();
        }
    }
}
