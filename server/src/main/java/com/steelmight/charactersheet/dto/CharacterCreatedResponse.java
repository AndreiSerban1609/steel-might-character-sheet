package com.steelmight.charactersheet.dto;

/** Returned by create / find so the client knows the derived id and the initial snapshot. */
public record CharacterCreatedResponse(String playerId, CombatSnapshot snapshot) {}
