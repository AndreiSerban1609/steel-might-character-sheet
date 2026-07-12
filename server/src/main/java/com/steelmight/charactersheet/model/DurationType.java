package com.steelmight.charactersheet.model;

/**
 * How an active effect expires (M3 Part B):
 * ROUNDS — remainingRounds ticks down at turn-end;
 * UNTIL_LONG_REST — cleared by the next rest (the constant name predates Q20's
 * single-rest model and is kept for M4-C compatibility; it means "until the next rest");
 * UNTIL_DISPELLED — only explicit removal clears it.
 */
public enum DurationType {
    ROUNDS,
    UNTIL_LONG_REST,
    UNTIL_DISPELLED
}
