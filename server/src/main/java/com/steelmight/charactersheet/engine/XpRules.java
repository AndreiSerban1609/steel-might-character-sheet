package com.steelmight.charactersheet.engine;

import com.steelmight.charactersheet.gamedata.GameDataProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Experience (Game Owner ruling 2026-08-27, src/data/xp-table.json):
 * <ul>
 *   <li>a slain creature is worth the {@code monsterXp} of its might (level when might is
 *       unset), split evenly between every player in the combat;</li>
 *   <li>{@code xpToNext} is read as a CUMULATIVE total — a level-L character qualifies for
 *       level L+1 once their lifetime XP reaches the level-L row (D&D-shaped; ASSUMPTION
 *       pending the Game Owner — the table's column reads "XP to level up");</li>
 *   <li>XP never levels anyone automatically: it flags "level available" and the existing
 *       level-up flow (with its stat/ability picks) does the rest.</li>
 * </ul>
 */
@Component
public class XpRules {
    public static final int MAX_LEVEL = 20;

    /** One row of xp-table.json; {@code xpToNext} is null on the last row. */
    public record Row(int level, int monsterXp, Integer xpToNext) {}

    private final List<Row> rows = new ArrayList<>();

    public XpRules(GameDataProvider gameData) {
        var table = gameData.getXpTable();
        if (table != null && table.isArray()) {
            for (var n : table) {
                rows.add(new Row(n.path("level").asInt(), n.path("monsterXp").asInt(),
                        n.hasNonNull("xpToNext") ? n.get("xpToNext").asInt() : null));
            }
        }
    }

    public boolean loaded() { return !rows.isEmpty(); }

    private Row row(int level) {
        int clamped = Math.max(1, Math.min(MAX_LEVEL, level));
        for (var r : rows) if (r.level() == clamped) return r;
        return null;
    }

    /** XP a slain creature of this might (or level) is worth to the whole party, before the split. */
    public int monsterXp(int mightOrLevel) {
        var r = row(mightOrLevel);
        return r != null ? r.monsterXp() : 0;
    }

    /** Lifetime XP at which a character of {@code level} qualifies for the next level; null at the cap. */
    public Integer xpToNext(int level) {
        var r = row(level);
        return r != null ? r.xpToNext() : null;
    }

    /** The highest level {@code totalXp} qualifies for (never below 1, never above the cap). */
    public int levelFor(int totalXp) {
        int level = 1;
        while (level < MAX_LEVEL) {
            Integer next = xpToNext(level);
            if (next == null || totalXp < next) break;
            level++;
        }
        return level;
    }

    /** Even split, floored — the remainder is simply not awarded (noted in the audit line). */
    public static int share(int pool, int recipients) {
        return recipients <= 0 ? 0 : pool / recipients;
    }
}
