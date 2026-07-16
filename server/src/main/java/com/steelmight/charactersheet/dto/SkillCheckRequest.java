package com.steelmight.charactersheet.dto;

/** advantage: "advantage" | "disadvantage" | null (normal draw) — chosen BEFORE the draw. */
public record SkillCheckRequest(String skillId, String advantage) {

    public SkillCheckRequest(String skillId) {
        this(skillId, null);
    }
}
