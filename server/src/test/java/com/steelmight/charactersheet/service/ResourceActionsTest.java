package com.steelmight.charactersheet.service;

import com.steelmight.charactersheet.dto.GainResourceRequest;
import com.steelmight.charactersheet.dto.SpendResourceRequest;
import com.steelmight.charactersheet.model.*;
import com.steelmight.charactersheet.repository.CharacterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M0-D acceptance criteria. Level-5 bard → derived max mana = 50×5 + 25 (milestone 5) = 275.
 */
@SpringBootTest
class ResourceActionsTest {

    @Autowired
    private CharacterService service;

    @Autowired
    private CharacterRepository repo;

    @BeforeEach
    void setUp() {
        repo.deleteAll();
        var c = new GameCharacter("p1");
        c.setName("Test");
        c.setLevel(5);
        c.setPathId("musician");
        c.setClassId("bard");
        c.setStats(new Stats(10, 10, 10, 10, 10, 10, 10));
        c.setHp(new HitPoints(125, 125, 0));
        c.setMana(new ManaPool(265, 275)); // 10 below derived max 275
        c.setAp(new ActionPoints(3, 6, 10));
        repo.save(c);
    }

    // Criterion 1 — validated spending, no partial spend

    @Test
    void overspendingApIsRejectedAndStateUnchanged() {
        assertThatThrownBy(() -> service.spendResource("p1", new SpendResourceRequest("ap", 5)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Insufficient ap: have 3, need 5");
        assertThat(repo.findById("p1").orElseThrow().getAp().getCurrent()).isEqualTo(3);
    }

    @Test
    void spendingExactlyAvailableSucceeds() {
        var response = service.spendResource("p1", new SpendResourceRequest("ap", 3));
        assertThat(response.snapshot().ap().current()).isZero();
        assertThat(response.resolution().getSteps())
                .anyMatch(s -> s.rule().equals("spend-ap"));
    }

    // Criterion 2 — gain capped at derived max

    @Test
    void gainingManaBeyondMaxIsCappedWithANote() {
        var response = service.gainResource("p1", new GainResourceRequest("mana", 50));
        assertThat(response.snapshot().mana().current()).isEqualTo(275);
        var step = response.resolution().getSteps().get(0);
        assertThat(step.rule()).isEqualTo("gain-mana");
        assertThat(step.note()).contains("Gained 10").contains("40 lost to cap");
    }

    @Test
    void gainingApIsCappedAtApMax() {
        var response = service.gainResource("p1", new GainResourceRequest("ap", 20));
        assertThat(response.snapshot().ap().current()).isEqualTo(10);
    }

    // Criterion 3 — unknown resources

    @Test
    void unknownResourceRejectedOnSpendAndGain() {
        assertThatThrownBy(() -> service.spendResource("p1", new SpendResourceRequest("chakra", 1)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unknown resource");
        assertThatThrownBy(() -> service.gainResource("p1", new GainResourceRequest("chakra", 1)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unknown resource");
    }

    @Test
    void classResourceSpendsAndGainsWhenPresent() {
        var c = repo.findById("p1").orElseThrow();
        c.setResource(new ClassResource("chakra", 4, 6));
        repo.save(c);

        service.spendResource("p1", new SpendResourceRequest("chakra", 3)); // 4 → 1
        var response = service.gainResource("p1", new GainResourceRequest("chakra", 10)); // capped at 6
        var step = response.resolution().getSteps().get(0);
        assertThat(step.valueAfter()).isEqualTo(6);
        assertThat(step.note()).contains("lost to cap");

        assertThatThrownBy(() -> service.spendResource("p1", new SpendResourceRequest("chakra", 7)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Insufficient chakra");
    }
}
