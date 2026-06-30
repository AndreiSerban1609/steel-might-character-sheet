package com.steelmight.charactersheet.dto;

/** A configurable template card (neutral or encounter). */
public record DeckCard(String name, int modifier, String description) {}
