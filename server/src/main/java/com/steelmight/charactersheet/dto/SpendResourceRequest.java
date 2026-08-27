package com.steelmight.charactersheet.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code note} (optional, 2026-08-27): what the points went on — "moved 20 ft" — printed in the log and audit. */
public record SpendResourceRequest(
        @NotBlank String resource,
        @Min(1) int amount,
        @Size(max = 120) String note
) {
    public SpendResourceRequest(String resource, int amount) {
        this(resource, amount, null);
    }
}
