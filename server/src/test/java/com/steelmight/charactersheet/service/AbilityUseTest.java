package com.steelmight.charactersheet.service;

import com.steelmight.charactersheet.dto.AbilitiesSnapshot;
import com.steelmight.charactersheet.dto.RestRequest;
import com.steelmight.charactersheet.dto.UpdateAbilitiesRequest;
import com.steelmight.charactersheet.dto.UpdateCustomAbilitiesRequest;
import com.steelmight.charactersheet.dto.UseAbilityRequest;
import com.steelmight.charactersheet.dto.UseCustomAbilityRequest;
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

/** Stories 1.3 + 1.4 — known-abilities picker and the use-ability pipeline. */
@SpringBootTest
class AbilityUseTest {

    @Autowired
    private CharacterService service;

    @Autowired
    private CharacterRepository repo;

    @BeforeEach
    void setUp() {
        repo.deleteAll();
        var c = new GameCharacter("conq");
        c.setName("Testquistador");
        c.setLevel(10); // Endurance rider live, perseverance unlocked
        c.setPathId("warrior");
        c.setClassId("conqueror");
        c.setStats(new Stats(16, 12, 14, 10, 10, 14, 10)); // WILL 14 → +2, CON 14 → +2
        c.setHp(new HitPoints(50, 300, 0));
        c.setMana(new ManaPool(0, 0));
        c.setAp(new ActionPoints(6, 6, 10));
        c.setResource(new ClassResource("energy", 60, 60));
        repo.save(c);
    }

    // ── Story 1.3: picker ──

    @Test
    void classGrantedAbilitiesAreImplicitlyKnown() {
        var snapshot = service.getAbilitiesSnapshot("conq");
        assertThat(snapshot.known()).contains("adrenaline", "indomitable-perseverance");
        assertThat(snapshot.picked()).isEmpty();
    }

