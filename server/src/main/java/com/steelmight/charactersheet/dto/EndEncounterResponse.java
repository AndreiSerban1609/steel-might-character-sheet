package com.steelmight.charactersheet.dto;

/** POST /rooms/{room}/encounter/end — the (now inactive) encounter plus the XP payout it triggered. */
public record EndEncounterResponse(EncounterView encounter, XpAward xpAward) {}
