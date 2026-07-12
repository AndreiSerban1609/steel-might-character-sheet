package com.steelmight.charactersheet.engine.rules.damage;

import com.steelmight.charactersheet.engine.ActiveMechanics;
import com.steelmight.charactersheet.engine.AbsorbMode;
import com.steelmight.charactersheet.engine.DamageEvent;
import com.steelmight.charactersheet.engine.MechanicType;
import com.steelmight.charactersheet.engine.ResolutionResult;
import com.steelmight.charactersheet.engine.ResolutionRule;
import com.steelmight.charactersheet.engine.StatDerivationEngine;
import com.steelmight.charactersheet.gamedata.GameDataProvider;
import com.steelmight.charactersheet.model.GameCharacter;
import org.springframework.stereotype.Component;

/**
 * Damage cap (M2-A rule 4): the damage-cap effect caps the event value at the
 * effect's value. Step only when it actually caps.
 */
@Component
public class DamageCapRule implements ResolutionRule<DamageEvent> {

    private final GameDataProvider gameData;
    private final StatDerivationEngine statEngine;

    public DamageCapRule(GameDataProvider gameData, StatDerivationEngine statEngine) {
        this.gameData = gameData;
        this.statEngine = statEngine;
    }

    @Override
    public void apply(DamageEvent event, GameCharacter character, ResolutionResult result) {
        int threshold = statEngine.computeStackThreshold(character);

        for (var hit : ActiveMechanics.collect(character, gameData, threshold, MechanicType.DAMAGE_ABSORB)) {
            if (hit.mechanic().mode() != AbsorbMode.PERCENT_CAP) continue;
            Integer cap = hit.effect().getValue();
            if (cap == null || event.getValue() <= cap) continue;

            int before = event.getValue();
            event.setValue(cap);
            result.addStep("damage-cap", hit.def().id() + " caps damage at " + cap, before, cap);
        }
    }
}
