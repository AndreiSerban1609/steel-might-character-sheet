package com.steelmight.charactersheet.dto;

import com.steelmight.charactersheet.model.AbilityScore;

import java.util.Map;

/** New base ability scores, keyed by ability (all seven required). */
public record UpdateStatsRequest(Map<AbilityScore, Integer> stats) {}
