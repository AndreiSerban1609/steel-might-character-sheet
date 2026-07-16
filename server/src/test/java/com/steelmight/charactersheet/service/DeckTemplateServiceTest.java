package com.steelmight.charactersheet.service;

import com.steelmight.charactersheet.dto.CardType;
import com.steelmight.charactersheet.dto.DeckCard;
import com.steelmight.charactersheet.dto.DeckTemplate;
import com.steelmight.charactersheet.dto.PlayerDeckConfig;
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
class DeckTemplateServiceTest {

    @Autowired
    private DeckTemplateService service;

    @Autowired
    private RoomDeckRepository roomRepo;

    @Autowired
    private PlayerDeckRepository playerRepo;

    @Autowired
    private CharacterRepository characterRepo;

    @BeforeEach
    void setUp() {
        playerRepo.deleteAll();
        roomRepo.deleteAll();
        characterRepo.deleteAll();
    }

    // ---- Room template ----

    @Test
    void defaultWhenUnset() {
        var t = service.getTemplate("nosuchroom");
        assertThat(t.neutralCards()).hasSize(5);
        assertThat(t.statCount()).isEqualTo(4);
        assertThat(t.encounterCards()).hasSize(3);
    }

    @Test
    void defaultBuildsFourteenCardDeck() {
        var deck = service.buildDeck(service.getTemplate("nosuchroom"));
        assertThat(deck).hasSize(14); // 2 criticals + 5 neutral + 4 stat + 3 encounter
        assertThat(deck.stream().filter(c -> c.type() == CardType.STAT).count()).isEqualTo(4);
        assertThat(deck.stream().filter(c -> c.type() == CardType.NEUTRAL).count()).isEqualTo(5);
        assertThat(deck.stream().filter(c -> c.type() == CardType.ENCOUNTER).count()).isEqualTo(3);
    }

    @Test
    void updatePersistsAndReshapesDeck() {
        service.updateTemplate("demo", new DeckTemplate(
                List.of(new DeckCard("Lucky", 1, "")),
                6,
                List.of(new DeckCard("Trap", -2, ""))));

        var read = service.getTemplate("demo");
        assertThat(read.statCount()).isEqualTo(6);
        assertThat(read.neutralCards()).hasSize(1);
        assertThat(read.encounterCards().get(0).name()).isEqualTo("Trap");
        assertThat(service.buildDeck(read)).hasSize(10); // 2 + 1 + 6 + 1
    }

    @Test
    void rejectsBadStatCount() {
        assertThatThrownBy(() -> service.updateTemplate("demo", new DeckTemplate(List.of(), 99, List.of())))
                .isInstanceOf(ResponseStatusException.class);
    }

    // ---- Player config ----

    @Test
    void buildDeckAppliesPlayerStatAdjustAndExtras() {
        var room = service.getTemplate("nosuchroom"); // default: 5 neutral, 4 stat, 3 encounter
        var config = new PlayerDeckConfig(2, List.of(new DeckCard("Lucky Charm", 2, "")));
        var deck = service.buildDeck(room, config);

        assertThat(deck).hasSize(17); // 2 crit + 5 neutral + (4+2) stat + 3 encounter + 1 extra
        assertThat(deck.stream().filter(c -> c.type() == CardType.STAT).count()).isEqualTo(6);
        assertThat(deck.stream().filter(c -> c.type() == CardType.CLASS).count()).isEqualTo(1);
    }

    @Test
    void playerDeckPersistsAndViewCombinesWithRoom() {
        var ch = new GameCharacter("p1");
        ch.setName("Test");
        ch.setLevel(5);
        ch.setPathId("musician");
        ch.setClassId("bard");
        ch.setRoomName("demo");
        ch.setStats(new Stats(10, 10, 10, 10, 10, 10, 10));
        ch.setHp(new HitPoints(100, 100, 0));
        ch.setMana(new ManaPool(0, 0));
        ch.setAp(new ActionPoints(6, 6, 10));
        characterRepo.save(ch);

        service.updatePlayerDeck("p1", new PlayerDeckConfig(-1, List.of()));

        var view = service.getPlayerDeckView("p1");
        assertThat(view.config().statAdjust()).isEqualTo(-1);
        assertThat(view.room().statCount()).isEqualTo(4); // demo room has no saved deck -> default
        assertThat(view.deckSize()).isEqualTo(13); // 2 + 5 + (4-1) + 3
    }

    @Test
    void rejectsBadStatAdjust() {
        assertThatThrownBy(() -> service.updatePlayerConfig("p1", new PlayerDeckConfig(99, List.of())))
                .isInstanceOf(ResponseStatusException.class);
    }

    // ---- Disabled encounter cards ----

    @Test
    void buildDeckSkipsDisabledEncounterCards() {
        var room = service.getTemplate("nosuchroom"); // default: 3 encounter (Stumble, Distraction, Bad Luck)
        var config = new PlayerDeckConfig(0, List.of(), List.of(0, 2));
        var deck = service.buildDeck(room, config);

        assertThat(deck).hasSize(12); // 14 default - 2 disabled encounters
        var encounters = deck.stream().filter(c -> c.type() == CardType.ENCOUNTER).toList();
        assertThat(encounters).hasSize(1);
        assertThat(encounters.get(0).name()).isEqualTo("Distraction");
    }

    @Test
    void disabledEncountersPersistAndOutOfRangeIndicesAreHarmless() {
        service.updatePlayerConfig("p1", new PlayerDeckConfig(0, List.of(), List.of(1, 7)));

        var config = service.getPlayerConfig("p1");
        assertThat(config.disabledEncounters()).containsExactlyInAnyOrder(1, 7);
        // index 7 points past the default room's 3 encounters -> simply no-ops
        var deck = service.buildDeck(service.getTemplate("nosuchroom"), config);
        assertThat(deck.stream().filter(c -> c.type() == CardType.ENCOUNTER).count()).isEqualTo(2);
    }

    @Test
    void rejectsNegativeDisabledEncounterIndex() {
        assertThatThrownBy(() -> service.updatePlayerConfig("p1",
                new PlayerDeckConfig(0, List.of(), List.of(-1))))
                .isInstanceOf(ResponseStatusException.class);
    }
}
