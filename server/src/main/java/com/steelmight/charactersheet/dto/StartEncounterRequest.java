package com.steelmight.charactersheet.dto;

import java.util.List;

/** Participants for a new encounter; null/empty → every character in the room. */
public record StartEncounterRequest(List<String> playerIds) {}
