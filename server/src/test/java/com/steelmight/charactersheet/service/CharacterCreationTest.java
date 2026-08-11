package com.steelmight.charactersheet.service;

import com.steelmight.charactersheet.dto.CreateCharacterRequest;
import com.steelmight.charactersheet.model.AbilityScore;
import com.steelmight.charactersheet.repository.CharacterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** M6-A acceptance criteria. */
@SpringBootTest
class CharacterCreationTest {

    @Autowired
    private CharacterService service;

    @Autowired
    private CharacterRepository repo;

    @BeforeEach
    void setUp() {
        repo.deleteAll();
    }

    private static Map<AbilityScore, Integer> statArray(int str, int dex, int con, int intel,
                                                        int wis, int will, int cha) {
        var m = new HashMap<AbilityScore, Integer>();
        m.put(AbilityScore.STR, str);
        m.put(AbilityScore.DEX, dex);
        m.put(AbilityScore.CON, con);
        m.put(AbilityScore.INT, intel);
        m.put(AbilityScore.WIS, wis);
        m.put(AbilityScore.WILL, will);
        m.put(AbilityScore.CHA, cha);
        return m;
    }

    /** INT 15+2, CON 13+2, WIS 11+1 after the bonus. */
    private static CreateCharacterRequest sorcerer(Map<AbilityScore, Integer> stats,
                                                   Map<AbilityScore, Integer> bonus,
                                                   List<String> spells) {
        return new CreateCharacterRequest("demo", "sorc@x.com", "Zara",
                "human", "wizard", "sorcerer", "study", 1,
                stats, bonus,
                List.of("knowledge", "arcana", "investigation"),
                spells, null, null, null);
    }

    private static CreateCharacterRequest validSorcerer() {
        return sorcerer(
                statArray(9, 12, 13, 15, 11, 10, 8),
                Map.of(AbilityScore.INT, 2, AbilityScore.CON, 2, AbilityScore.WIS, 1),
                List.of("magic-bolt"));
    }

    // Criterion 1 — @Transactional so the entity's lazy collections stay readable
    @Test
    @org.springframework.transaction.annotation.Transactional
    void validSorcererInitializesEverythingFromData() {
        var created = service.createCharacter(validSorcerer());
        var snapshot = created.snapshot();

        // CON 13 + 2 = 15 (+2) -> HP = (20 + 6) * 1 = 26; sorcerer manaPerLevel 50
        assertThat(snapshot.hp().max()).isEqualTo(26);
        assertThat(snapshot.mana().max()).isEqualTo(50);
        assertThat(snapshot.stats().get(AbilityScore.INT)).isEqualTo(17);
        assertThat(snapshot.ap().current()).isEqualTo(6);
        assertThat(snapshot.ap().max()).isEqualTo(10);
        // N16: per-class saving throws — values still live on the path pending migration
        assertThat(snapshot.savingThrowProficiencies())
                .containsExactlyInAnyOrder(AbilityScore.INT, AbilityScore.WIS);
        assertThat(snapshot.speed()).isEqualTo(15); // human movementSpeed (ft per AP)

        var entity = repo.findById(created.playerId()).orElseThrow();
        assertThat(entity.getKnownSpells()).containsExactly("magic-bolt");
        assertThat(entity.getGold()).isEqualTo(100); // Q38 re-cut: 100 generic gold = 10 × level-1 quest reward
        assertThat(entity.getSpecializationId()).isEqualTo("study");
        assertThat(entity.getTalents()).containsExactly("luck-crafter"); // Study's startingTalent
        assertThat(entity.getProficiencies())
                .containsExactlyInAnyOrder("knowledge", "arcana", "investigation");
    }

    // Criterion 2
    @Test
    void statArrayPermutationAndBonusAreEnforced() {
        // duplicate 15 — not a permutation
        assertThatThrownBy(() -> service.createCharacter(sorcerer(
                statArray(15, 12, 13, 15, 11, 10, 8),
                Map.of(AbilityScore.INT, 2, AbilityScore.CON, 2, AbilityScore.WIS, 1),
                List.of("magic-bolt"))))
                .hasMessageContaining("permutation");

        // bonus sum 4
        assertThatThrownBy(() -> service.createCharacter(sorcerer(
                statArray(9, 12, 13, 15, 11, 10, 8),
                Map.of(AbilityScore.INT, 2, AbilityScore.CON, 2),
                List.of("magic-bolt"))))
                .hasMessageContaining("sum to 5");

        // bonus sum 6
        assertThatThrownBy(() -> service.createCharacter(sorcerer(
                statArray(9, 12, 13, 15, 11, 10, 8),
                Map.of(AbilityScore.INT, 2, AbilityScore.CON, 2, AbilityScore.WIS, 2),
                List.of("magic-bolt"))))
                .hasMessageContaining("sum to 5");

        // 3 on one stat
        assertThatThrownBy(() -> service.createCharacter(sorcerer(
                statArray(9, 12, 13, 15, 11, 10, 8),
                Map.of(AbilityScore.INT, 3, AbilityScore.CON, 2),
                List.of("magic-bolt"))))
                .hasMessageContaining("per-stat maximum");
    }

