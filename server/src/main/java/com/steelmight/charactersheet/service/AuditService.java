package com.steelmight.charactersheet.service;

import com.steelmight.charactersheet.dto.AuditView;
import com.steelmight.charactersheet.model.AuditEntry;
import com.steelmight.charactersheet.model.GameCharacter;
import com.steelmight.charactersheet.repository.AuditEntryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Room activity log. Every state-changing character action records one line —
 * the trusted-table counterpart to server-side validation: adjudications (a player
 * dismissing an effect the table resolved) are allowed but visible.
 */
@Service
@Transactional
public class AuditService {

    /** Per-room retention: prune down to KEEP once a room crosses CAP entries. */
    private static final int CAP = 400;
    private static final int KEEP = 300;
    private static final int MAX_SUMMARY = 300;

    private final AuditEntryRepository repo;

    /** Pruning is amortized — a COUNT on every single action is wasted work. */
    private final AtomicLong logCount = new AtomicLong();
    private static final int PRUNE_EVERY = 50;

    public AuditService(AuditEntryRepository repo) {
        this.repo = repo;
    }

    /** No-op for characters without a room (legacy dev rows) — nothing could query them. */
    public void log(GameCharacter c, String action, String summary) {
        if (c.getRoomName() == null || c.getRoomName().isBlank()) return;
        String text = summary != null && summary.length() > MAX_SUMMARY
                ? summary.substring(0, MAX_SUMMARY - 1) + "…" : summary;
        repo.save(new AuditEntry(c.getRoomName(), c.getPlayerId(), c.getName(), action, text));
        if (logCount.incrementAndGet() % PRUNE_EVERY == 0) {
            prune(c.getRoomName());
        }
    }

    private void prune(String room) {
        long count = repo.countByRoomName(room);
        if (count > CAP) {
            repo.deleteAll(repo.findByRoomNameOrderByIdAsc(room, PageRequest.of(0, (int) (count - KEEP))));
        }
    }

    /**
     * Actions a player may see in their own combat log (demo feedback #22, ruled
     * 2026-08-13: combat only, self only, always on). Deliberately an allowlist, not a
     * denylist — a new action type defaults to private rather than leaking gold, shopping
     * or bio edits into a log that's meant to be a fight recap.
     */
    private static final Set<String> COMBAT_ACTIONS = Set.of(
            "damage", "heal", "weapon-attack", "use-ability", "cast", "use-consumable",
            "apply-effect", "remove-effect", "revive", "rest",
            "spend-resource", "gain-resource", "skill-check");

    /** One character's own combat history, newest first. */
    @Transactional(readOnly = true)
    public List<AuditView> combatLogFor(String playerId, int limit) {
        int capped = Math.max(1, Math.min(limit, 200));
        return repo.findByPlayerIdAndActionInOrderByIdDesc(playerId, COMBAT_ACTIONS,
                        PageRequest.of(0, capped)).stream()
                .map(e -> new AuditView(e.getCreatedAt(), e.getPlayerId(), e.getCharacterName(),
                        e.getAction(), e.getSummary()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AuditView> recent(String room, int limit) {
        int capped = Math.max(1, Math.min(limit, 200));
        return repo.findByRoomNameOrderByIdDesc(room, PageRequest.of(0, capped)).stream()
                .map(e -> new AuditView(e.getCreatedAt(), e.getPlayerId(), e.getCharacterName(),
                        e.getAction(), e.getSummary()))
                .toList();
    }
}
