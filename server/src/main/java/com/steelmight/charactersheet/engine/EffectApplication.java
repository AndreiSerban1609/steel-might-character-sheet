package com.steelmight.charactersheet.engine;

/**
 * One incoming effect application (M0-A R1, M2-A flags).
 *
 * @param effectId              id from effects.json
 * @param source                who/what applied it (display data; also the shield-exclusivity
 *                              "same source + ability" check, M2-A)
 * @param stacks                incoming stacks; null defaults to 1
 * @param value                 effect value; required when the definition has {@code hasValue}
 * @param durationRounds        explicit active window — a "direct application" (e.g. a spell's
 *                              "stunned for 1 round") that bypasses threshold accumulation
 * @param duringOwnTurn         player cleanse window (N9): a threshold trigger during the
 *                              character's own turn survives the current end-of-turn decrement
 *                              and expires at the end of their next turn
 * @param bypassImmunity        skip the protection phase (immunity/warded/shield exclusivity)
 *                              for self-inflicted/system effects (M2-A)
 * @param replaceExistingShield destroy any active shield before applying an incoming one
 *                              instead of rejecting it (unified shield rule, Q08)
 * @param durationType          explicit expiry model (M3 Part B) — e.g. UNTIL_LONG_REST for
 *                              spell effects (M4-C); null derives from durationRounds
 */
public record EffectApplication(
        String effectId,
        String source,
        Integer stacks,
        Integer value,
        Integer durationRounds,
        boolean duringOwnTurn,
        boolean bypassImmunity,
        boolean replaceExistingShield,
        com.steelmight.charactersheet.model.DurationType durationType
) {
    public EffectApplication(String effectId, String source, Integer stacks, Integer value,
                             Integer durationRounds) {
        this(effectId, source, stacks, value, durationRounds, false, false, false, null);
    }

    public EffectApplication(String effectId, String source, Integer stacks, Integer value,
                             Integer durationRounds, boolean duringOwnTurn) {
        this(effectId, source, stacks, value, durationRounds, duringOwnTurn, false, false, null);
    }

    public EffectApplication(String effectId, String source, Integer stacks, Integer value,
                             Integer durationRounds, boolean duringOwnTurn, boolean bypassImmunity,
                             boolean replaceExistingShield) {
        this(effectId, source, stacks, value, durationRounds, duringOwnTurn, bypassImmunity,
                replaceExistingShield, null);
    }

    public int stacksOrDefault() {
        return stacks != null ? stacks : 1;
    }
}
