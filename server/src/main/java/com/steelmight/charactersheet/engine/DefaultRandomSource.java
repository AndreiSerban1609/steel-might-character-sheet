package com.steelmight.charactersheet.engine;

import org.springframework.stereotype.Component;

import java.util.random.RandomGenerator;

@Component
public class DefaultRandomSource implements RandomSource {

    private final RandomGenerator random = new java.util.Random();

    @Override
    public int nextInt(int bound) {
        return random.nextInt(bound);
    }
}
