package com.steelmight.charactersheet.engine;

import java.util.ArrayList;
import java.util.List;

public class ResolutionResult {

    private final List<ResolutionStep> steps = new ArrayList<>();
    private final List<String> effectsTriggered = new ArrayList<>();

    public void addStep(String rule, String note, int valueBefore, int valueAfter) {
        steps.add(new ResolutionStep(rule, note, valueBefore, valueAfter));
    }

    public void addTriggeredEffect(String effectId) {
        effectsTriggered.add(effectId);
    }

    public List<ResolutionStep> getSteps() { return steps; }
    public List<String> getEffectsTriggered() { return effectsTriggered; }
}
