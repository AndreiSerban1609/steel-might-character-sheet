package com.steelmight.charactersheet.dto;

import jakarta.validation.constraints.Min;

public record HealRequest(
        @Min(1) int value
) {}
