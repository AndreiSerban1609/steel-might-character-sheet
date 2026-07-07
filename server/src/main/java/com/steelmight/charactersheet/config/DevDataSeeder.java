package com.steelmight.charactersheet.config;

import com.steelmight.charactersheet.engine.StatDerivationEngine;
import com.steelmight.charactersheet.model.ActionPoints;
import com.steelmight.charactersheet.model.GameCharacter;
import com.steelmight.charactersheet.model.HitPoints;
import com.steelmight.charactersheet.model.ManaPool;
import com.steelmight.charactersheet.model.Stats;
import com.steelmight.charactersheet.repository.CharacterRepository;
import com.steelmight.charactersheet.service.CharacterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds a demo party (room "demo") on dev startup so the GM roster has data before players
 * create their own characters. Each character is guarded by id, so edits in the persistent
 * dev DB are never overwritten.
 */
@Component
@Profile("dev")
public class DevDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);
    private static final String ROOM = "demo";

    private final CharacterRepository repo;
    private final StatDerivationEngine statEngine;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public DevDataSeeder(CharacterRepository repo, StatDerivationEngine statEngine,
                         org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.repo = repo;
        this.statEngine = statEngine;
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        migrateLegacyMoneyColumns();
        seed("aria@steelandmight.test", "Aria Stormvoice", "human", "musician", "bard", 5,
                new Stats(10, 14, 12, 13, 8, 11, 16), 0.8, List.of("persuasion", "performance", "deception"),
                List.of("dissonating-song", "hasting-trill", "sad-story", "increasing-pitch", "stunning-echoes"));
        seed("thorgrim@steelandmight.test", "Thorgrim Ironhide", "dwarf", "warrior", "barbarian", 5,
                new Stats(17, 12, 16, 8, 10, 10, 9), 1.0, List.of("athletics", "intimidation", "survival"),
                List.of());
        seed("saulus@steelandmight.test", "Brother Saulus", "human", "disciple", "cleric", 5,
                new Stats(10, 10, 13, 11, 15, 16, 12), 1.0, List.of("medicine", "religion", "insight"),
                List.of("sacred-bolt", "healing-word", "healing-touch-withering-touch"));
    }

    /** One-time dev-DB migration: money went gold → copper (M5-A) → back to a single
     *  generic gold (Game Owner 2026-07-06). DBs from the brief copper era carry the
     *  value in COPPER — move it into GOLD and drop the column. Also lifts the NOT
     *  NULL constraint pre-M5 DBs had on GOLD. Fresh DBs: no-op. */
    private void migrateLegacyMoneyColumns() {
        try {
            jdbc.execute("ALTER TABLE characters ALTER COLUMN gold SET NULL");
        } catch (Exception e) {
            log.debug("gold column NOT NULL lift skipped: {}", e.getMessage());
        }
        try {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_name = 'CHARACTERS' AND column_name = 'COPPER'",
                    Integer.class);
            if (count == null || count == 0) return;
            int migrated = jdbc.update(
                    "UPDATE characters SET gold = copper WHERE gold IS NULL AND copper IS NOT NULL");
            jdbc.execute("ALTER TABLE characters DROP COLUMN IF EXISTS copper");
            log.info("Moved copper-era money into gold ({} rows) and dropped the copper column", migrated);
        } catch (Exception e) {
            log.warn("Copper-column migration skipped: {}", e.getMessage());
        }
    }

    private void seed(String email, String name, String race, String pathId, String classId,
                      int level, Stats stats, double hpFraction, List<String> proficiencies,
                      List<String> knownSpells) {
        String id = CharacterService.characterId(ROOM, email);
        if (repo.existsById(id)) return;

        var c = new GameCharacter(id);
        c.setRoomName(ROOM);
        c.setEmail(email);
        c.setName(name);
        c.setRaceId(race);
        c.setPathId(pathId);
        c.setClassId(classId);
        c.setLevel(level);
        c.setSpeed(30);
        c.setStats(stats);
        c.setAp(new ActionPoints(6, 6, 10));
        c.setGold(500); // shop money (generic gold — a L1 one-handed weapon costs 10)
        c.getProficiencies().addAll(proficiencies);
        c.getKnownSpells().addAll(knownSpells);

        int maxHp = statEngine.computeMaxHP(c);
        int maxMana = statEngine.computeMaxMana(c);
        c.setHp(new HitPoints((int) Math.round(maxHp * hpFraction), maxHp, 0));
        c.setMana(new ManaPool(maxMana, maxMana));

        repo.save(c);
        log.info("Seeded '{}' ({}, {}/{})", id, name, pathId, classId);
    }
}
