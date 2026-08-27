package com.steelmight.charactersheet.engine.rules.damage;

import com.steelmight.charactersheet.engine.DamageEvent;
import com.steelmight.charactersheet.engine.ResolutionResult;
import com.steelmight.charactersheet.engine.ResolutionRule;
import com.steelmight.charactersheet.model.AbilityScore;
import com.steelmight.charactersheet.model.Combatant;
import com.steelmight.charactersheet.model.LifeStatus;
import org.springframework.stereotype.Component;

/**
 * Death check at 0 HP (M2-D, replaces the M0-B DownedRule marker):
 * - WILL modifier > 0 → DOWNED for that many rounds (N18: no Death fight during
 *   the window); the countdown decrements at the character's turn-end.
 * - WILL modifier <= 0 → DEAD outright, Death fight pending (fought after the
 *   current combat, N11a).
 * Repeated hits while already downed/dead don't re-trigger.
 */
@Component
public class DeathCheckRule implements ResolutionRule<DamageEvent> {

    @Override
    public void apply(DamageEvent event, Combatant character, ResolutionResult result) {
        if (event.getValue() <= 0) return;
        if (character.getHp().getCurrent() > 0) return;
        if (character.getLifeStatus() != LifeStatus.ALIVE) return;

        // Rulings E4 + 2026-08-26: monsters simply die at 0 HP — no downed window, no Medicine
        // revival, no Death fight. Only players use the death rules.
        if (!character.usesDeathRules()) {
            character.setLifeStatus(LifeStatus.DEAD);
            result.addStep("death", character.getName() + " is slain", 0, 0);
            result.addTriggeredEffect("death");
            return;
        }

        int willMod = character.getStats().modifier(AbilityScore.WILL);
        if (willMod <= 0) {
            character.setLifeStatus(LifeStatus.DEAD);
            character.setPendingDeathFight(true);
            result.addStep("death", character.getName()
                    + " dies outright (WILL modifier " + willMod + " — no downed window)", 0, 0);
            result.addTriggeredEffect("death");
        } else {
            character.setLifeStatus(LifeStatus.DOWNED);
            character.setDownedRoundsRemaining(willMod);
            character.setDownsThisCombat(character.getDownsThisCombat() + 1);
            result.addStep("downed", character.getName() + " is downed — " + willMod
                    + " round(s) to revive (Medicine check)", 0, willMod);
            result.addTriggeredEffect("downed");
        }
    }
}
