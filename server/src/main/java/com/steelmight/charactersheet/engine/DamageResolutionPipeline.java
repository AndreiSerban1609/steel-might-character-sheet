package com.steelmight.charactersheet.engine;

import com.steelmight.charactersheet.model.GameCharacter;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class DamageResolutionPipeline {

    private final List<ResolutionRule<DamageEvent>> rules;

    public DamageResolutionPipeline() {
        // Rules are applied in game-design order.
        // Each rule will be its own class once implemented.
        this.rules = List.of(
                // 1. Resistance / vulnerability
                // 2. Physical armor (PA) or magic armor (MA)
                // 3. Aura checks (paladin auras, etc.)
                // 4. Damage cap effects
                // 5. Block
                // 6. Temp HP absorption
                // 7. HP reduction
                // 8. Death checks (0 HP triggers)
                // 9. Triggered effects (Burning from fire, Erratic Presence, etc.)
        );
    }

    public ResolutionResult resolve(DamageEvent event, GameCharacter character) {
        var result = new ResolutionResult();
        for (var rule : rules) {
            rule.apply(event, character, result);
            if (event.getValue() <= 0) break;
        }
        return result;
    }
}
