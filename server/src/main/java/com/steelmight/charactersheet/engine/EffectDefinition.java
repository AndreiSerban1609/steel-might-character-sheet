package com.steelmight.charactersheet.engine;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
        String description
) {
    public boolean hasMechanicOfType(MechanicType type) {
        return mechanics != null && mechanics.stream().anyMatch(m -> m.type() == type);
    }

    public List<EffectMechanic> mechanicsOfType(MechanicType type) {
        if (mechanics == null) return List.of();
        return mechanics.stream().filter(m -> m.type() == type).toList();
    }
}
