package com.steelmight.charactersheet.engine;

import com.steelmight.charactersheet.model.Combatant;

@FunctionalInterface
public interface ResolutionRule<E> {

    void apply(E event, Combatant character, ResolutionResult result);
}
