package com.steelmight.charactersheet.engine;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * One tier of an application-based effect (corroded, exhaustion): the Nth
 * application activates {@code applications[N-1]} and every tier below it.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EffectApplicationTier(
        int order,
        List<EffectMechanic> mechanics,
        String duration
) {}
