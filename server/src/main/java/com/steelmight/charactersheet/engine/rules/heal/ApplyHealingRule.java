package com.steelmight.charactersheet.engine.rules.heal;

import com.steelmight.charactersheet.engine.HealEvent;
import com.steelmight.charactersheet.engine.ResolutionResult;
import com.steelmight.charactersheet.engine.ResolutionRule;
import com.steelmight.charactersheet.engine.StatDerivationEngine;
import com.steelmight.charactersheet.model.GameCharacter;
import org.springframework.stereotype.Component;

/**
 * Adds the heal value to hp.current, capped at the derived max HP (M0-B R3).
 * Healing never restores temp HP. Maimed/Cursed/Decaying modifiers are M2-B
 * rules that run before this one.
 */
@Component
public class ApplyHealingRule implements ResolutionRule<HealEvent> {

    private final StatDerivationEngine statEngine;

    public ApplyHealingRule(StatDerivationEngine statEngine) {
        this.statEngine = statEngine;
    }

    @Override
    public void apply(HealEvent event, GameCharacter character, ResolutionResult result) {
        int maxHp = statEngine.computeMaxHP(character);
        int before = character.getHp().getCurrent();
        int after = Math.min(maxHp, before + event.getValue());
        int applied = after - before;
        int discarded = event.getValue() - applied;

        character.getHp().setCurrent(after);
        String note = "Healed " + applied
                + (discarded > 0 ? " (" + discarded + " discarded — at max HP)" : "");
        result.addStep("apply-healing", note, before, after);
    }
}
