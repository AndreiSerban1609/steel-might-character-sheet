package com.steelmight.charactersheet.engine.rules.damage;

import com.steelmight.charactersheet.engine.ActiveMechanics;
import com.steelmight.charactersheet.engine.AbsorbMode;
import com.steelmight.charactersheet.engine.DamageCategory;
import com.steelmight.charactersheet.engine.DamageEvent;
import com.steelmight.charactersheet.engine.MechanicType;
import com.steelmight.charactersheet.engine.ResolutionResult;
import com.steelmight.charactersheet.engine.ResolutionRule;
import com.steelmight.charactersheet.engine.StatDerivationEngine;
import com.steelmight.charactersheet.gamedata.GameDataProvider;
import com.steelmight.charactersheet.model.ActiveEffect;
import com.steelmight.charactersheet.model.GameCharacter;
import org.springframework.stereotype.Component;

/**
 * Absorption chain (M2-A rule 5), in order:
 * 1. Block (mode: instances) — one stack negates the entire damage instance.
 * 2. Typed shields (specific before general, Game Owner 2026-08-12): the magic
 *    shield absorbs magical damage only, the physical shield physical damage
 *    only — each is simply bypassed by the other category, and TRUE damage
 *    bypasses both (only temp HP stands in its way).
 * 3. Temp HP — pool absorbing anything; ActiveEffect.value is the source of truth,
 *    hp.temp mirrors it (M0-A invariant).
 * Absorbed-to-0 damage still "landed" (Q04) — M2-B's trigger stage will care.
 */
@Component
public class AbsorptionRule implements ResolutionRule<DamageEvent> {

    private final GameDataProvider gameData;
    private final StatDerivationEngine statEngine;

    public AbsorptionRule(GameDataProvider gameData, StatDerivationEngine statEngine) {
        this.gameData = gameData;
        this.statEngine = statEngine;
    }

    @Override
    public void apply(DamageEvent event, GameCharacter character, ResolutionResult result) {
        if (event.getValue() <= 0) return;
        int threshold = statEngine.computeStackThreshold(character);
        var absorbs = ActiveMechanics.collect(character, gameData, threshold, MechanicType.DAMAGE_ABSORB);

        // 1. Block — negates the whole instance, one stack per instance.
        for (var hit : absorbs) {
            if (hit.mechanic().mode() != AbsorbMode.INSTANCES) continue;
            var block = hit.effect();
            if (block.getStacks() < 1) continue;

            int before = event.getValue();
            event.setValue(0);
            block.setStacks(block.getStacks() - 1);
            result.addStep("block",
                    hit.def().id() + " negates the damage instance ("
                            + block.getStacks() + " left)", before, 0);
            if (block.getStacks() == 0) character.removeEffect(block);
            return;
        }

        // 2. Typed shields — each absorbs only its own damage category.
        if (event.getCategory() == DamageCategory.MAGICAL) {
            for (var hit : absorbs) {
                if (hit.mechanic().mode() != AbsorbMode.MAGIC_SHIELD) continue;
                absorbFromPool(event, character, hit.effect(), "magic-shield", false, result);
                if (event.getValue() <= 0) return;
            }
        }
        if (event.getCategory() == DamageCategory.PHYSICAL) {
            for (var hit : absorbs) {
                if (hit.mechanic().mode() != AbsorbMode.PHYSICAL_SHIELD) continue;
                absorbFromPool(event, character, hit.effect(), "physical-shield", false, result);
                if (event.getValue() <= 0) return;
            }
        }

        // 3. Temp HP — general absorption; keep hp.temp mirrored.
        for (var hit : absorbs) {
            if (hit.mechanic().mode() != AbsorbMode.TEMP_HP) continue;
            absorbFromPool(event, character, hit.effect(), "temp-hp", true, result);
            if (event.getValue() <= 0) return;
        }
    }

    private void absorbFromPool(DamageEvent event, GameCharacter character, ActiveEffect effect,
                                String ruleName, boolean mirrorTempHp, ResolutionResult result) {
        int pool = effect.getValue() != null ? effect.getValue() : 0;
        if (pool <= 0) return;

        int absorbed = Math.min(pool, event.getValue());
        int remaining = pool - absorbed;
        int before = event.getValue();

        event.setValue(before - absorbed);
        effect.setValue(remaining);
        if (mirrorTempHp && character.getHp() != null) character.getHp().setTemp(remaining);
        result.addStep(ruleName,
                "Absorbed " + absorbed + " damage (" + remaining + " remaining)",
                before, event.getValue());
        if (remaining == 0) character.removeEffect(effect);
    }
}
