package com.steelmight.charactersheet.dto;

import jakarta.validation.constraints.NotBlank;

/** Use a class ability (Story 1.4): validate → spend costs → resolve/print. */
public record UseAbilityRequest(@NotBlank String abilityId) {}
