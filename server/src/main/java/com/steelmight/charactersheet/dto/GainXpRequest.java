package com.steelmight.charactersheet.dto;

import jakarta.validation.constraints.Size;

/**
 * XP earned outside combat — missions, discovery, items (Game Owner 2026-08-27: "a way to
 * add XP yourself"). Negative amounts are allowed as GM corrections; the total floors at 0.
 */
public record GainXpRequest(
        int amount,
        @Size(max = 120) String reason
) {}
