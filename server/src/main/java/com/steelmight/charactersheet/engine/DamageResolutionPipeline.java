package com.steelmight.charactersheet.engine;

import com.steelmight.charactersheet.engine.rules.damage.AbsorptionRule;
import com.steelmight.charactersheet.engine.rules.damage.ArmorRule;
import com.steelmight.charactersheet.engine.rules.damage.ConcentrationCheckRule;
import com.steelmight.charactersheet.engine.rules.damage.DamageCapRule;
import com.steelmight.charactersheet.engine.rules.damage.DeathCheckRule;
import com.steelmight.charactersheet.engine.rules.damage.FlatTakenModifierRule;
import com.steelmight.charactersheet.engine.rules.damage.HpReductionRule;
import com.steelmight.charactersheet.engine.rules.damage.ImmunityRule;
import com.steelmight.charactersheet.engine.rules.damage.ResistanceVulnerabilityRule;
import com.steelmight.charactersheet.engine.rules.damage.TriggeredEffectsRule;
import com.steelmight.charactersheet.model.Combatant;
import org.springframework.stereotype.Component;
import java.util.List;

/**
 * Ordered damage rules (M0-B core + M2-A protections + M2-B triggers):
 * Immunity → Resistance/Vulnerability → FlatTakenModifier (wounded, Q07: before armor)
 * → Armor → DamageCap → Absorption (block → magic shield → temp HP)
 * → HpReduction → Downed → ConcentrationCheck (M4-C) → TriggeredEffects.
 *
 * Early-stop semantics (Q04): immunity HALTS the event — nothing after it runs,
 * triggers included. Absorbed-to-0 damage skips the remaining damage rules but
 * still reaches the triggers stage (the damage "landed" — it wakes sleepers).
 */
@Component
public class DamageResolutionPipeline {

    private final List<ResolutionRule<DamageEvent>> rules;
    private final TriggeredEffectsRule triggeredEffectsRule;

    public DamageResolutionPipeline(ImmunityRule immunityRule,
                                    ResistanceVulnerabilityRule resistanceVulnerabilityRule,
                                    FlatTakenModifierRule flatTakenModifierRule,
                                    ArmorRule armorRule,
                                    DamageCapRule damageCapRule,
                                    AbsorptionRule absorptionRule,
                                    HpReductionRule hpReductionRule,
                                    DeathCheckRule deathCheckRule,
                                    ConcentrationCheckRule concentrationCheckRule,
                                    TriggeredEffectsRule triggeredEffectsRule) {
        this.rules = List.of(
                immunityRule,
                resistanceVulnerabilityRule,
                flatTakenModifierRule,
                armorRule,
                damageCapRule,
                absorptionRule,
                hpReductionRule,
                deathCheckRule,
                concentrationCheckRule);
        this.triggeredEffectsRule = triggeredEffectsRule;
    }

    public ResolutionResult resolve(DamageEvent event, Combatant character) {
        var result = new ResolutionResult();
        for (var rule : rules) {
            rule.apply(event, character, result);
            if (event.isHalted() || event.getValue() <= 0) break;
        }
        if (!event.isHalted()) {
            triggeredEffectsRule.apply(event, character, result);
        }
        return result;
    }
}
