package com.steelmight.charactersheet.engine.rules.damage;

import com.steelmight.charactersheet.engine.AbsorbMode;
import com.steelmight.charactersheet.engine.ActiveMechanics;
import com.steelmight.charactersheet.engine.DamageEvent;
import com.steelmight.charactersheet.engine.MechanicType;
import com.steelmight.charactersheet.engine.ResolutionResult;
import com.steelmight.charactersheet.engine.ResolutionRule;
import com.steelmight.charactersheet.engine.StatDerivationEngine;
import com.steelmight.charactersheet.engine.TriggerAction;
import com.steelmight.charactersheet.engine.TriggerEvent;
import com.steelmight.charactersheet.gamedata.GameDataProvider;
import com.steelmight.charactersheet.model.Combatant;
import org.springframework.stereotype.Component;

/**
 * Subtracts the remaining event value from hp.current, floored at 0 (M0-B R2.2),
 * with the M2-D lethal-protection floors:
 * - death-resist (damageAbsorb preventLethal, condition ownTurn): HP floors at 1
 *   when the damage lands during the character's own turn.
 * - perseverance (triggerOnEvent reachZeroHp → setHpTo1, 1 charge): HP set to 1,
 *   the effect is consumed.
 */
@Component
public class HpReductionRule implements ResolutionRule<DamageEvent> {

    private final GameDataProvider gameData;
    private final StatDerivationEngine statEngine;

    public HpReductionRule(GameDataProvider gameData, StatDerivationEngine statEngine) {
        this.gameData = gameData;
        this.statEngine = statEngine;
    }

    @Override
    public void apply(DamageEvent event, Combatant character, ResolutionResult result) {
        if (event.getValue() <= 0) return;

        int before = character.getHp().getCurrent();
        int after = Math.max(0, before - event.getValue());
        if (after == before) return; // already at 0 — nothing to reduce

        if (after == 0) {
            after = tryLethalProtections(event, character, result);
        }

        character.getHp().setCurrent(after);
        event.setHpReduced(true);
        result.addStep("hp-reduction", "Took " + event.getValue() + " "
                + event.getDamageType().name().toLowerCase() + " damage", before, after);
    }

    /** @return 1 when a protection floors the hit, 0 otherwise. */
    private int tryLethalProtections(DamageEvent event, Combatant character, ResolutionResult result) {
        int threshold = statEngine.computeStackThreshold(character);

        // death-resist — not consumed; only during the character's own turn (per its condition).
        for (var hit : ActiveMechanics.collect(character, gameData, threshold, MechanicType.DAMAGE_ABSORB)) {
            if (hit.mechanic().mode() != AbsorbMode.PREVENT_LETHAL) continue;
            boolean ownTurnOnly = "ownTurn".equals(hit.mechanic().condition());
            if (ownTurnOnly && !event.isDuringOwnTurn()) continue;

            result.addStep("death-resist", hit.def().id() + " prevents lethal damage — HP floors at 1", 0, 1);
            return 1;
        }

        // perseverance — one charge, consumed on use.
        for (var hit : ActiveMechanics.collect(character, gameData, threshold, MechanicType.TRIGGER_ON_EVENT)) {
            if (hit.mechanic().event() != TriggerEvent.REACH_ZERO_HP) continue;
            if (hit.mechanic().triggerAction() != TriggerAction.SET_HP_TO_1) continue;

            character.removeEffect(hit.effect());
            result.addStep("perseverance",
                    hit.def().id() + " holds HP at 1 (charge consumed)", 0, 1);
            result.addTriggeredEffect("consumed:" + hit.def().id());
            return 1;
        }

        return 0;
    }
}
