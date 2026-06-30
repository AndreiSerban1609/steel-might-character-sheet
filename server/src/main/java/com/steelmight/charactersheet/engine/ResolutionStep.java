package com.steelmight.charactersheet.engine;

public record ResolutionStep(
        String rule,
        String note,
        int valueBefore,
        int valueAfter
) {}
