package com.steelmight.charactersheet.service;

import com.steelmight.charactersheet.dto.AuditView;
import com.steelmight.charactersheet.model.AuditEntry;
import com.steelmight.charactersheet.model.GameCharacter;
import com.steelmight.charactersheet.repository.AuditEntryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    public AuditService(AuditEntryRepository repo) {
        this.repo = repo;
    }

    /** No-op for characters without a room (legacy dev rows) — nothing could query them. */
    public void log(GameCharacter c, String action, String summary) {
        if (c.getRoomName() == null || c.getRoomName().isBlank()) return;
        String text = summary != null && summary.length() > MAX_SUMMARY
                ? summary.substring(0, MAX_SUMMARY - 1) + "…" : summary;
        repo.save(new AuditEntry(c.getRoomName(), c.getPlayerId(), c.getName(), action, text));
        prune(c.getRoomName());
    }

    private void prune(String room) {
        long count = repo.countByRoomName(room);
        if (count > CAP) {
            repo.deleteAll(repo.findByRoomNameOrderByIdAsc(room, PageRequest.of(0, (int) (count - KEEP))));
        }
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
