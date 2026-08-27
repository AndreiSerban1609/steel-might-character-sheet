package com.steelmight.charactersheet.service;

import com.steelmight.charactersheet.dto.ApplyEffectRequest;
import com.steelmight.charactersheet.dto.CastRequest;
import com.steelmight.charactersheet.dto.DamageRequest;
import com.steelmight.charactersheet.dto.EncounterView;
import com.steelmight.charactersheet.dto.MonsterTemplateRequest;
import com.steelmight.charactersheet.dto.MonsterView;
import com.steelmight.charactersheet.dto.SpawnMonstersRequest;
import com.steelmight.charactersheet.dto.StartEncounterRequest;
import com.steelmight.charactersheet.model.AbilityScore;
import com.steelmight.charactersheet.model.ActionPoints;
import com.steelmight.charactersheet.model.CombatantType;
import com.steelmight.charactersheet.model.DamageType;
import com.steelmight.charactersheet.model.GameCharacter;
import com.steelmight.charactersheet.model.HitPoints;
import com.steelmight.charactersheet.model.ManaPool;
import com.steelmight.charactersheet.model.Stats;
import com.steelmight.charactersheet.repository.CharacterRepository;
import com.steelmight.charactersheet.repository.MonsterInstanceRepository;
import com.steelmight.charactersheet.repository.MonsterTemplateRepository;
import com.steelmight.charactersheet.repository.RoomEncounterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Epic 2 Story 2.3: monsters in the turn order. They roll initiative like players (E8),
 * their turns auto-start with ticks and are ended by the GM, the dead are skipped,
 * reinforcements slot in mid-fight, spells can target them, and a character can be
 * mirrored into a Death-fight template (Story 2.5).
 */
@SpringBootTest
class MonsterEncounterTest {

    private static final String ROOM = "monster-enc-room";

    @Autowired private MonsterService monsters;
    @Autowired private EncounterService encounters;
    @Autowired private TurnFlowService turnFlow;
    @Autowired private CharacterService characterService;
    @Autowired private CharacterRepository characterRepo;
    @Autowired private MonsterTemplateRepository templateRepo;
    @Autowired private MonsterInstanceRepository instanceRepo;
    @Autowired private RoomEncounterRepository encounterRepo;

    @BeforeEach
    void setUp() {
        encounterRepo.deleteAll();
        instanceRepo.deleteAll();
        templateRepo.deleteAll();
        characterRepo.deleteAll();
        player("p1", "Alpha", "warrior", "barbarian", 10);
        player("p2", "Bravo", "warrior", "barbarian", 10);
    }

    private GameCharacter player(String id, String name, String path, String cls, int dex) {
        var c = new GameCharacter(id);
        c.setName(name);
        c.setRoomName(ROOM);
        c.setLevel(5);
        c.setPathId(path);
        c.setClassId(cls);
        c.setStats(new Stats(10, dex, 12, 10, 10, 14, 16));
        c.setHp(new HitPoints(100, 100, 0));
        c.setMana(new ManaPool(1000, 1000));
        c.setAp(new ActionPoints(6, 6, 10));
        return characterRepo.save(c);
    }

    /** DEX 50 (+20): beats every DEX-10 player whatever the d20s say (test randomness is not fixed). */
    private MonsterView goblin() {
        var t = monsters.createTemplate(ROOM, new MonsterTemplateRequest("Goblin", 4, 40, 13, 0, 0, 30, 3, 0,
                Map.of(AbilityScore.DEX, 50, AbilityScore.WILL, 8), List.of(),
                Map.of(DamageType.FIRE, 0.5), "Bites.", null));
        return monsters.spawn(ROOM, new SpawnMonstersRequest(t.id(), 1)).get(0);
    }

    private static DamageRequest trueDamage(int value) {
        return new DamageRequest(value, DamageType.TRUE, null, false, null, false);
    }

    private EncounterView.Entry entry(EncounterView view, String combatantId) {
        return view.entries().stream().filter(e -> e.playerId().equals(combatantId)).findFirst().orElseThrow();
    }

