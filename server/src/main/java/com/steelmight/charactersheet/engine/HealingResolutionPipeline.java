package com.steelmight.charactersheet.engine;

import com.steelmight.charactersheet.engine.rules.heal.ApplyHealingRule;
import com.steelmight.charactersheet.engine.rules.heal.CursedRule;
import com.steelmight.charactersheet.engine.rules.heal.DecayingRule;
import com.steelmight.charactersheet.engine.rules.heal.DownedHealBlockRule;
import com.steelmight.charactersheet.engine.rules.heal.MaimedRule;
import com.steelmight.charactersheet.model.Combatant;
import org.springframework.stereotype.Component;
import java.util.List;

/**
 * Ordered healing rules (M0-B core + M2-B modifiers + M2-D downed block):
 * DownedHealBlock (Q11: healing the downed/dead is a no-op) → Maimed (halve)
 * → Cursed (block — wins over Decaying by running first) → Decaying (convert
 * to true damage) → ApplyHealing (derived-max cap).
 * A rule that zeroes the heal stops the pipeline. Public — HoT ticks (M2-B)
 * and potions (M5-C) call resolve() directly. Temp-HP grants are effect
 * applications (M0-A), not healing — these rules never touch them.
 */
@Component
public class HealingResolutionPipeline {

    private final List<ResolutionRule<HealEvent>> rules;

    public HealingResolutionPipeline(DownedHealBlockRule downedHealBlockRule,
                                     MaimedRule maimedRule,
                                     CursedRule cursedRule,
                                     DecayingRule decayingRule,
                                     ApplyHealingRule applyHealingRule) {
        this.rules = List.of(downedHealBlockRule, maimedRule, cursedRule, decayingRule, applyHealingRule);
    }

    public ResolutionResult resolve(HealEvent event, Combatant character) {
        var result = new ResolutionResult();
        for (var rule : rules) {
            rule.apply(event, character, result);
            if (event.getValue() <= 0) break;
        }
        return result;
    }
}
