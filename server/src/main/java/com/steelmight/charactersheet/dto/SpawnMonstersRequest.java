package com.steelmight.charactersheet.dto;

import jakarta.validation.constraints.NotNull;

/** Stamp {@code count} (default 1) instances of a room template into the fight. */
public record SpawnMonstersRequest(
        @NotNull Long templateId,
        Integer count
) {}