    @Test
    void monstersRollIntoTheOrderWithTheirVitalsInline_E8() {
        var g = goblin();
        var view = encounters.start(ROOM, null);

        assertThat(view.entries()).hasSize(3);
        var gob = entry(view, g.combatantId());
        assertThat(gob.combatantType()).isEqualTo(CombatantType.MONSTER);
        assertThat(gob.hp()).isEqualTo(40);
        assertThat(gob.maxHp()).isEqualTo(40);
        assertThat(gob.status()).isEqualTo("ALIVE");
        assertThat(gob.initiative()).isBetween(21, 40); // d20 + 20
        assertThat(entry(view, "p1").combatantType()).isEqualTo(CombatantType.PLAYER);
        assertThat(entry(view, "p1").hp()).isNull();
        assertThat(view.currentPlayerId()).isEqualTo(g.combatantId());
    }

    @Test
    void monsterTurnsAutoStartWithTicksAndAreEndedByTheGm() {
        var g = goblin();
        monsters.applyEffect(ROOM, g.id(),
                new ApplyEffectRequest("burning", 4, null, null, "test", false, false, false, null));

        encounters.start(ROOM, null);
        turnFlow.autoStartCurrent(ROOM); // what the /encounter/start route does next
        var opened = encounters.get(ROOM);
        assertThat(opened.currentPlayerId()).isEqualTo(g.combatantId());
        assertThat(opened.turnStarted()).isTrue();
        // 4 burning ticked at the goblin's turn start: 4 fire × 0.5 innate = 2.
        assertThat(entry(opened, g.combatantId()).hp()).isEqualTo(38);

        // Players can't act on the goblin's turn.
        assertThatThrownBy(() -> characterService.turnEnd("p1"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("not your turn");

        // The GM ends it: the order advances and the next PLAYER's turn auto-starts,
        // its first-turn AP note merged into the goblin's log under a name prefix.
        var ended = monsters.turnEnd(ROOM, g.id());
        var afterGoblin = encounters.get(ROOM);
        String nextId = afterGoblin.currentPlayerId();
        assertThat(nextId).isIn("p1", "p2");
        assertThat(afterGoblin.turnStarted()).isTrue();
        String nextName = entry(afterGoblin, nextId).name();
        assertThat(ended.resolution().getSteps())
                .anySatisfy(s -> assertThat(s.rule()).isEqualTo("turn-order"))
                .anySatisfy(s -> assertThat(s.rule()).isEqualTo(nextName + ":ap-recovery"));

        // Both players end their turns → round 2 wraps back to the goblin, whose
        // turn-start ticks (burning again) land in the last player's log.
        characterService.turnEnd(nextId);
        var lastId = encounters.get(ROOM).currentPlayerId();
        var wrap = characterService.turnEnd(lastId);
        var round2 = encounters.get(ROOM);
        assertThat(round2.round()).isEqualTo(2);
        assertThat(round2.currentPlayerId()).isEqualTo(g.combatantId());
        assertThat(wrap.resolution().getSteps())
                .anySatisfy(s -> assertThat(s.rule()).startsWith("Goblin:burning:"));
        assertThat(entry(round2, g.combatantId()).hp()).isEqualTo(36);
    }

    @Test
    void deadMonstersAreSkippedInTheOrder() {
        var g = goblin();
        encounters.start(ROOM, null);
        turnFlow.autoStartCurrent(ROOM);
        monsters.turnEnd(ROOM, g.id());

        monsters.damage(ROOM, g.id(), trueDamage(100));
        assertThat(entry(encounters.get(ROOM), g.combatantId()).status()).isEqualTo("DEAD");

        // Two player turn ends wrap the round — the corpse never becomes current.
        for (int i = 0; i < 2; i++) {
            characterService.turnEnd(encounters.get(ROOM).currentPlayerId());
            assertThat(encounters.get(ROOM).currentPlayerId()).isNotEqualTo(g.combatantId());
        }
        assertThat(encounters.get(ROOM).round()).isEqualTo(2);
    }

    @Test
    void reinforcementsJoinAndDespawnsLeaveARunningOrder() {
        goblin();
        encounters.start(ROOM, null);
        turnFlow.autoStartCurrent(ROOM);
        var before = encounters.get(ROOM);
        assertThat(before.entries()).hasSize(3);

        var second = monsters.spawn(ROOM, new SpawnMonstersRequest(templateRepo.findAll().get(0).getId(), 1)).get(0);
        var joined = encounters.get(ROOM);
        assertThat(joined.entries()).hasSize(4);
        assertThat(entry(joined, second.combatantId()).name()).isEqualTo("Goblin 2");
        // The current turn is untouched by a reinforcement arriving.
        assertThat(joined.currentPlayerId()).isEqualTo(before.currentPlayerId());
        assertThat(joined.turnStarted()).isTrue();

        monsters.delete(ROOM, second.id());
        assertThat(encounters.get(ROOM).entries()).hasSize(3);
        assertThat(encounters.get(ROOM).entries()).noneMatch(e -> e.playerId().equals(second.combatantId()));
    }

    @Test
    void aSurprisedMonsterSkipsTheSurpriseRound() {
        var g = goblin();
        var view = encounters.start(ROOM, new StartEncounterRequest(null, List.of(g.combatantId())));
        assertThat(view.round()).isZero();
        assertThat(entry(view, g.combatantId()).surprised()).isTrue();
        assertThat(view.currentPlayerId()).isIn("p1", "p2"); // goblin out-rolled everyone but is skipped
    }

    @Test
    void spellsCanTargetAMonsterWithTheirEffects() {
        var g = goblin();
        var bard = player("bard", "Lyra", "musician", "bard", 10);
        bard.getKnownSpells().add("sad-story");
        characterRepo.save(bard);

        var cast = characterService.cast("bard",
                new CastRequest("sad-story", null, null, null, g.combatantId(), null));
        assertThat(cast.resolution().getPayload().get("effectsAppliedTo")).isEqualTo(g.combatantId());
        assertThat(cast.snapshot().activeEffects()).anyMatch(e -> e.id().equals("concentrating"));

        var target = monsters.get(ROOM, g.id());
        assertThat(target.activeEffects()).extracting("id")
                .contains("difficult-terrain", "obscured-vision");

        // Unknown target costs nothing (all-or-nothing) — and is a 404, not a 500.
        assertThatThrownBy(() -> characterService.cast("bard",
                new CastRequest("sad-story", null, null, null, "monster:999", null)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
    }

    @Test
    void tauntForcesOffensiveActionsOntoTheHolder() {
        var g = goblin();
        // Alpha taunts the goblin: the effect's source IS the taunter's combatant id.
        monsters.applyEffect(ROOM, g.id(),
                new ApplyEffectRequest("taunted", 1, null, 3, "p1", false, false, false, null));

        var atBravo = new DamageRequest(5, DamageType.SLASHING, null, false, null, false, null, g.combatantId());
        assertThatThrownBy(() -> characterService.damage("p2", atBravo))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("taunted by Alpha");
        assertThat(characterService.getCombatSnapshot("p2").hp().current()).isEqualTo(100); // nothing spent

        // The taunter is a legal target …
        var atAlpha = characterService.damage("p1", atBravo);
        assertThat(atAlpha.snapshot().hp().current()).isEqualTo(95);

        // … and the taunt dies with its holder.
        characterService.damage("p1", trueDamage(1000));
        assertThat(characterService.getCombatSnapshot("p1").status()).isNotEqualTo("ALIVE");
        assertThat(characterService.damage("p2", atBravo).snapshot().hp().current()).isEqualTo(95);

        // Casters too: a taunted bard may only aim harmful spell effects at the taunter.
        var bard = player("bard", "Lyra", "musician", "bard", 10);
        bard.getKnownSpells().add("sad-story");
        characterRepo.save(bard);
        characterService.applyEffect("bard",
                new ApplyEffectRequest("taunted", 1, null, 3, g.combatantId(), false, false, false, null));
        assertThatThrownBy(() -> characterService.cast("bard",
                new CastRequest("sad-story", null, null, null, "p2", null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("taunted by Goblin");
        var onGoblin = characterService.cast("bard",
                new CastRequest("sad-story", null, null, null, g.combatantId(), null));
        assertThat(onGoblin.resolution().getPayload().get("effectsAppliedTo")).isEqualTo(g.combatantId());
    }

    @Test
    void deathFightTemplateMirrorsTheCharacter() {
        var snap = characterService.getCombatSnapshot("p1");
        var t = monsters.templateFromCharacter(ROOM, "p1");
        assertThat(t.name()).isEqualTo("Alpha (death fight)");
        assertThat(t.level()).isEqualTo(5);
        assertThat(t.maxHp()).isEqualTo(snap.hp().max());
        assertThat(t.ac()).isEqualTo(snap.ac());
        assertThat(t.stats()).containsEntry(AbilityScore.CHA, 16);

        var clone = monsters.spawn(ROOM, new SpawnMonstersRequest(t.id(), 1)).get(0);
        assertThat(clone.hp().max()).isEqualTo(snap.hp().max());
        assertThat(clone.name()).isEqualTo("Alpha (death fight)");
    }
}
