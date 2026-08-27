package com.steelmight.charactersheet.engine.rules.heal;

import com.steelmight.charactersheet.engine.ActiveMechanics;
import com.steelmight.charactersheet.engine.HealEvent;
import com.steelmight.charactersheet.engine.MechanicType;
import com.steelmight.charactersheet.engine.ResolutionResult;
import com.steelmight.charactersheet.engine.ResolutionRule;
import com.steelmight.charactersheet.engine.StatDerivationEngine;
import com.steelmight.charactersheet.gamedata.GameDataProvider;
import com.steelmight.charactersheet.model.Combatant;
import org.springframework.stereotype.Component;

/**
 * Partial healing reduction (M2-B rule 1) — maimed: healingModifier multiplier 0.5,
 * halved (floored) ONCE per effect regardless of stacks (the multiplier is per-effect).
 */
@Component
public class MaimedRule implements ResolutionRule<HealEvent> {

    private final GameDataProvider gameData;
    private final StatDerivationEngine statEngine;

    public MaimedRule(GameDataProvider gameData, StatDerivationEngine statEngine) {
        this.gameData = gameData;
        this.statEngine = statEngine;
    }

    @Override
    public void apply(HealEvent event, Combatant character, ResolutionResult result) {
        int threshold = statEngine.computeStackThreshold(character);
        for (var hit : ActiveMechanics.collect(character, gameData, threshold, MechanicType.HEALING_MODIFIER)) {
            var m = hit.mechanic().multiplier();
            if (m == null || m <= 0 || m >= 1) continue; // full blocks (0) and conversions handled later

            int before = event.getValue();
            event.setValue((int) Math.floor(before * m));
            result.addStep("healing-modifier",
                    hit.def().id() + " reduces healing ×" + m, before, event.getValue());
        }
    }
}
