package com.steelmight.charactersheet.service;

import com.steelmight.charactersheet.dto.XpAward;
import com.steelmight.charactersheet.engine.XpRules;
import com.steelmight.charactersheet.model.CombatantType;
import com.steelmight.charactersheet.model.LifeStatus;
import com.steelmight.charactersheet.model.MonsterInstance;
import com.steelmight.charactersheet.model.RoomEncounter;
import com.steelmight.charactersheet.repository.CharacterRepository;
import com.steelmight.charactersheet.repository.RoomEncounterRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

/**
 * Experience bookkeeping (Game Owner ruling 2026-08-27).
 * <p>
 * Kills are banked into the room's running encounter the moment a monster dies — from
 * whichever path killed it (board damage, a targeted attack, a DoT tick) — via the single
 * {@link #creditKill} hook every monster persistence site calls; {@code xpCredited} on the
 * instance makes it idempotent. When the GM ends the combat the pool is split evenly among
 * every PLAYER entry of the turn order ({@link #award}). Nothing here levels anyone: the
 * snapshot flags {@code levelAvailable} and the existing level-up flow does the rest.
 */
@Service
public class XpService {
    private final RoomEncounterRepository encounters;
    private final CharacterRepository characters;
    private final XpRules rules;
    private final AuditService audit;

    public XpService(RoomEncounterRepository encounters, CharacterRepository characters,
                     XpRules rules, AuditService audit) {
        this.encounters = encounters;
        this.characters = characters;
        this.rules = rules;
        this.audit = audit;
    }

    /** XP a monster is worth to the party: its might row, or its level when might is unset. */
    public int worthOf(MonsterInstance m) {
        Integer might = m.getBlock() != null ? m.getBlock().getMight() : null;
        return rules.monsterXp(might != null ? might : m.getLevel());
    }

    /**
     * Bank a slain monster into its room's running encounter — once. A kill with no combat
     * running is only audited (the ruling splits XP "between all players participating in
     * the combat"; the GM awards it by hand if it should count).
     */
    public void creditKill(MonsterInstance m) {
        if (m.getLifeStatus() != LifeStatus.DEAD || m.isXpCredited()) return;
        m.setXpCredited(true);
        int worth = worthOf(m);
        var enc = encounters.findById(m.getRoomName()).orElse(null);
        if (enc == null) {
            audit.log(m.getRoomName(), m.getCombatantId(), m.getDisplayName(), "xp",
                    "Slain outside a combat — " + worth + " XP not banked (award it manually if it should count)");
            return;
        }
        enc.addXp(worth);
        encounters.save(enc);
        audit.log(m.getRoomName(), m.getCombatantId(), m.getDisplayName(), "xp",
                "Slain — " + worth + " XP banked (combat pool now " + enc.getXpPool() + ")");
    }

    /** Split the encounter's pool among its player entries (even, floored); an empty pool awards nothing. */
    public XpAward award(RoomEncounter enc) {
        var players = enc.getEntries().stream()
                .filter(e -> e.getCombatantType() == CombatantType.PLAYER)
                .toList();
        int pool = enc.getXpPool();
        if (pool <= 0 || players.isEmpty()) return XpAward.none();

        int share = XpRules.share(pool, players.size());
        var awarded = new ArrayList<XpAward.Recipient>();
        for (var entry : players) {
            var c = characters.findById(entry.getCombatantId()).orElse(null);
            if (c == null) continue; // deleted mid-combat — their share is simply not paid
            c.setXp(c.getXp() + share);
            characters.save(c);
            boolean levelAvailable = rules.levelFor(c.getXp()) > c.getLevel();
            audit.log(c, "xp", "+" + share + " XP — combat: " + pool + " split " + players.size() + " ways"
                    + (levelAvailable ? " — level " + (c.getLevel() + 1) + " available" : "")
                    + " (total " + c.getXp() + ")");
            awarded.add(new XpAward.Recipient(c.getCombatantId(), c.getName(), share, c.getXp(), c.getLevel(),
                    rules.xpToNext(c.getLevel()), levelAvailable));
        }
        return new XpAward(pool, players.size(), share, awarded);
    }
}
