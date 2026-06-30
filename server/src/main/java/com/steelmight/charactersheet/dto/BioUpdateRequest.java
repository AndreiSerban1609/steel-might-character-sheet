package com.steelmight.charactersheet.dto;

public record BioUpdateRequest(
        String name,
        String portraitUrl,
        String symbolUrl,
        String background,
        String alignment,
        AppearanceUpdate appearance,
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
    public record AppearanceUpdate(Integer age, String eyeColor, Integer heightCm,
                                    String skin, Integer weightKg, String hair) {}
}
