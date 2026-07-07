package com.steelmight.charactersheet.dto;

import com.steelmight.charactersheet.model.AbilityScore;

import java.util.List;
import java.util.Map;

/**
 * A player creating their own character (M6-A). The server derives the id from
 * room + email. {@code level} defaults to 1.
 *
 * @param stats              exactly a permutation of character-creation.json's statArray
 * @param bonusAllocation    +5 across any stats, max 2 per stat (N17: unconstrained by race)
 * @param specializationId   slug of a specialization name valid for the class (Q35);
 *                           its startingTalent is auto-granted
 * @param skillProficiencies exactly 3 distinct skill ids
 * @param knownSpells        casters: exactly the level-1 allotment (1 spell); others: empty
 */
public record CreateCharacterRequest(
        String roomName,
        String email,
        String name,
        String raceId,
        String pathId,
        String classId,
        String specializationId,
        Integer level,
        Map<AbilityScore, Integer> stats,
        Map<AbilityScore, Integer> bonusAllocation,
        List<String> skillProficiencies,
        List<String> knownSpells
) {}
