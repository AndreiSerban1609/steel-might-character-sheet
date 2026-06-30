package com.steelmight.charactersheet.dto;

public record BioSnapshot(
        String name,
        String portraitUrl,
        String symbolUrl,
        String raceId,
        String pathId,
        String classId,
        String specializationId,
        int level,
        String background,
        String alignment,
        AppearanceView appearance,
        String personalityTraits,
        String ideals,
        String bonds,
        String flaws,
        String backstory,
        String notes,
        String allies,
        String organizations,
        String titles
) {
    public record AppearanceView(Integer age, String eyeColor, Integer heightCm,
                                  String skin, Integer weightKg, String hair) {}
}
