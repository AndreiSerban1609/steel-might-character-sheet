package com.steelmight.charactersheet.engine.rules.damage;

import com.steelmight.charactersheet.engine.ActiveMechanics;
import com.steelmight.charactersheet.engine.DamageEvent;
import com.steelmight.charactersheet.engine.EffectApplicationEngine;
import com.steelmight.charactersheet.engine.MechanicType;
import com.steelmight.charactersheet.engine.ResolutionResult;
import com.steelmight.charactersheet.engine.ResolutionRule;
import com.steelmight.charactersheet.engine.StatDerivationEngine;
import com.steelmight.charactersheet.engine.TriggerEvent;
import com.steelmight.charactersheet.gamedata.GameDataProvider;
import com.steelmight.charactersheet.model.Combatant;
import org.springframework.stereotype.Component;

import java.util.HashSet;

/**
 * Post-damage triggers (M2-B). Runs after the damage rules for any damage that
 * "landed" — including absorbed-to-0 damage (Q04: it wakes sleepers) but never
 * immunity-halted damage (the pipeline skips this stage entirely then).
 *
 * - removeOnEvent takeDamage (sleeping/sleep): removed via the effect engine.
 * - removeOnEvent harmedBySource (charmed): removed only when the event's
 *   sourceId matches the effect's source.
 * - Attacker-side triggers (retaliation, stun-on-retaliation, vampiric-aura)
 *   need a second character — out of scope until an encounter model exists.
 */
@Component
public class TriggeredEffectsRule implements ResolutionRule<DamageEvent> {

    private final GameDataProvider gameData;
    private final StatDerivationEngine statEngine;
    private final EffectApplicationEngine effectEngine;

    public TriggeredEffectsRule(GameDataProvider gameData, StatDerivationEngine statEngine,
                                EffectApplicationEngine effectEngine) {
        this.gameData = gameData;
        this.statEngine = statEngine;
        this.effectEngine = effectEngine;
    }

    @Override
    public void apply(DamageEvent event, Combatant character, ResolutionResult result) {
        int threshold = statEngine.computeStackThreshold(character);
        var toRemove = new HashSet<String>();

        for (var hit : ActiveMechanics.collect(character, gameData, threshold, MechanicType.REMOVE_ON_EVENT)) {
            var trigger = hit.mechanic().event();
            if (trigger == TriggerEvent.TAKE_DAMAGE) {
                toRemove.add(hit.def().id());
            } else if (trigger == TriggerEvent.HARMED_BY_SOURCE
                    && event.getSourceId() != null
                    && event.getSourceId().equals(hit.effect().getSource())) {
                toRemove.add(hit.def().id());
            }
        }

        for (var effectId : toRemove) {
            var removal = effectEngine.remove(character, effectId);
            removal.getSteps().forEach(s ->
                    result.addStep("trigger:" + s.rule(), s.note() + " (damage taken)",
                            s.valueBefore(), s.valueAfter()));
            result.addTriggeredEffect("removed:" + effectId);
        }
    }
}
