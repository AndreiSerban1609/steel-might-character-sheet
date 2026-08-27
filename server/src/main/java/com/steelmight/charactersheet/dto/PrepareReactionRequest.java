package com.steelmight.charactersheet.dto;

import com.steelmight.charactersheet.model.PreparedReaction;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Declare a prepared reaction (2026-08-27): {@code note} is what the player readies
 * ("roll out of the way when the ogre swings"), {@code apCost} is paid now, on the
 * declaring turn. Zero is allowed — some preps are free by ruling; the table decides.
 */
public record PrepareReactionRequest(
        @NotBlank @Size(max = PreparedReaction.MAX_NOTE_LENGTH) String note,
        @Min(0) int apCost
) {}
