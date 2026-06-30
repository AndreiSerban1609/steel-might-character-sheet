package com.steelmight.charactersheet.dto;

import com.steelmight.charactersheet.model.AbilityScore;

import java.util.Map;

/**
 * A player creating their own character. The server derives the id from room + email.
 * {@code stats} is optional (defaults to the standard array); {@code level} defaults to 1.
 */
public record CreateCharacterRequest(
        String roomName,
        String email,
        String name,
        String pathId,
        String classId,
        Integer level,
        Map<AbilityScore, Integer> stats
) {}
