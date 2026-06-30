package com.steelmight.charactersheet.model;

public enum AbilityScore {
    STR, DEX, CON, INT, WIS, WILL, CHA;

    public int modifier(int score) {
        return Math.floorDiv(score - 10, 2);
    }
}
