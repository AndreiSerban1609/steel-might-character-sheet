package com.steelmight.charactersheet.dto;

import com.steelmight.charactersheet.model.AbilityScore;

import java.util.List;
import java.util.Map;

/**
 * POST /actions/level-up (M6-B/C). All-or-nothing — everything validates before
 * anything mutates.
 *
 * @param choices statIncreases required at levels 6/12/18 (sum 5, max 2/stat);
 *                newSpells per the caster progression arrays; talentId at
 *                3/7/11/15/19 (and 17 when neither spec talent is owned yet);
 *                featId ("active" | "passive" | "modification") at 5/9/13
 */
public record LevelUpRequest(Choices choices) {

    public record Choices(
            Map<AbilityScore, Integer> statIncreases,
            List<String> newSpells,
            String talentId,
            String featId
    ) {}
}