    // Criterion 3
    @Test
    void duplicateIdentityIs409() {
        service.createCharacter(validSorcerer());
        assertThatThrownBy(() -> service.createCharacter(validSorcerer()))
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    // Criterion 4
    @Test
    void spellAllotmentIsEnforcedPerCasterType() {
        // caster with 0 spells
        assertThatThrownBy(() -> service.createCharacter(sorcerer(
                statArray(9, 12, 13, 15, 11, 10, 8),
                Map.of(AbilityScore.INT, 2, AbilityScore.CON, 2, AbilityScore.WIS, 1),
                List.of())))
                .hasMessageContaining("exactly 1");

        // caster with 2 spells
        assertThatThrownBy(() -> service.createCharacter(sorcerer(
                statArray(9, 12, 13, 15, 11, 10, 8),
                Map.of(AbilityScore.INT, 2, AbilityScore.CON, 2, AbilityScore.WIS, 1),
                List.of("magic-bolt", "nether-zone"))))
                .hasMessageContaining("exactly 1");

        // non-caster with a spell
        assertThatThrownBy(() -> service.createCharacter(new CreateCharacterRequest(
                "demo", "barb@x.com", "Korg", "dwarf", "warrior", "barbarian", "berserker", 1,
                statArray(15, 12, 13, 9, 11, 10, 8),
                Map.of(AbilityScore.STR, 2, AbilityScore.CON, 2, AbilityScore.DEX, 1),
                List.of("athletics", "intimidation", "survival"),
                List.of("magic-bolt"), null, null, null)))
                .hasMessageContaining("non-casters");
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void barbarianGetsClassResourceAndSpecTalent() {
        var created = service.createCharacter(new CreateCharacterRequest(
                "demo", "barb@x.com", "Korg", "dwarf", "warrior", "barbarian", "berserker", 1,
                statArray(15, 12, 13, 9, 11, 10, 8),
                Map.of(AbilityScore.STR, 2, AbilityScore.CON, 2, AbilityScore.DEX, 1),
                List.of("athletics", "intimidation", "survival"),
                List.of(), null, null, null));

        var entity = repo.findById(created.playerId()).orElseThrow();
        assertThat(entity.getResource().getType()).isEqualTo("rages");
        assertThat(entity.getResource().getMax()).isEqualTo(2);   // resourcePerLevel[0]
        assertThat(entity.getResource().getCurrent()).isEqualTo(2); // allotments start full (Q19)
        assertThat(entity.getTalents()).containsExactly("blood-rush"); // Berserker's startingTalent
    }

    // Game Owner 2026-08-12: one weapon + one shield + one body armor, free at creation.
    @Test
    @org.springframework.transaction.annotation.Transactional
    void startingEquipmentIsGrantedEquippedAtLevelOne() {
        var base = validSorcerer();
        var created = service.createCharacter(new CreateCharacterRequest(
                base.roomName(), base.email(), base.name(), base.raceId(), base.pathId(),
                base.classId(), base.specializationId(), 1, base.stats(), base.bonusAllocation(),
                base.skillProficiencies(), base.knownSpells(),
                "longsword", "light-armor", true));

        var entity = repo.findById(created.playerId()).orElseThrow();
        assertThat(entity.getInventory())
                .extracting(e -> e.getItemId())
                .containsExactlyInAnyOrder("longsword", "light-armor", "shield");
        assertThat(entity.getInventory())
                .allSatisfy(e -> {
                    assertThat(e.isEquipped()).isTrue();
                    assertThat(e.getUpgradeTier()).isEqualTo(1);
                });
    }

    @Test
    void startingEquipmentRejectsTwoHandedWithShieldAndWrongKinds() {
        var base = validSorcerer();
        assertThatThrownBy(() -> service.createCharacter(new CreateCharacterRequest(
                base.roomName(), base.email(), base.name(), base.raceId(), base.pathId(),
                base.classId(), base.specializationId(), 1, base.stats(), base.bonusAllocation(),
                base.skillProficiencies(), base.knownSpells(),
                "greatsword", null, true)))
                .hasMessageContaining("exclude each other");

        assertThatThrownBy(() -> service.createCharacter(new CreateCharacterRequest(
                base.roomName(), base.email(), base.name(), base.raceId(), base.pathId(),
                base.classId(), base.specializationId(), 1, base.stats(), base.bonusAllocation(),
                base.skillProficiencies(), base.knownSpells(),
                "light-armor", null, null)))
                .hasMessageContaining("must be a weapon");

        assertThatThrownBy(() -> service.createCharacter(new CreateCharacterRequest(
                base.roomName(), base.email(), base.name(), base.raceId(), base.pathId(),
                base.classId(), base.specializationId(), 1, base.stats(), base.bonusAllocation(),
                base.skillProficiencies(), base.knownSpells(),
                null, "shield", null)))
                .hasMessageContaining("must be a body armor");
    }

    @Test
    void invalidSpecializationRaceAndSkillsAre400() {
        var base = validSorcerer();
        assertThatThrownBy(() -> service.createCharacter(new CreateCharacterRequest(
                base.roomName(), base.email(), base.name(), base.raceId(), base.pathId(),
                base.classId(), "berserker", 1, base.stats(), base.bonusAllocation(),
                base.skillProficiencies(), base.knownSpells(), null, null, null)))
                .hasMessageContaining("not valid for class");

        assertThatThrownBy(() -> service.createCharacter(new CreateCharacterRequest(
                base.roomName(), base.email(), base.name(), "halfling", base.pathId(),
                base.classId(), base.specializationId(), 1, base.stats(), base.bonusAllocation(),
                base.skillProficiencies(), base.knownSpells(), null, null, null)))
                .hasMessageContaining("unknown race");

        assertThatThrownBy(() -> service.createCharacter(new CreateCharacterRequest(
                base.roomName(), base.email(), base.name(), base.raceId(), base.pathId(),
                base.classId(), base.specializationId(), 1, base.stats(), base.bonusAllocation(),
                List.of("arcana", "arcana", "knowledge"), base.knownSpells(), null, null, null)))
                .hasMessageContaining("distinct skill");
    }
}
