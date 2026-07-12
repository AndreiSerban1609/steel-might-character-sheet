package com.steelmight.charactersheet.dto;

import java.util.List;

/** POST /actions/prepare-spells (M4-E) — replaces the whole prepared list. */
public record PrepareSpellsRequest(
        List<String> spellIds
) {}
