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

    public DevDataSeeder(CharacterRepository repo, StatDerivationEngine statEngine) {
        this.repo = repo;
        this.statEngine = statEngine;
    }

    @Override
    public void run(String... args) {
        seed("aria@steelandmight.test", "Aria Stormvoice", "human", "musician", "bard", 5,
                new Stats(10, 14, 12, 13, 8, 11, 16), 0.8, List.of("persuasion", "performance", "deception"));
        seed("thorgrim@steelandmight.test", "Thorgrim Ironhide", "dwarf", "warrior", "barbarian", 5,
                new Stats(17, 12, 16, 8, 10, 10, 9), 1.0, List.of("athletics", "intimidation", "survival"));
        seed("saulus@steelandmight.test", "Brother Saulus", "human", "disciple", "cleric", 5,
                new Stats(10, 10, 13, 11, 15, 16, 12), 1.0, List.of("medicine", "religion", "insight"));
    }

    private void seed(String email, String name, String race, String pathId, String classId,
                      int level, Stats stats, double hpFraction, List<String> proficiencies) {
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
        c.getProficiencies().addAll(proficiencies);

        int maxHp = statEngine.computeMaxHP(c);
        int maxMana = statEngine.computeMaxMana(c);
        c.setHp(new HitPoints((int) Math.round(maxHp * hpFraction), maxHp, 0));
        c.setMana(new ManaPool(maxMana, maxMana));

        repo.save(c);
        log.info("Seeded '{}' ({}, {}/{})", id, name, pathId, classId);
    }
}
