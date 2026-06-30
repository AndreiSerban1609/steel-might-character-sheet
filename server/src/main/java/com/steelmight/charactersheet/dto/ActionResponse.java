package com.steelmight.charactersheet.dto;

import com.steelmight.charactersheet.engine.ResolutionResult;

public record ActionResponse<T>(
        ResolutionResult resolution,
        T snapshot
) {}
