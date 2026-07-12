package com.steelmight.charactersheet.dto;

/** A bonus accumulated from a passed redraw-effect card; adds to the check's final total. */
public record RedrawBonus(String name, int modifier) {}
