package com.steelmight.charactersheet.engine.rules.heal;

import com.steelmight.charactersheet.engine.ActiveMechanics;
import com.steelmight.charactersheet.engine.DamageCategory;
import com.steelmight.charactersheet.engine.DamageEvent;
import com.steelmight.charactersheet.engine.DamageResolutionPipeline;
import com.steelmight.charactersheet.engine.HealEvent;
import com.steelmight.charactersheet.engine.MechanicType;
import com.steelmight.charactersheet.engine.ResolutionResult;
import com.steelmight.charactersheet.engine.ResolutionRule;
import com.steelmight.charactersheet.engine.StatDerivationEngine;
import com.steelmight.charactersheet.gamedata.GameDataProvider;
import com.steelmight.charactersheet.model.DamageType;
import com.steelmight.charactersheet.model.Combatant;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Healing conversion (M2-B rule 3) — decaying: the (possibly maimed-halved) heal
 * becomes TRUE damage instead (Q10: ignores all armor AND resistances), dispatched
 * through the damage pipeline; its steps merge in prefixed "decaying:".
 */
@Component
public class DecayingRule implements ResolutionRule<HealEvent> {

    private final GameDataProvider gameData;
    private final StatDerivationEngine statEngine;
    private final DamageResolutionPipeline damagePipeline;

    public DecayingRule(GameDataProvider gameData, StatDerivationEngine statEngine,
                        DamageResolutionPipeline damagePipeline) {
        this.gameData = gameData;
        this.statEngine = statEngine;
        this.damagePipeline = damagePipeline;
    }

    @Override
    public void apply(HealEvent event, Combatant character, ResolutionResult result) {
        if (event.getValue() <= 0) return;
        int threshold = statEngine.computeStackThreshold(character);

        for (var hit : ActiveMechanics.collect(character, gameData, threshold, MechanicType.HEALING_MODIFIER)) {
            if (!hit.mechanic().convertToDamage()) continue;

            int converted = event.getValue();
            event.setValue(0);
            result.addStep("decaying",
                    hit.def().id() + " converts " + converted + " healing into true damage", converted, 0);

            var damage = new DamageEvent(converted, DamageType.TRUE, DamageCategory.TRUE,
                    List.of("decayingConversion", "ignoresArmor"), true);
            var damageResult = damagePipeline.resolve(damage, character);
            damageResult.getSteps().forEach(s ->
                    result.addStep("decaying:" + s.rule(), s.note(), s.valueBefore(), s.valueAfter()));
            damageResult.getEffectsTriggered().forEach(result::addTriggeredEffect);
            return;
        }
    }
}
