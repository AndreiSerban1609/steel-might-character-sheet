package com.steelmight.charactersheet.dto;

import java.util.List;

/** The full set of skill-check proficiencies (skill ids from skills.json) — replaces the current set. */
public record ProficienciesRequest(List<String> skillIds) {}
