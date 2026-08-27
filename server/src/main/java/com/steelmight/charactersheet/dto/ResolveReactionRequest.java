package com.steelmight.charactersheet.dto;

import jakarta.validation.constraints.Min;

/**
 * Close out a prepared reaction: {@code index} into the snapshot's preparedReactions
 * list; {@code used} = it triggered (vs. cancelled). Either way the AP stays spent —
 * the prep was the cost (2026-08-27 ruling); the GM can refund via the vitals edit.
 */
public record ResolveReactionRequest(
        @Min(0) int index,
        boolean used
) {}
