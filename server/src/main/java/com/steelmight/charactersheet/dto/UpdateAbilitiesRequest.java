package com.steelmight.charactersheet.dto;

import java.util.List;

/** Replace the character's choice-group ability picks (free-form picker, Story 1.3). */
public record UpdateAbilitiesRequest(List<String> abilityIds) {}
