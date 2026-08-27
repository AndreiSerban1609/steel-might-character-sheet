package com.steelmight.charactersheet.engine.rules.damage;

import com.steelmight.charactersheet.engine.DamageEvent;
import com.steelmight.charactersheet.engine.EffectApplicationEngine;
import com.steelmight.charactersheet.engine.ModifiableStat;
import com.steelmight.charactersheet.engine.RandomSource;
import com.steelmight.charactersheet.engine.ResolutionResult;
import com.steelmight.charactersheet.engine.ResolutionRule;
import com.steelmight.charactersheet.engine.StatDerivationEngine;
import com.steelmight.charactersheet.model.AbilityScore;
import com.steelmight.charactersheet.model.Combatant;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Concentration/channeling break on damage (M4-C, Guide pp.29-30 / Q26 / N2):
 * HP-reducing damage against a caster holding a concentrating or channeling
 * marker forces a WILL saving throw, DC = 5 + attacker's might. Failure ends
 * the maintained spell — the marker AND every effect it applied (matched by
 * source) are removed. Without attackerMight on the request, the server emits
 * a resolve-manually step instead of rolling.
 *
 * Save convention (implementation default): d10 + WILL modifier
 * (+ proficiency when proficient in WILL saves, + willSave effect modifiers) —
 * S&M rolls d10s; the Guide does not spell out the save die.
 */
@Component
public class ConcentrationCheckRule implements ResolutionRule<DamageEvent> {

    private static final List<String> MARKERS = List.of("concentrating", "channeling");

    private final StatDerivationEngine statEngine;
    private final EffectApplicationEngine effectEngine;
    private final RandomSource randomSource;

    public ConcentrationCheckRule(StatDerivationEngine statEngine,
                                  EffectApplicationEngine effectEngine,
                                  RandomSource randomSource) {
        this.statEngine = statEngine;
        this.effectEngine = effectEngine;
        this.randomSource = randomSource;
    }

    @Override
    public void apply(DamageEvent event, Combatant character, ResolutionResult result) {
        if (!event.isHpReduced()) return;

        for (String markerId : MARKERS) {
            var marker = character.getActiveEffects().stream()
                    .filter(e -> markerId.equals(e.getEffectId()))
                    .findFirst()
                    .orElse(null);
            if (marker == null) continue;

            if (event.getAttackerMight() == null) {
                result.addStep("concentration-check",
                        "Damage while " + markerId + " — resolve the WILL save manually "
                                + "(DC = 5 + attacker's might); on failure remove " + markerId, 0, 0);
                continue;
            }

            int dc = 5 + event.getAttackerMight();
            int roll = 1 + randomSource.nextInt(10);
            int bonus = character.getStats().modifier(AbilityScore.WILL)
                    + (character.getSavingThrowProficiencies().contains(AbilityScore.WILL)
                            ? statEngine.computeProficiencyBonus(character) : 0);
            bonus = statEngine.resolveModifiedStat(character, ModifiableStat.WILL_SAVE, bonus);
            int total = roll + bonus;

            if (total >= dc) {
                result.addStep("concentration-check",
                        "WILL save held " + markerId + " (" + roll + " + " + bonus
                                + " = " + total + " vs DC " + dc + ")", total, total);
                continue;
            }

            result.addStep("concentration-check",
                    "WILL save failed (" + roll + " + " + bonus + " = " + total
                            + " vs DC " + dc + ") — " + markerId + " ends", total, total);
            String source = marker.getSource();
            merge(result, effectEngine.remove(character, markerId));
            merge(result, effectEngine.removeBySource(character, source));
            result.addTriggeredEffect("broken:" + markerId);
        }
    }

    private static void merge(ResolutionResult into, ResolutionResult from) {
        from.getSteps().forEach(s ->
                into.addStep("concentration:" + s.rule(), s.note(), s.valueBefore(), s.valueAfter()));
        from.getEffectsTriggered().forEach(into::addTriggeredEffect);
    }
}
