package com.steelmight.charactersheet.dto;

import jakarta.validation.constraints.NotBlank;

/** DM override: set a participant's initiative; the order re-sorts around it. */
public record SetInitiativeRequest(@NotBlank String playerId, int initiative) {}
