package com.steelmight.charactersheet.dto;

import java.time.Instant;

/** One activity-log line, newest first in the room feed. */
public record AuditView(Instant time, String playerId, String characterName,
                        String action, String summary) {}
