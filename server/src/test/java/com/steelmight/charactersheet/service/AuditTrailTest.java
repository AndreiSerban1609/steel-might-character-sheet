package com.steelmight.charactersheet.service;

import com.steelmight.charactersheet.dto.ApplyEffectRequest;
import com.steelmight.charactersheet.dto.DamageRequest;
import com.steelmight.charactersheet.model.*;
import com.steelmight.charactersheet.repository.AuditEntryRepository;
import com.steelmight.charactersheet.repository.CharacterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Room activity log: every adjudication-prone mutation leaves a reviewable line. */
@SpringBootTest
class AuditTrailTest {

    @Autowired
    private CharacterService service;

    @Autowired
    private AuditService audit;

    @Autowired
    private CharacterRepository repo;

    @Autowired
    private AuditEntryRepository auditRepo;

    @Autowired
    private SkillCheckService skillChecks;

    @BeforeEach
    void setUp() {
        auditRepo.deleteAll();
        repo.deleteAll();
        var c = new GameCharacter("p1");
        c.setName("Logged");
        c.setRoomName("audit-room");
        c.setLevel(5);
        c.setPathId("musician");
        c.setClassId("bard");
        c.setStats(new Stats(10, 10, 10, 10, 10, 10, 10));
        c.setHp(new HitPoints(100, 125, 0));
        c.setMana(new ManaPool(0, 0));
        c.setAp(new ActionPoints(6, 6, 10));
        repo.save(c);
    }

    @Test
    void actionsLandInTheRoomFeedNewestFirst() {
        service.applyEffect("p1", new ApplyEffectRequest("burning", 3, null, null, "test",
                false, false, false, null));
        service.damage("p1", new DamageRequest(10, DamageType.FIRE, null, false, null, false, null));
        service.removeEffect("p1", "burning");

        var feed = audit.recent("audit-room", 10);
        assertThat(feed).hasSize(3);
        assertThat(feed.get(0).action()).isEqualTo("remove-effect");
        assertThat(feed.get(0).summary()).isEqualTo("Removed burning");
        assertThat(feed.get(0).characterName()).isEqualTo("Logged");
        assertThat(feed.get(1).action()).isEqualTo("damage");
        assertThat(feed.get(1).summary()).contains("10 FIRE").contains("HP lost");
        assertThat(feed.get(2).action()).isEqualTo("apply-effect");
        assertThat(feed.get(2).summary()).isEqualTo("Applied burning ×3");
        assertThat(feed.get(0).time()).isNotNull();
    }

    @Test
    void skillDrawsLandInTheFeed() {
        skillChecks.draw("p1", "stealth");

        var feed = audit.recent("audit-room", 5);
        assertThat(feed.get(0).action()).isEqualTo("skill-check");
        assertThat(feed.get(0).summary()).startsWith("Drew stealth");
    }

    @Test
    void charactersWithoutARoomAreNotLogged() {
        var loner = new GameCharacter("loner");
        loner.setName("Loner");
        loner.setLevel(5);
        loner.setPathId("musician");
        loner.setClassId("bard");
        loner.setStats(new Stats(10, 10, 10, 10, 10, 10, 10));
        loner.setHp(new HitPoints(100, 125, 0));
        loner.setMana(new ManaPool(0, 0));
        loner.setAp(new ActionPoints(6, 6, 10));
        repo.save(loner);

        service.heal("loner", new com.steelmight.charactersheet.dto.HealRequest(5));

        assertThat(auditRepo.count()).isZero();
    }

    @Test
    void playerCombatLogIsSelfScopedAndCombatOnly() {
        var other = new GameCharacter("p2");
        other.setName("Someone Else");
        other.setRoomName("audit-room");
        other.setLevel(5);
        other.setPathId("musician");
        other.setClassId("bard");
        other.setStats(new Stats(10, 10, 10, 10, 10, 10, 10));
        other.setHp(new HitPoints(100, 125, 0));
        other.setMana(new ManaPool(0, 0));
        other.setAp(new ActionPoints(6, 6, 10));
        repo.save(other);

        service.damage("p1", new DamageRequest(10, DamageType.FIRE, null, false, null, false, null));
        service.damage("p2", new DamageRequest(7, DamageType.FIRE, null, false, null, false, null));
        var c = repo.findById("p1").orElseThrow();
        audit.log(c, "purchase", "Bought a very expensive hat"); // shopping is not combat
        audit.log(c, "inventory-edit", "GM set gold");

        var log = audit.combatLogFor("p1", 20);
        assertThat(log).allMatch(e -> e.playerId().equals("p1"));
        assertThat(log).extracting(a -> a.action())
                .containsExactly("damage")
                .doesNotContain("purchase", "inventory-edit");

        // The room feed still sees everything — this filter is the player's view only.
        assertThat(audit.recent("audit-room", 20)).hasSize(4);
    }

    @Test
    void feedIsPrunedOncePastTheCap() {
        var c = repo.findById("p1").orElseThrow();
        for (int i = 0; i < 500; i++) {
            audit.log(c, "test", "entry " + i);
        }
        // pruning is amortized (every PRUNE_EVERY logs), so the transient bound is
        // CAP + PRUNE_EVERY; the steady state trims back down to KEEP.
        long count = auditRepo.countByRoomName("audit-room");
        assertThat(count).isLessThanOrEqualTo(450);
        // newest entries survive
        List<String> recent = audit.recent("audit-room", 5).stream()
                .map(a -> a.summary()).toList();
        assertThat(recent.get(0)).isEqualTo("entry 499");
    }
}
