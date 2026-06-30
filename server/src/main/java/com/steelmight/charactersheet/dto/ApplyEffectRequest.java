package com.steelmight.charactersheet.dto;

import jakarta.validation.constraints.NotBlank;

public record ApplyEffectRequest(
        @NotBlank String effectId,
        int stacks,
        Integer value,
        Integer duration,
        String source
) {
    public ApplyEffectRequest {
        if (stacks <= 0) stacks = 1;
    }
}
