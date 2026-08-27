package com.steelmight.charactersheet.service;

import com.steelmight.charactersheet.dto.PrepareReactionRequest;
import com.steelmight.charactersheet.dto.ResolveReactionRequest;
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
 * Prepared reactions (2026-08-27 Game Owner): custom reactions — "roll out of the way",
 * "extinguish my torch when X" — cost AP on the turn they are prepared. The server holds
 * the declaration so the table sees it; it lapses at the start of the preparer's next
 * turn. The AP never comes back (used or cancelled). Also: spend-resource's free-text note.
 */
@SpringBootTest
class PreparedReactionTest {

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
        c.setMana(new ManaPool(265, 275));
        c.setAp(new ActionPoints(3, 6, 10));
        repo.save(c);
    }

    @Test
    void preparingSpendsApNowAndShowsInTheSnapshot() {
        var r = service.prepareReaction("p1", new PrepareReactionRequest("roll out of the way", 2));

        assertThat(r.snapshot().ap().current()).isEqualTo(1);
        assertThat(r.snapshot().preparedReactions()).hasSize(1);
        assertThat(r.snapshot().preparedReactions().get(0).note()).isEqualTo("roll out of the way");
        assertThat(r.snapshot().preparedReactions().get(0).apCost()).isEqualTo(2);
        assertThat(r.resolution().getSteps()).anySatisfy(s -> {
            assertThat(s.rule()).isEqualTo("prepare-reaction");
            assertThat(s.note()).contains("roll out of the way").contains("2 AP");
        });
    }

    @Test
    void preparingBeyondAvailableApIsRejectedAndNothingIsStored() {
        assertThatThrownBy(() -> service.prepareReaction("p1", new PrepareReactionRequest("dodge", 5)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Insufficient ap: have 3, need 5");
        var c = repo.findById("p1").orElseThrow();
        assertThat(c.getAp().getCurrent()).isEqualTo(3);
        assertThat(c.getPreparedReactions()).isEmpty();
    }

    @Test
    void aFreePrepIsAllowedByRuling() {
        var r = service.prepareReaction("p1", new PrepareReactionRequest("extinguish my torch when they enter", 0));
        assertThat(r.snapshot().ap().current()).isEqualTo(3);
        assertThat(r.snapshot().preparedReactions()).hasSize(1);
        assertThat(r.resolution().getSteps()).noneMatch(s -> s.rule().equals("spend-ap"));
    }

    @Test
    void resolvingUsedOrCancelledRemovesItWithoutRefund() {
        service.prepareReaction("p1", new PrepareReactionRequest("roll out of the way", 1));
        service.prepareReaction("p1", new PrepareReactionRequest("extinguish my torch", 1));

        var used = service.resolveReaction("p1", new ResolveReactionRequest(0, true));
        assertThat(used.snapshot().ap().current()).isEqualTo(1);
        assertThat(used.snapshot().preparedReactions()).extracting("note").containsExactly("extinguish my torch");
        assertThat(used.resolution().getSteps()).anySatisfy(s ->
                assertThat(s.note()).contains("used").contains("roll out of the way"));

        var cancelled = service.resolveReaction("p1", new ResolveReactionRequest(0, false));
        assertThat(cancelled.snapshot().ap().current()).isEqualTo(1); // still spent
        assertThat(cancelled.snapshot().preparedReactions()).isEmpty();
        assertThat(cancelled.resolution().getSteps()).anySatisfy(s ->
                assertThat(s.note()).contains("cancelled").contains("stays spent"));
    }

    @Test
    void resolvingAMissingIndexIs400() {
        assertThatThrownBy(() -> service.resolveReaction("p1", new ResolveReactionRequest(0, true)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no prepared reaction at index 0");
    }

    @Test
    void preparedReactionsLapseWhenTheNextTurnStarts() {
        service.prepareReaction("p1", new PrepareReactionRequest("roll out of the way", 1));

        var r = service.turnStart("p1"); // free play: ticks + AP recovery + player resets
        assertThat(r.snapshot().preparedReactions()).isEmpty();
        assertThat(r.resolution().getSteps()).anySatisfy(s -> {
            assertThat(s.rule()).isEqualTo("prepared-reaction");
            assertThat(s.note()).contains("expired unused").contains("roll out of the way");
        });
        assertThat(repo.findById("p1").orElseThrow().getPreparedReactions()).isEmpty();
    }

    @Test
    void combatStartClearsLeftoverPreparations() {
        service.prepareReaction("p1", new PrepareReactionRequest("roll out of the way", 1));
        var r = service.combatStart("p1");
        assertThat(r.snapshot().preparedReactions()).isEmpty();
        assertThat(r.resolution().getSteps()).anySatisfy(s -> assertThat(s.note()).contains("cleared at combat start"));
    }

    @Test
    void spendResourceCarriesTheNoteIntoTheLog() {
        var r = service.spendResource("p1", new SpendResourceRequest("ap", 1, "moved 20 ft"));
        assertThat(r.snapshot().ap().current()).isEqualTo(2);
        assertThat(r.resolution().getSteps()).anySatisfy(s -> {
            assertThat(s.rule()).isEqualTo("spend-ap");
            assertThat(s.note()).isEqualTo("Spent 1 AP — moved 20 ft");
        });
        // The two-arg form (older callers) still prints the bare line.
        var bare = service.spendResource("p1", new SpendResourceRequest("ap", 1));
        assertThat(bare.resolution().getSteps().get(0).note()).isEqualTo("Spent 1 AP");
    }
}
