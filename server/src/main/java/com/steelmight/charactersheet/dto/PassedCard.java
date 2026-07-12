package com.steelmight.charactersheet.dto;

/** A card auto-passed during a draw: "wrong-check" (skill restriction) or "redraw-bonus". */
public record PassedCard(Card card, String reason) {}
