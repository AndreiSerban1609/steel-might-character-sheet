package com.steelmight.charactersheet.dto;

import java.util.Map;

/**
 * Full replacement of the character's pinned derived stats (demo feedback #11/#12).
 * Keys are {@link com.steelmight.charactersheet.model.OverridableStat} keys; a key
 * left out — or mapped to null — returns that stat to normal derivation.
 */
public record StatOverridesRequest(Map<String, Integer> overrides) {}
