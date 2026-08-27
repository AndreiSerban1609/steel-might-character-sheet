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
 * Healing block (M2-B rule 2) — cursed: healingModifier multiplier 0. Zeroes the
 * heal, which stops the pipeline — Cursed wins over Decaying because it runs
 * first (documented design, ARCHITECTURE.md).
 */
@Component
public class CursedRule implements ResolutionRule<HealEvent> {

    private final GameDataProvider gameData;
    private final StatDerivationEngine statEngine;

    public CursedRule(GameDataProvider gameData, StatDerivationEngine statEngine) {
        this.gameData = gameData;
        this.statEngine = statEngine;
    }

    @Override
    public void apply(HealEvent event, Combatant character, ResolutionResult result) {
        int threshold = statEngine.computeStackThreshold(character);
        for (var hit : ActiveMechanics.collect(character, gameData, threshold, MechanicType.HEALING_MODIFIER)) {
            var m = hit.mechanic().multiplier();
            if (m == null || m != 0.0) continue;

            int before = event.getValue();
            event.setValue(0);
            result.addStep("healing-blocked",
                    hit.def().id() + " blocks all healing", before, 0);
            return;
        }
    }
}
