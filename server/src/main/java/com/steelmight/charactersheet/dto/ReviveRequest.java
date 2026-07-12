package com.steelmight.charactersheet.dto;

/**
 * DM action for a resolved Medicine check (N11b) or a returned Death fight (M2-D).
 *
 * @param hpRestored       HP after revival; default 1, capped at derived max
 * @param deathStackGained returning from a WON Death fight — increments deathStacks
 * @param criticalFail     the Medicine check critically failed — the downed character
 *                         dies (Death fight pending)
 */
public record ReviveRequest(
        Integer hpRestored,
        boolean deathStackGained,
        boolean criticalFail
) {}
