package com.steelmight.charactersheet.engine;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ResolutionResult {

    private final List<ResolutionStep> steps = new ArrayList<>();
    private final List<String> effectsTriggered = new ArrayList<>();
    // Action-specific extras (M4-A: cast returns saveDC/attackBonus/damageType).
    private final Map<String, Object> payload = new LinkedHashMap<>();

    public void addStep(String rule, String note, int valueBefore, int valueAfter) {
        steps.add(new ResolutionStep(rule, note, valueBefore, valueAfter));
    }

    public void addTriggeredEffect(String effectId) {
        effectsTriggered.add(effectId);
    }

    public void putPayload(String key, Object value) {
        payload.put(key, value);
    }

    public List<ResolutionStep> getSteps() { return steps; }
    public List<String> getEffectsTriggered() { return effectsTriggered; }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Map<String, Object> getPayload() { return payload; }
}
