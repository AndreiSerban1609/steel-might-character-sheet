package com.steelmight.charactersheet.engine.rules.damage;

import com.steelmight.charactersheet.engine.ActiveMechanics;
import com.steelmight.charactersheet.engine.DamageDirection;
import com.steelmight.charactersheet.engine.DamageEvent;
import com.steelmight.charactersheet.engine.MechanicType;
import com.steelmight.charactersheet.engine.ResolutionResult;
import com.steelmight.charactersheet.engine.ResolutionRule;
import com.steelmight.charactersheet.engine.StatDerivationEngine;
import com.steelmight.charactersheet.gamedata.GameDataProvider;
import com.steelmight.charactersheet.model.GameCharacter;
import org.springframework.stereotype.Component;

/**
 * Flat taken-damage modifiers (M2-A rule 3) — i.e. wounded: +stacks per instance,
 * only on directAttack-tagged events. Runs BEFORE armor (Q07: the Wounded bonus is
 * added to the attack's damage, which armor then reduces).
 */
@Component
public class FlatTakenModifierRule implements ResolutionRule<DamageEvent> {

    private final GameDataProvider gameData;
    private final StatDerivationEngine statEngine;

    public FlatTakenModifierRule(GameDataProvider gameData, StatDerivationEngine statEngine) {
        this.gameData = gameData;
        this.statEngine = statEngine;
    }

    @Override
    public void apply(DamageEvent event, GameCharacter character, ResolutionResult result) {
        int threshold = statEngine.computeStackThreshold(character);

        for (var hit : ActiveMechanics.collect(character, gameData, threshold, MechanicType.DAMAGE_MODIFIER)) {
            var mech = hit.mechanic();
            if (mech.direction() != DamageDirection.TAKEN || !mech.flat()) continue;
            // wounded's mechanic is scoped to direct attacks (source: "directAttacks").
            if ("directAttacks".equals(mech.source()) && !event.hasTag("directAttack")) continue;

            int perStack = mech.value() != null ? mech.value() : 1;
            int bonus = mech.valueFromStacks() ? perStack * hit.effect().getStacks() : perStack;
            if (bonus == 0) continue;

            int before = event.getValue();
            event.setValue(before + bonus);
            result.addStep("flat-taken-modifier",
                    hit.def().id() + " +" + bonus + " damage taken", before, event.getValue());
        }
    }
}
