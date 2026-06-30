package com.steelmight.charactersheet.dto;

/** A single deck card. {@code modifier} is null for criticals and Stat cards (resolved at draw time). */
public record Card(CardType type, String name, Integer modifier, String description) {}
