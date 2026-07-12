package com.steelmight.charactersheet;

import com.steelmight.charactersheet.model.*;

/**
 * Shared test-character builder (M0-A test plan). Level 5 → stack threshold ceil(5/2) = 3.
 * Every engine/service test package builds characters through this.
 */
public final class TestCharacterFactory {

    private TestCharacterFactory() {}

    /** Level-5 bard with empty effects and standard stats. */
    public static GameCharacter level5Bard() {
        return character(5, "musician", "bard");
    }

    public static GameCharacter character(int level, String pathId, String classId) {
        var c = new GameCharacter("test-player");
        c.setName("Test");
        c.setLevel(level);
        c.setPathId(pathId);
        c.setClassId(classId);
        c.setSpeed(30);
        // WILL 14 → modifier +2 → hitting 0 HP downs (2-round window) instead of
        // killing outright (M2-D); death-branch tests set WILL explicitly.
        c.setStats(new Stats(10, 14, 12, 10, 10, 14, 16));
        c.setHp(new HitPoints(100, 100, 0));
        c.setMana(new ManaPool(200, 200));
        c.setAp(new ActionPoints(6, 6, 10));
        return c;
    }
}
