package com.steelmight.charactersheet.model;

/**
 * Death & dying state machine (M2-D, Q09/N11/N18):
 * ALIVE → (0 HP, WILL mod > 0) → DOWNED for WILL-mod rounds → un-revived → DEAD.
 * ALIVE → (0 HP, WILL mod <= 0) → DEAD outright.
 * DOWNED → Medicine-check revive → ALIVE (crit fail → DEAD).
 * DEAD carries the pending Death fight (fought after the current combat, N11a).
 */
public enum LifeStatus {
    ALIVE,
    DOWNED,
    DEAD
}