    @Test
    void pickerValidatesClassAndLevel() {
        var updated = service.updateKnownAbilities("conq",
                new UpdateAbilitiesRequest(List.of("whirlwind", "parry")));
        assertThat(updated.picked()).containsExactly("whirlwind", "parry");
        assertThat(updated.known()).contains("whirlwind", "adrenaline");

        assertThatThrownBy(() -> service.updateKnownAbilities("conq",
                new UpdateAbilitiesRequest(List.of("rage")))) // barbarian ability
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("belongs to barbarian");

        assertThatThrownBy(() -> service.updateKnownAbilities("conq",
                new UpdateAbilitiesRequest(List.of("overwhelming-power")))) // level 16
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("requires level 16");

        assertThatThrownBy(() -> service.updateKnownAbilities("conq",
                new UpdateAbilitiesRequest(List.of("nonsense"))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("unknown ability");
    }

    @Test
    void snapshotReportsUseBudgetsAndDepletesOnUse() {
        var fresh = service.getAbilitiesSnapshot("conq");
        var adrenaline = fresh.uses().stream()
                .filter(u -> u.abilityId().equals("adrenaline")).findFirst().orElseThrow();
        assertThat(adrenaline.perRestMax()).isEqualTo(2); // WILL +2, min 1
        assertThat(adrenaline.perRestRemaining()).isEqualTo(2);
        assertThat(adrenaline.perTurnMax()).isEqualTo(1);
        assertThat(adrenaline.perTurnRemaining()).isEqualTo(1);
        // reading the snapshot must not materialize use-counter rows
        assertThat(repo.findById("conq").orElseThrow().getAbilityUses()).isEmpty();

        service.useAbility("conq", new UseAbilityRequest("adrenaline"));

        var after = service.getAbilitiesSnapshot("conq").uses().stream()
                .filter(u -> u.abilityId().equals("adrenaline")).findFirst().orElseThrow();
        assertThat(after.perRestRemaining()).isEqualTo(1);
        assertThat(after.perTurnRemaining()).isEqualTo(0);
    }

    // ── Free-text custom abilities (2026-07-20 Game Owner ruling) ──

    @Test
    void customAbilitiesRoundTripAndPrintOnUse() {
        var updated = service.updateCustomAbilities("conq", new UpdateCustomAbilitiesRequest(List.of(
                new AbilitiesSnapshot.CustomAbilityView("Beast Bond",
                        "Companion acts on my initiative (AR3 pending)."))));
        assertThat(updated.custom()).hasSize(1);
        assertThat(updated.custom().get(0).name()).isEqualTo("Beast Bond");

        // case-insensitive by name; the rules text lands in the log for the table
        var used = service.useCustomAbility("conq", new UseCustomAbilityRequest("beast bond"));
        assertThat(used.resolution().getSteps())
                .anyMatch(s -> s.rule().equals("use-custom-ability")
                        && s.note().contains("Companion acts on my initiative"));

        assertThatThrownBy(() -> service.useCustomAbility("conq", new UseCustomAbilityRequest("nope")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no custom ability named");

        assertThatThrownBy(() -> service.updateCustomAbilities("conq",
                new UpdateCustomAbilitiesRequest(List.of(
                        new AbilitiesSnapshot.CustomAbilityView(" ", "x")))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("needs a name");

        // use-by-name would silently pick the first duplicate — rejected up front
        assertThatThrownBy(() -> service.updateCustomAbilities("conq",
                new UpdateCustomAbilitiesRequest(List.of(
                        new AbilitiesSnapshot.CustomAbilityView("Trick", "a"),
                        new AbilitiesSnapshot.CustomAbilityView("trick", "b")))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("duplicate custom ability name");
    }

    // ── Self-effects apply for BOTH resolutions (2026-07-18 fix) ──

    private GameCharacter saveCharacter(String id, String pathId, String classId, ClassResource resource) {
        var c = new GameCharacter(id);
        c.setName(id);
        c.setLevel(5);
        c.setPathId(pathId);
        c.setClassId(classId);
        c.setStats(new Stats(14, 12, 14, 10, 10, 14, 10));
        c.setHp(new HitPoints(100, 100, 0));
        c.setMana(new ManaPool(0, 0));
        c.setAp(new ActionPoints(6, 6, 10));
        c.setResource(resource);
        return repo.save(c);
    }

    @Test
    void manualAbilityStillAppliesItsStructuredSelfEffect() {
        saveCharacter("barb", "warrior", "barbarian", new ClassResource("rages", 4, 4));

        var used = service.useAbility("barb", new UseAbilityRequest("rage"));

        assertThat(used.snapshot().resource().current()).isEqualTo(3); // 1 rage spent
        assertThat(used.snapshot().activeEffects())
                .anyMatch(e -> e.id().equals("physical-resistance") && Integer.valueOf(3).equals(e.rounds()));
        // the narrative rules text still lands in the log
        assertThat(used.resolution().getSteps()).anyMatch(s -> s.rule().equals("use-ability"));
    }

    @Test
    void autoSelfEffectOnHasValueEffectUsesStacksAsValue() {
        saveCharacter("marty", "monk", "martyr", new ClassResource("focus", 30, 30));

        var used = service.useAbility("marty", new UseAbilityRequest("focus-swift-strikes"));

        assertThat(used.snapshot().resource().current()).isEqualTo(10); // 20 focus spent
        assertThat(used.snapshot().activeEffects())
                .anyMatch(e -> e.id().equals("reduced-weapon-ap-cost") && Integer.valueOf(1).equals(e.value()));
    }

    @Test
    void valuelessHasValueSelfEffectBecomesADmNoteInsteadOfCrashing() {
        saveCharacter("marty2", "monk", "martyr", new ClassResource("focus", 30, 30));

        var used = service.useAbility("marty2", new UseAbilityRequest("focus-to-temp-hp"));

        // the amount depends on the focus the player chooses to spend — DM applies it
        assertThat(used.resolution().getSteps())
                .anyMatch(s -> s.rule().equals("ability-self-effect") && s.note().contains("DM applies"));
        assertThat(used.snapshot().activeEffects()).noneMatch(e -> e.id().equals("temporary-hp"));
        assertThat(used.snapshot().hp().temp()).isEqualTo(0);
    }

    // ── Story 1.4: use-ability ──

    @Test
    void adrenalineGrantsApAndEnforcesBudgets() {
        var used = service.useAbility("conq", new UseAbilityRequest("adrenaline"));
        assertThat(used.snapshot().ap().current()).isEqualTo(9); // 6 + 3
        assertThat(used.resolution().getSteps())
                .anyMatch(s -> s.rule().equals("gain-ap"))
                .anyMatch(s -> s.rule().equals("ability-note")); // −3 AP next turn, manual pending A6

        // max once per turn
        assertThatThrownBy(() -> service.useAbility("conq", new UseAbilityRequest("adrenaline")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already used this turn");

        // turn start clears the per-turn budget; WILL +2 → 2 per rest, so a second use fits
        service.turnStart("conq");
        service.useAbility("conq", new UseAbilityRequest("adrenaline"));

        // rest budget (WILL mod 2, min 1 → 2) exhausted
        service.turnStart("conq");
        assertThatThrownBy(() -> service.useAbility("conq", new UseAbilityRequest("adrenaline")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no Adrenaline uses left until a rest");

        // 50% rest restores ceil(0.5 × 2) = 1 use
        service.rest("conq", new RestRequest(50));
        service.useAbility("conq", new UseAbilityRequest("adrenaline"));
    }

    @Test
    void perseveranceHealsSpendsThePoolAndTriggersTheEnduranceRider() {
        var c = repo.findById("conq").orElseThrow();
        c.getResource().setCurrent(10); // room for the +20 energy rider
        repo.save(c);

        var used = service.useAbility("conq", new UseAbilityRequest("indomitable-perseverance"));

        // 1 AP + 1 perseverance spent
        assertThat(used.snapshot().ap().current()).isEqualTo(5);
        var pool = used.snapshot().pools().stream()
                .filter(pv -> pv.id().equals("perseverance")).findFirst().orElseThrow();
        assertThat(pool.current()).isEqualTo(1);

        // heal: (level)d10 + level × CON mod = 10d10 + 20 → between 30 and 120, HP was 50/300
        int healed = used.snapshot().hp().current() - 50;
        assertThat(healed).isBetween(30, 120);
        assertThat(used.resolution().getPayload()).containsKey("healingRoll");

        // Endurance rider (L10): +20 energy
        assertThat(used.snapshot().resource().current()).isEqualTo(30);
    }

    @Test
    void pickedManeuverSpendsEnergyAndComputesTargetStacks() {
        service.updateKnownAbilities("conq", new UpdateAbilitiesRequest(List.of("crippling-strike")));

        var used = service.useAbility("conq", new UseAbilityRequest("crippling-strike"));
        assertThat(used.snapshot().resource().current()).isEqualTo(50); // 60 − 10 energy

        // rooted stacks = ceil(level / 3) = ceil(10/3) = 4, computed but applied at the table
        assertThat(used.resolution().getSteps())
                .anyMatch(s -> s.rule().equals("ability-target") && s.note().contains("4 rooted"));
    }

    @Test
    void unpickedManeuverIsRejected() {
        assertThatThrownBy(() -> service.useAbility("conq", new UseAbilityRequest("whirlwind")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not known");
    }

    @Test
    void manualAbilityPrintsTheRuleAfterSpending() {
        service.updateKnownAbilities("conq", new UpdateAbilitiesRequest(List.of("whirlwind")));
        var used = service.useAbility("conq", new UseAbilityRequest("whirlwind"));
        assertThat(used.snapshot().resource().current()).isEqualTo(45); // 60 − 15
        assertThat(used.resolution().getSteps())
                .anyMatch(s -> s.rule().equals("use-ability") && s.note().contains("declared on an attack"));
    }

    @Test
    void insufficientEnergyIsRejectedBeforeAnySpend() {
        service.updateKnownAbilities("conq", new UpdateAbilitiesRequest(List.of("whirlwind")));
        var c = repo.findById("conq").orElseThrow();
        c.getResource().setCurrent(5);
        repo.save(c);

        assertThatThrownBy(() -> service.useAbility("conq", new UseAbilityRequest("whirlwind")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Insufficient energy");
        // AP untouched — all-or-nothing
        assertThat(service.getCombatSnapshot("conq").ap().current()).isEqualTo(6);
    }

    @Test
    void passivesCannotBeUsed() {
        service.updateKnownAbilities("conq", new UpdateAbilitiesRequest(List.of("style-evasive")));
        assertThatThrownBy(() -> service.useAbility("conq", new UseAbilityRequest("style-evasive")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("always on");
    }

    @Test
    void furyDiceCostRollsAtSpendTime() {
        var barb = new GameCharacter("barb");
        barb.setName("Fury Tester");
        barb.setLevel(10);
        barb.setPathId("warrior");
        barb.setClassId("barbarian");
        barb.setStats(new Stats(18, 12, 16, 8, 10, 10, 10));
        barb.setHp(new HitPoints(100, 400, 0));
        barb.setMana(new ManaPool(0, 0));
        barb.setAp(new ActionPoints(6, 6, 10));
        barb.setResource(new ClassResource("rages", 5, 5));
        repo.save(barb);

        // savage-restoration: group null → implicit; costs 2 + 1d4 fury; fury may go negative
        var used = service.useAbility("barb", new UseAbilityRequest("fury-savage-restoration"));
        var fury = used.snapshot().pools().stream()
                .filter(pv -> pv.id().equals("fury")).findFirst().orElseThrow();
        assertThat(fury.current()).isBetween(-6, -3); // 0 − (2 + 1d4)
        assertThat(used.resolution().getSteps())
                .anyMatch(s -> s.rule().equals("spend-fury") && s.note().contains("rolled"));
        // heal 6d8 + (level − 8) d8 = 8d8 at L10
        assertThat(used.snapshot().hp().current()).isBetween(108, 164);
    }
}
