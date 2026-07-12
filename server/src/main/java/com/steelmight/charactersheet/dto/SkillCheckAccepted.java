package com.steelmight.charactersheet.dto;

/** Outcome of accepting a skill-check result: whether the final card was consumed/burned. */
public record SkillCheckAccepted(boolean cardRemoved, String removal) {}
