package com.steelmight.charactersheet.dto;

import java.util.List;

/** Replaces the character's free-text ability list (pending-rulings escape hatch). */
public record UpdateCustomAbilitiesRequest(List<AbilitiesSnapshot.CustomAbilityView> abilities) {}
