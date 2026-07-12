package com.steelmight.charactersheet.engine.rules.damage;

import com.steelmight.charactersheet.engine.DamageCategory;
import com.steelmight.charactersheet.engine.DamageEvent;
import com.steelmight.charactersheet.engine.ResolutionResult;
import com.steelmight.charactersheet.engine.ResolutionRule;
import com.steelmight.charactersheet.engine.StatDerivationEngine;
import com.steelmight.charactersheet.model.DamageType;
import com.steelmight.charactersheet.model.GameCharacter;
import org.springframework.stereotype.Component;

/**
 * Flat armor reduction (M0-B R2.1): physical by PA — except crushing, which ignores PA
 * (Guide p.7); magical by MA; true by nothing. Skipped for dot/ignoresArmor-tagged
 * events (armor does not reduce start/end-of-turn or environmental damage, Guide pp.7-8).
 */
@Component
public class ArmorRule implements ResolutionRule<DamageEvent> {

    private final StatDerivationEngine statEngine;

    public ArmorRule(StatDerivationEngine statEngine) {
        this.statEngine = statEngine;
    }

    @Override
    public void apply(DamageEvent event, GameCharacter character, ResolutionResult result) {
        if (event.hasTag("dot") || event.hasTag("ignoresArmor")) return;

        int armor;
        String armorName;
        if (event.getCategory() == DamageCategory.PHYSICAL) {
            if (event.getDamageType() == DamageType.CRUSHING) return; // crushing ignores PA
            armor = statEngine.computePA(character);
            armorName = "PA";
        } else if (event.getCategory() == DamageCategory.MAGICAL) {
            armor = statEngine.computeMA(character);
            armorName = "MA";
        } else {
            return; // true damage: no armor
        }

        int reduction = Math.min(event.getValue(), armor);
        if (reduction <= 0) return; // silent pass-through adds no step

        int before = event.getValue();
        event.setValue(before - reduction);
        result.addStep("armor", armorName + " " + armor + " reduces "
                + event.getDamageType().name().toLowerCase() + " damage", before, event.getValue());
    }
}
