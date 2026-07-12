package com.steelmight.charactersheet.service;

import com.steelmight.charactersheet.dto.CardType;
import com.steelmight.charactersheet.dto.DeckCard;
import com.steelmight.charactersheet.dto.DeckTemplate;
import com.steelmight.charactersheet.dto.PlayerDeckConfig;
import com.steelmight.charactersheet.dto.RestRequest;
import com.steelmight.charactersheet.dto.SkillCheckResult;
import com.steelmight.charactersheet.engine.StatDerivationEngine;
import com.steelmight.charactersheet.model.*;
import com.steelmight.charactersheet.repository.CharacterRepository;
import com.steelmight.charactersheet.repository.PlayerDeckRepository;
import com.steelmight.charactersheet.repository.RoomDeckRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class SkillCheckServiceTest {

    @Autowired
    private SkillCheckService service;

    @Autowired
    private CharacterRepository repo;

    @Autowired
    private PlayerDeckRepository playerDeckRepo;

    @Autowired
    private RoomDeckRepository roomDeckRepo;

    @Autowired
    private StatDerivationEngine engine;

    @Autowired
    private DeckTemplateService decks;

    @Autowired
    private CharacterService characterService;

    private GameCharacter character;

    @BeforeEach
    void setUp() {
        repo.deleteAll();
        playerDeckRepo.deleteAll();
        roomDeckRepo.deleteAll();
        var c = new GameCharacter("p1");
        c.setName("Test");
        c.setLevel(5);
        c.setPathId("musician");
        c.setClassId("bard");
        c.setStats(new Stats(10, 14, 12, 13, 8, 11, 16)); // DEX 14 -> +2, STR 10 -> +0
        c.setHp(new HitPoints(100, 100, 0));
        c.setMana(new ManaPool(0, 0));
        c.setAp(new ActionPoints(6, 6, 10));
        c.getProficiencies().add("stealth");
        character = repo.save(c);
    }

    /** Room with only the 2 locked criticals as base (plus the player's extras). */
    private void tinyRoom(String room) {
        character.setRoomName(room);
        character = repo.save(character);
        decks.updateTemplate(room, new DeckTemplate(List.of(), 0, List.of()));
    }

    private void extras(DeckCard... cards) {
        decks.updatePlayerConfig("p1", new PlayerDeckConfig(0, List.of(cards)));
    }

    @Test
    void drawResolvesConsistentlyAcrossManyDraws() {
        for (int i = 0; i < 80; i++) {
            var r = service.draw("p1", "stealth");
            assertThat(r.ability()).isEqualTo("DEX");
            assertThat(r.proficient()).isTrue();
            assertThat(r.d10()).isBetween(1, 10);
            assertThat(r.card()).isNotNull();

            if (r.critical()) {
                assertThat(r.total()).isNull();
                assertThat(r.effectiveModifier()).isNull();
            } else {
                assertThat(r.total()).isEqualTo(r.d10() + r.effectiveModifier());
                switch (r.card().type()) {
                    case STAT -> assertThat(r.effectiveModifier()).isEqualTo(2); // DEX +2
                    case NEUTRAL -> assertThat(r.effectiveModifier()).isEqualTo(0);
                    case ENCOUNTER -> assertThat(r.effectiveModifier()).isEqualTo(-1);
                    default -> { /* class cards not in the default deck */ }
                }
            }
        }
    }

    @Test
    void nonProficientSkillFlaggedFalse() {
        var r = service.draw("p1", "athletics");
        assertThat(r.ability()).isEqualTo("STR");
        assertThat(r.proficient()).isFalse();
    }

    @Test
    void unknownSkillRejected() {
        assertThatThrownBy(() -> service.draw("p1", "flying")).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void proficientCheckGrantsProficiencyBonusManyRedraws() {
        var r = service.draw("p1", "stealth");
        assertThat(r.redrawsUsed()).isZero();
        assertThat(r.redrawsRemaining()).isEqualTo(engine.computeProficiencyBonus(character));
    }

    @Test
    void nonProficientCheckGrantsNoRedraws() {
        var r = service.draw("p1", "athletics");
        assertThat(r.redrawsRemaining()).isZero();
        assertThatThrownBy(() -> service.redraw("p1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no redraws remaining");
    }

    @Test
    void redrawKeepsTheDieAndCountsDown() {
        var first = service.draw("p1", "stealth");
        int budget = first.redrawsRemaining();
        assertThat(budget).isGreaterThan(0);

        var previous = first;
        for (int i = 1; i <= budget; i++) {
            var r = service.redraw("p1");
            assertThat(r.d10()).isEqualTo(first.d10()); // the die never re-rolls
            assertThat(r.skillId()).isEqualTo("stealth");
            assertThat(r.proficient()).isTrue();
            assertThat(r.redrawsUsed()).isEqualTo(i);
            assertThat(r.redrawsRemaining()).isEqualTo(budget - i);
            if (!r.critical()) {
                assertThat(r.total()).isEqualTo(first.d10() + r.effectiveModifier());
            }
            previous = r;
        }

        assertThat(previous.redrawsRemaining()).isZero();
        assertThatThrownBy(() -> service.redraw("p1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no redraws remaining");
    }

    @Test
    void redrawWithoutADrawRejected() {
        var c = new GameCharacter("p2-never-drew");
        c.setName("Fresh");
        c.setLevel(3);
        c.setPathId("musician");
        c.setClassId("bard");
        c.setStats(new Stats(10, 10, 10, 10, 10, 10, 10));
        c.setHp(new HitPoints(50, 50, 0));
        c.setMana(new ManaPool(0, 0));
        c.setAp(new ActionPoints(6, 6, 10));
        repo.save(c);

        assertThatThrownBy(() -> service.redraw("p2-never-drew"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no skill check in progress");
    }

    @Test
    void newDrawResetsTheRedrawBudget() {
        service.draw("p1", "stealth");
        service.redraw("p1");
        var fresh = service.draw("p1", "stealth");
        assertThat(fresh.redrawsUsed()).isZero();
        assertThat(fresh.redrawsRemaining()).isEqualTo(engine.computeProficiencyBonus(character));
    }

    @Test
    void wrongCheckClassCardAutoPassesWithoutCostingRedraws() {
        tinyRoom("wc-room");
        extras(new DeckCard("Sniper's Eye", 5, "", "athletics", null, null, null));

        boolean sawSkip = false;
        for (int i = 0; i < 40; i++) {
            var r = service.draw("p1", "stealth"); // deck: 2 criticals + the athletics-only card
            assertThat(r.card().type()).isNotEqualTo(CardType.CLASS); // never resolves on the wrong check
            if (!r.passedCards().isEmpty()) {
                sawSkip = true;
                assertThat(r.passedCards().get(0).reason()).isEqualTo("wrong-check");
                assertThat(r.passedCards().get(0).card().name()).isEqualTo("Sniper's Eye");
                assertThat(r.bonusTotal()).isZero(); // a wrong-check pass grants no bonus
                assertThat(r.redrawsUsed()).isZero(); // and costs no player redraw
            }
        }
        assertThat(sawSkip).isTrue();
    }

    @Test
    void matchingCheckClassCardResolvesNormally() {
        tinyRoom("mc-room");
        extras(new DeckCard("Sniper's Eye", 5, "", "stealth", null, null, null));

        for (int i = 0; i < 40; i++) {
            var r = service.draw("p1", "stealth");
            if (r.card().type() == CardType.CLASS) {
                assertThat(r.effectiveModifier()).isEqualTo(5);
                assertThat(r.total()).isEqualTo(r.d10() + 5);
                return;
            }
        }
        throw new AssertionError("class card never resolved in 40 draws of a 3-card deck");
    }

    @Test
    void redrawBonusCardIsPassedAndItsBonusAddsToTheTotal() {
        tinyRoom("rb-room");
        decks.updateTemplate("rb-room",
                new DeckTemplate(List.of(new DeckCard("Neutral", 0, "")), 0, List.of()));
        extras(new DeckCard("Inspiration", 0, "", null, 2, null, null));

        for (int i = 0; i < 60; i++) {
            var r = service.draw("p1", "stealth"); // deck: 2 criticals + neutral + bonus card
            assertThat(r.card().name()).isNotEqualTo("Inspiration"); // always passed, never final
            if (!r.passedCards().isEmpty()) {
                assertThat(r.passedCards().get(0).reason()).isEqualTo("redraw-bonus");
                assertThat(r.bonusTotal()).isEqualTo(2);
                assertThat(r.redrawBonuses()).hasSize(1);
                if (!r.critical()) {
                    assertThat(r.total()).isEqualTo(r.d10() + r.effectiveModifier() + 2);
                }
                return;
            }
        }
        throw new AssertionError("bonus card never drawn first in 60 draws of a 4-card deck");
    }

    @Test
    void consumeCardLeavesTheDeckUntilRest() {
        tinyRoom("cons-room");
        extras(new DeckCard("Trick", 3, "", null, null, "consume", null));

        drawUntilClassCardIsFinal("Trick");
        var accepted = service.accept("p1");
        assertThat(accepted.cardRemoved()).isTrue();
        assertThat(accepted.removal()).isEqualTo("consume");

        assertThat(decks.getPlayerConfig("p1").extraCards().get(0).consumed()).isTrue();
        assertThat(decks.effectiveDeck(repo.findById("p1").orElseThrow())).hasSize(2); // criticals only

        characterService.rest("p1", new RestRequest(100));
        assertThat(decks.getPlayerConfig("p1").extraCards().get(0).consumed()).isNull();
        assertThat(decks.effectiveDeck(repo.findById("p1").orElseThrow())).hasSize(3);
    }

    @Test
    void burnCardIsRemovedPermanently() {
        tinyRoom("burn-room");
        extras(new DeckCard("Last Resort", 8, "", null, null, "burn", null));

        drawUntilClassCardIsFinal("Last Resort");
        var accepted = service.accept("p1");
        assertThat(accepted.cardRemoved()).isTrue();
        assertThat(accepted.removal()).isEqualTo("burn");

        assertThat(decks.getPlayerConfig("p1").extraCards()).isEmpty();
        characterService.rest("p1", new RestRequest(100));
        assertThat(decks.getPlayerConfig("p1").extraCards()).isEmpty(); // burn survives rest
    }

    @Test
    void acceptingANonClassCardRemovesNothing() {
        tinyRoom("plain-room");
        extras(new DeckCard("Trick", 3, "", null, null, "consume", null));

        for (int i = 0; i < 60; i++) {
            var r = service.draw("p1", "stealth");
            if (r.card().type() != CardType.CLASS) {
                var accepted = service.accept("p1");
                assertThat(accepted.cardRemoved()).isFalse();
                assertThat(decks.getPlayerConfig("p1").extraCards().get(0).consumed()).isNull();
                return;
            }
        }
        throw new AssertionError("never drew a critical in 60 draws of a 3-card deck");
    }

    private SkillCheckResult drawUntilClassCardIsFinal(String name) {
        for (int i = 0; i < 100; i++) {
            var r = service.draw("p1", "stealth");
            if (r.card().type() == CardType.CLASS && name.equals(r.card().name())) return r;
        }
        throw new AssertionError("class card '" + name + "' never resolved as final in 100 draws");
    }
}
