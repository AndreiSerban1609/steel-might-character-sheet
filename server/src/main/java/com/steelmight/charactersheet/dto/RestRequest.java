package com.steelmight.charactersheet.dto;

/**
 * Single tiered rest (Q20 — the short/long split is gone). Quality tier is
 * DM-adjudicated (duration + recharge source) and passed here as the resulting
 * percentage: 25 / 50 / 75 / 100. Omitted → 100.
 */
public record RestRequest(Integer tier) {}
