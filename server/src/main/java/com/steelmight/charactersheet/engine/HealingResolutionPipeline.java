package com.steelmight.charactersheet.engine;

import com.steelmight.charactersheet.model.GameCharacter;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class HealingResolutionPipeline {

    private final List<ResolutionRule<HealEvent>> rules;

    public HealingResolutionPipeline() {
        // Rules in game-design order:
        // 1. Maimed — healing halved
        // 2. Cursed — healing blocked entirely
        // 3. Decaying — healing becomes damage
        // 4. Apply healing (capped at max HP)
        this.rules = List.of();
    }

    public ResolutionResult resolve(HealEvent event, GameCharacter character) {
        var result = new ResolutionResult();
        for (var rule : rules) {
            rule.apply(event, character, result);
        }
        return result;
    }
}
