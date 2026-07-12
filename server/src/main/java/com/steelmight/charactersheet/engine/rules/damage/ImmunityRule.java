package com.steelmight.charactersheet.engine.rules.damage;

import com.steelmight.charactersheet.engine.ActiveMechanics;
import com.steelmight.charactersheet.engine.DamageEvent;
import com.steelmight.charactersheet.engine.MechanicType;
import com.steelmight.charactersheet.engine.ResolutionResult;
import com.steelmight.charactersheet.engine.ResolutionRule;
import com.steelmight.charactersheet.engine.StatDerivationEngine;
import com.steelmight.charactersheet.gamedata.GameDataProvider;
import com.steelmight.charactersheet.model.GameCharacter;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Damage immunity (M2-A rule 1): an active IMMUNITY mechanic covering "allDamage"
 * whose except-list does not include the event's damage type zeroes the event and
 * stops the pipeline entirely — immunity-stopped damage never "lands" (Q04), unlike
 * absorbed damage. Data: petrified excepts ["true"]; frozen/crystallised except nothing.
 */
@Component
public class ImmunityRule implements ResolutionRule<DamageEvent> {

    private final GameDataProvider gameData;
    private final StatDerivationEngine statEngine;

    public ImmunityRule(GameDataProvider gameData, StatDerivationEngine statEngine) {
        this.gameData = gameData;
        this.statEngine = statEngine;
    }

    @Override
    public void apply(DamageEvent event, GameCharacter character, ResolutionResult result) {
        int threshold = statEngine.computeStackThreshold(character);
        String typeKey = event.getDamageType().name().toLowerCase(Locale.ROOT);

        for (var hit : ActiveMechanics.collect(character, gameData, threshold, MechanicType.IMMUNITY)) {
            if (hit.mechanic().consumeStacks()) continue; // warded-style — application side only
            if (!hit.mechanic().immunityTargets().contains("allDamage")) continue;
            var except = hit.mechanic().except();
            if (except != null && except.contains(typeKey)) continue;

            int before = event.getValue();
            event.setValue(0);
            event.halt(); // immunity-stopped damage never lands — no triggers (Q04)
            result.addStep("immunity",
                    hit.def().id() + " grants immunity to " + typeKey + " damage", before, 0);
            return;
        }
    }
}
