package com.steelmight.charactersheet.engine;

import com.steelmight.charactersheet.model.GameCharacter;

@FunctionalInterface
public interface ResolutionRule<E> {

    void apply(E event, GameCharacter character, ResolutionResult result);
}
