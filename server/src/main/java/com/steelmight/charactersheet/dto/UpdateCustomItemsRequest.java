package com.steelmight.charactersheet.dto;

import java.util.List;

/**
 * Full replacement of a character's custom item definitions (demo feedback #19).
 * Same shape as the custom-abilities editor: the client sends the whole list back.
 */
public record UpdateCustomItemsRequest(List<CustomItemView> items) {}
