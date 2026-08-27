package com.steelmight.charactersheet.engine.rules.heal;

import com.steelmight.charactersheet.engine.HealEvent;
import com.steelmight.charactersheet.engine.ResolutionResult;
import com.steelmight.charactersheet.engine.ResolutionRule;
import com.steelmight.charactersheet.model.Combatant;
import com.steelmight.charactersheet.model.LifeStatus;
import org.springframework.stereotype.Component;

/**
 * Healing a downed (or dead) character does NOTHING (Q11, M2-D) — revival is
 * only the Medicine-check revive action. Zeroes the heal, stopping the pipeline.
 */
@Component
public class DownedHealBlockRule implements ResolutionRule<HealEvent> {

    @Override
    public void apply(HealEvent event, Combatant character, ResolutionResult result) {
        if (character.getLifeStatus() == LifeStatus.ALIVE) return;

        int before = event.getValue();
        event.setValue(0);
        result.addStep("downed-no-heal",
                "Healing a " + character.getLifeStatus().name().toLowerCase()
                        + " character does nothing — revival requires a Medicine check (Q11)",
                before, 0);
    }
}
