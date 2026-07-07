package com.steelmight.charactersheet.engine;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EffectDefinition(
        String id,
        String name,
        boolean stackBased,
        boolean multiInstance,
        boolean hasValue,
        boolean applicationBased,
        Integer maxApplications,
        String source,
        List<EffectMechanic> mechanics,
        // Application-based ladder (corroded, exhaustion): tier N active at stacks >= N.
        List<EffectApplicationTier> applications,
        String description,
        // Not in the JSON — stamped by GameDataProvider from which array the definition came from.
        EffectPolarity polarity
) {
    public EffectDefinition withPolarity(EffectPolarity polarity) {
        return new EffectDefinition(id, name, stackBased, multiInstance, hasValue,
                applicationBased, maxApplications, source, mechanics, applications, description, polarity);
    }

    public boolean isNegative() {
        return polarity == EffectPolarity.NEGATIVE;
    }

    /**
     * The mechanics a specific ActiveEffect contributes right now. For application-based
     * effects that's the union of tiers 1..stacks; for everything else the flat list.
     */
    public List<EffectMechanic> mechanicsAtStacks(int stacks) {
        if (!applicationBased) {
            return mechanics != null ? mechanics : List.of();
        }
        var result = new ArrayList<EffectMechanic>();
        if (applications != null) {
            for (var tier : applications) {
                if (tier.order() <= stacks && tier.mechanics() != null) {
                    result.addAll(tier.mechanics());
                }
            }
        }
        return result;
    }

    public boolean hasMechanicOfType(MechanicType type) {
        return mechanics != null && mechanics.stream().anyMatch(m -> m.type() == type);
    }

    public List<EffectMechanic> mechanicsOfType(MechanicType type) {
        if (mechanics == null) return List.of();
        return mechanics.stream().filter(m -> m.type() == type).toList();
    }
}
