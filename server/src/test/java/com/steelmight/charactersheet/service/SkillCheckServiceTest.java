package com.steelmight.charactersheet.service;

import com.steelmight.charactersheet.dto.CardType;
import com.steelmight.charactersheet.model.*;
import com.steelmight.charactersheet.repository.CharacterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class SkillCheckServiceTest {

    @Autowired
    private SkillCheckService service;

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
        c.setStats(new Stats(10, 14, 12, 13, 8, 11, 16)); // DEX 14 -> +2, STR 10 -> +0
        c.setHp(new HitPoints(100, 100, 0));
        c.setMana(new ManaPool(0, 0));
        c.setAp(new ActionPoints(6, 6, 10));
        c.getProficiencies().add("stealth");
        repo.save(c);
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
}
