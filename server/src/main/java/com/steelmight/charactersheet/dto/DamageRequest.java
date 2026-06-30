package com.steelmight.charactersheet.dto;

import com.steelmight.charactersheet.model.DamageType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record DamageRequest(
        @Min(1) int value,
        @NotNull DamageType damageType
) {}
