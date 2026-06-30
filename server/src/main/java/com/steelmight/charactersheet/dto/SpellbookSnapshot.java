package com.steelmight.charactersheet.dto;

import com.steelmight.charactersheet.model.AbilityScore;
import java.util.List;

public record SpellbookSnapshot(
        List<String> knownSpells,
        List<String> preparedSpells,
        int currentMana,
        int maxMana,
        boolean concentrating,
        AbilityScore spellcastingAttribute,
        int spellSaveDC,
        int spellAttackBonus
) {}
