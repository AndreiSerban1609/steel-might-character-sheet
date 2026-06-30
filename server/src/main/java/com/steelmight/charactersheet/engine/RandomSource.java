package com.steelmight.charactersheet.engine;

/** Injectable randomness so shuffles/rolls are deterministic in tests. */
public interface RandomSource {
    /** Uniform int in [0, bound). */
    int nextInt(int bound);
}
