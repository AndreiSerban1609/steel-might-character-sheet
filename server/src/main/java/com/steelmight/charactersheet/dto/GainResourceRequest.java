package com.steelmight.charactersheet.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record GainResourceRequest(
        @NotBlank String resource,
        @Min(1) int amount
) {}
