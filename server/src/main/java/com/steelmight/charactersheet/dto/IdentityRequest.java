package com.steelmight.charactersheet.dto;

/** Patch for identity fields; any null field is left unchanged. */
public record IdentityRequest(String name, Integer level) {}
