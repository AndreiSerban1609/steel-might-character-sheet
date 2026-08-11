package com.steelmight.charactersheet.engine;

import com.steelmight.charactersheet.gamedata.GameDataProvider;
import com.steelmight.charactersheet.model.ActiveEffect;
import com.steelmight.charactersheet.model.GameCharacter;
import com.steelmight.charactersheet.model.LifeStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

/**
 * Effect apply/remove with stacking + threshold semantics (M0-A).
 *
 * Buckets by definition flags (M0-A R3):
 * - multiInstance                  → always a new independent instance (burning, envenomed, wounded)
 * - noStack damageAbsorb           → keep-higher value; hp.temp mirrors (temporary-hp)
 * - standUpCost mechanic           → single instance, re-apply INCREMENTS stacks (prone)
 * - stackBased + negative          → threshold accumulation (R3b): dormant until stacks >= ceil(level/2)
 * - stackBased + positive          → single instance, stacks is a counter, re-apply adds (warded, block)
 * - otherwise                      → refresh: one instance, update source/value, reset duration (taunted)
 *
 * Immunity/Warded negation and composites are M2 layers; this engine is the base they extend.
 */
@Component
public class EffectApplicationEngine {

    private static final Logger log = LoggerFactory.getLogger(EffectApplicationEngine.class);

    private final GameDataProvider gameData;
    private final StatDerivationEngine statEngine;

    public EffectApplicationEngine(GameDataProvider gameData, StatDerivationEngine statEngine) {
        this.gameData = gameData;
        this.statEngine = statEngine;
    }

    // ---- Apply ----

    public ResolutionResult apply(GameCharacter target, EffectApplication app) {
        var def = gameData.getEffect(app.effectId());
        if (def == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown effect: " + app.effectId());
        }
        int stacks = app.stacksOrDefault();
        if (stacks < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "stacks must be at least 1");
        }
        if (def.hasValue() && app.value() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "effect '" + def.id() + "' requires a value");
        }

        var result = new ResolutionResult();

        // M2-A protection phase: hard immunity → warded → shield exclusivity.
        if (!app.bypassImmunity() && checkProtections(target, def, app, result)) {
            return result;
        }

        if (def.multiInstance()) {
            applyNewInstance(target, def, app, stacks, result);
        } else if (hasNoStackAbsorb(def)) {
            applyKeepHigher(target, def, app, result);
        } else if (hasStandUpCost(def)) {
            applyIncrementing(target, def, app, stacks, result);
        } else if (def.applicationBased()) {
            applyApplicationLadder(target, def, app, stacks, result);
        } else if (def.stackBased() && def.isNegative()) {
            applyThreshold(target, def, app, stacks, result);
        } else if (def.stackBased()) {
            applyCounter(target, def, app, stacks, result);
        } else {
            applyRefresh(target, def, app, stacks, result);
        }

        checkDeathMechanic(target, result);
        return result;
    }

    /** DEATH mechanic wiring (M2-D): an active DEATH mechanic — exhaustion tier 6 —
     *  kills outright; the Death fight is pending after the current combat (N11a). */
    private void checkDeathMechanic(GameCharacter target, ResolutionResult result) {
        if (target.getLifeStatus() == LifeStatus.DEAD) return;
        int threshold = statEngine.computeStackThreshold(target);
        for (var hit : ActiveMechanics.collect(target, gameData, threshold, MechanicType.DEATH)) {
            target.setLifeStatus(LifeStatus.DEAD);
            target.setDownedRoundsRemaining(null);
            target.setPendingDeathFight(true);
            result.addStep("death",
                    hit.def().id() + " reaches its lethal tier — " + target.getName() + " dies", 0, 0);
            result.addTriggeredEffect("death");
            return;
        }
    }

    /** Application-based ladders (corroded, exhaustion): each application increments the
     *  tier count, capped at maxApplications; every application (including past the cap)
     *  refreshes the duration of all acquired tiers (M2-C — corroded's "1 round" per
     *  application; exhaustion has no durations and persists until removed). */
    private void applyApplicationLadder(GameCharacter target, EffectDefinition def, EffectApplication app,
                                        int stacks, ResolutionResult result) {
        int cap = def.maxApplications() != null ? def.maxApplications() : Integer.MAX_VALUE;
        var existing = findFirst(target, def.id());
        if (existing == null) {
            existing = new ActiveEffect(def.id(), app.source(), 0, app.value(),
                    null, target.getActiveEffects().size());
            target.addEffect(existing);
        }
        int before = existing.getStacks();
        existing.setStacks(Math.min(cap, before + stacks));
        existing.setSource(app.source());
        stampDuration(existing, app);
        Integer duration = app.durationRounds() != null ? app.durationRounds() : ladderDurationRounds(def);
        if (duration != null) existing.setRemainingRounds(duration);
        result.addStep("apply-effect",
                def.id() + " tier " + existing.getStacks() + "/" + (def.maxApplications() != null ? def.maxApplications() : "∞")
                        + (existing.getStacks() == before ? " (already at max; duration refreshed)" : ""),
                before, existing.getStacks());
    }

    /** Rounds parsed from the ladder's tier durations (e.g. "1 round"); null when untimed. */
    private Integer ladderDurationRounds(EffectDefinition def) {
        if (def.applications() == null) return null;
        Integer rounds = null;
        for (var tier : def.applications()) {
            if (tier.duration() == null) continue;
            var m = java.util.regex.Pattern.compile("(\\d+)").matcher(tier.duration());
            if (m.find()) {
                int parsed = Integer.parseInt(m.group(1));
                rounds = rounds == null ? parsed : Math.max(rounds, parsed);
            }
        }
        return rounds;
    }

    // ---- Protections (M2-A) ----

    /** @return true when the application was negated/rejected (steps already recorded). */
    private boolean checkProtections(GameCharacter target, EffectDefinition def,
                                     EffectApplication app, ResolutionResult result) {
        int threshold = statEngine.computeStackThreshold(target);

        // 1. Hard immunity — active IMMUNITY mechanics (excluding warded-style consumeStacks)
        //    covering "effects", "negativeEffects" (polarity), or the specific id.
        //    Composite-aware via ActiveMechanics (M2-C).
        for (var hit : ActiveMechanics.collect(target, gameData, threshold, MechanicType.IMMUNITY)) {
            var mech = hit.mechanic();
            if (mech.consumeStacks()) continue;
            var targets = mech.immunityTargets();
            boolean covers = targets.contains("effects")
                    || (targets.contains("negativeEffects") && def.isNegative())
                    || targets.contains(def.id());
            if (!covers) continue;
            if (mech.except() != null && mech.except().contains(def.id())) continue;

            result.addStep("immunity",
                    hit.def().id() + " grants immunity to " + def.id() + " — nothing applied", 0, 0);
            return true;
        }

        // 2. Warded — negates one incoming negative application per stack (Guide p.14).
        //    Checked after hard immunities so an immune target doesn't waste a ward stack.
        if (def.isNegative()) {
            var warded = findFirst(target, "warded");
            if (warded != null && warded.getStacks() >= 1) {
                int before = warded.getStacks();
                warded.setStacks(before - 1);
                result.addStep("warded",
                        "warded negates " + def.id() + " (consumed 1 stack)", before, warded.getStacks());
                if (warded.getStacks() == 0) {
                    target.removeEffect(warded);
                    result.addStep("remove-effect", "warded depleted", 0, 0);
                }
                return true;
            }
        }

        // 3. Shield exclusivity (Q08): no new shield while ANY shield is active, unless it
        //    comes from the same source + ability (then it refreshes/stacks per its own
        //    rules) or the caller opts to destroy the active one (replaceExistingShield).
        if (isShield(def)) {
            var blocking = target.getActiveEffects().stream()
                    .filter(e -> {
                        var d = gameData.getEffect(e.getEffectId());
                        return d != null && isShield(d);
                    })
                    .filter(e -> !(e.getEffectId().equals(def.id())
                            && java.util.Objects.equals(e.getSource(), app.source())))
                    .toList();
            if (!blocking.isEmpty()) {
                if (app.replaceExistingShield()) {
                    for (var shield : blocking) {
                        removeInstance(target, shield, result,
                                "shield destroyed to accept " + def.id() + ": " + shield.getEffectId());
                    }
                } else {
                    result.addStep("shieldExclusivity",
                            blocking.get(0).getEffectId() + " is active — new shield "
                                    + def.id() + " rejected", 0, 0);
                    return true;
                }
            }
        }

        return false;
    }

    /** Shields per the unified shield rule: block, magic/physical shield, temporary-hp. */
    private boolean isShield(EffectDefinition def) {
        return def.mechanicsOfType(MechanicType.DAMAGE_ABSORB).stream()
                .anyMatch(m -> m.mode() == AbsorbMode.INSTANCES
                        || m.mode() == AbsorbMode.MAGIC_SHIELD
                        || m.mode() == AbsorbMode.PHYSICAL_SHIELD
                        || m.mode() == AbsorbMode.TEMP_HP);
    }

    private void applyNewInstance(GameCharacter target, EffectDefinition def, EffectApplication app,
                                  int stacks, ResolutionResult result) {
        var effect = new ActiveEffect(def.id(), app.source(), stacks, app.value(),
                app.durationRounds(), target.getActiveEffects().size());
        stampDuration(effect, app);
        target.addEffect(effect);
        result.addStep("apply-effect", "Applied " + def.id() + " (new instance)", 0, stacks);
    }

    private void applyKeepHigher(GameCharacter target, EffectDefinition def, EffectApplication app,
                                 ResolutionResult result) {
        // temporary-hp semantics: higher value replaces lower; lower or equal is ignored.
        var existing = findFirst(target, def.id());
        int incoming = app.value() != null ? app.value() : 0;
        if (existing == null) {
            var effect = new ActiveEffect(def.id(), app.source(), 1, incoming,
                    app.durationRounds(), target.getActiveEffects().size());
            stampDuration(effect, app);
            target.addEffect(effect);
            mirrorTempHp(target, def, incoming);
            result.addStep("apply-effect", "Applied " + def.id(), 0, incoming);
            return;
        }
        int current = existing.getValue() != null ? existing.getValue() : 0;
        if (incoming > current) {
            existing.setValue(incoming);
            existing.setSource(app.source());
            stampDuration(existing, app);
            if (app.durationRounds() != null) existing.setRemainingRounds(app.durationRounds());
            mirrorTempHp(target, def, incoming);
            result.addStep("apply-effect", def.id() + " value raised", current, incoming);
        } else {
            result.addStep("apply-effect",
                    def.id() + " kept higher value (" + current + " >= " + incoming + ")", current, current);
        }
    }

    private void applyIncrementing(GameCharacter target, EffectDefinition def, EffectApplication app,
                                   int stacks, ResolutionResult result) {
        // prone: re-application increments stacks (feeds the stand-up cost), never refreshes.
        var existing = findFirst(target, def.id());
        if (existing == null) {
            applyNewInstance(target, def, app, stacks, result);
            return;
        }
        int before = existing.getStacks();
        existing.setStacks(before + stacks);
        existing.setSource(app.source());
        if (app.durationRounds() != null) existing.setRemainingRounds(app.durationRounds());
        result.addStep("apply-effect", def.id() + " re-applied (stand-up cost increases)",
                before, existing.getStacks());
    }

    private void applyThreshold(GameCharacter target, EffectDefinition def, EffectApplication app,
                                int stacks, ResolutionResult result) {
        int threshold = statEngine.computeStackThreshold(target);
        var existing = findFirst(target, def.id());
        if (existing == null) {
            existing = new ActiveEffect(def.id(), app.source(), 0, app.value(),
                    null, target.getActiveEffects().size());
            stampDuration(existing, app);
            target.addEffect(existing);
        } else {
            existing.setSource(app.source());
            guardQ03(def, existing, app);
        }

        if (app.durationRounds() != null) {
            // Direct application (e.g. a spell's "stunned for 1 round"): open/extend the
            // active window directly — no stack math, dormant stacks untouched.
            int before = existing.getRemainingRounds() != null ? existing.getRemainingRounds() : 0;
            int after = Math.max(before, app.durationRounds());
            existing.setRemainingRounds(after);
            result.addStep("apply-effect",
                    def.id() + " active window set directly (no accumulation)", before, after);
            return;
        }

        int before = existing.getStacks();
        existing.setStacks(before + stacks);
        boolean active = existing.getStacks() >= threshold;
        if (active && app.duringOwnTurn()) {
            // Player cleanse window (N9): survives the current end-of-turn decrement and
            // expires at the end of the NEXT turn → window of 2 decrements.
            int window = existing.getRemainingRounds() != null ? existing.getRemainingRounds() : 0;
            existing.setRemainingRounds(Math.max(window, 2));
        }
        result.addStep("stack-accumulation",
                def.id() + " " + existing.getStacks() + "/" + threshold + " stacks — "
                        + (active ? "ACTIVE" : "dormant"),
                before, existing.getStacks());
    }

    private void applyCounter(GameCharacter target, EffectDefinition def, EffectApplication app,
                              int stacks, ResolutionResult result) {
        // Positive stack-counters (warded, block): one instance, stacks accumulate,
        // events consume them (M2-A).
        var existing = findFirst(target, def.id());
        if (existing == null) {
            applyNewInstance(target, def, app, stacks, result);
            return;
        }
        guardQ03(def, existing, app);
        int before = existing.getStacks();
        existing.setStacks(before + stacks);
        existing.setSource(app.source());
        stampDuration(existing, app);
        if (app.durationRounds() != null) existing.setRemainingRounds(app.durationRounds());
        result.addStep("apply-effect", def.id() + " stacks added", before, existing.getStacks());
    }

    private void applyRefresh(GameCharacter target, EffectDefinition def, EffectApplication app,
                              int stacks, ResolutionResult result) {
        var existing = findFirst(target, def.id());
        if (existing == null) {
            applyNewInstance(target, def, app, stacks, result);
            return;
        }
        existing.setSource(app.source());
        existing.setStacks(stacks);
        existing.setValue(app.value() != null ? app.value() : existing.getValue());
        stampDuration(existing, app);
        existing.setRemainingRounds(app.durationRounds());
        result.addStep("apply-effect", def.id() + " refreshed (source: " + app.source() + ")",
                existing.getStacks(), existing.getStacks());
    }

    /** Q03: no current effect combines stackBased + hasValue with differing values — assert and log loudly. */
    private void guardQ03(EffectDefinition def, ActiveEffect existing, EffectApplication app) {
        if (def.hasValue() && app.value() != null && existing.getValue() != null
                && !existing.getValue().equals(app.value())) {
            log.error("Q03 violated: stackBased+hasValue effect '{}' re-applied with differing value "
                    + "({} vs {}) — keeping the existing value. Rule needs a design decision.",
                    def.id(), existing.getValue(), app.value());
        }
    }

    // ---- Remove ----

    /** Removes ALL instances of the effect. Absent → 200 with a "not present" step (idempotent). */
    public ResolutionResult remove(GameCharacter target, String effectId) {
        var result = new ResolutionResult();
        var matches = target.getActiveEffects().stream()
                .filter(e -> e.getEffectId().equals(effectId))
                .toList();
        if (matches.isEmpty()) {
            result.addStep("remove-effect", effectId + " not present", 0, 0);
            return result;
        }
        for (var effect : matches) {
            removeInstance(target, effect, result, "Removed " + effectId);
        }
        return result;
    }

    /** Removes every active effect whose source matches — a dropped concentration/
     *  channeling spell takes the effects it applied with it (M4-C). */
    public ResolutionResult removeBySource(GameCharacter target, String source) {
        var result = new ResolutionResult();
        if (source == null) return result;
        for (var effect : new ArrayList<>(target.getActiveEffects())) {
            if (source.equals(effect.getSource())) {
                removeInstance(target, effect, result,
                        effect.getEffectId() + " removed (its source ended)");
            }
        }
        return result;
    }

    private void removeInstance(GameCharacter target, ActiveEffect effect,
                                ResolutionResult result, String note) {
        var def = gameData.getEffect(effect.getEffectId());
        target.removeEffect(effect);
        if (def != null) mirrorTempHp(target, def, 0);
        result.addStep("remove-effect", note, effect.getStacks(), 0);
    }

    // ---- Turn-end tick (N9 consumption + window/duration expiry) ----

    /**
     * End-of-turn effect lifecycle for the afflicted character (M0-A R3b / N9):
     * active threshold effects consume {@code threshold} stacks (full bites only — never
     * partial, dormant leftovers linger until a rest) and their window decrements; other
     * effects with a duration tick down and expire through the removal path.
     * M0-C's turn-end endpoint delegates here and adds AP recovery on turn-start.
     */
    public ResolutionResult tickTurnEnd(GameCharacter target) {
        var result = new ResolutionResult();
        int threshold = statEngine.computeStackThreshold(target);

        for (var effect : new ArrayList<>(target.getActiveEffects())) {
            var def = gameData.getEffect(effect.getEffectId());
            if (def == null) {
                log.warn("Skipping tick for unknown effect id '{}'", effect.getEffectId());
                continue;
            }

            if (def.stackBased() && !def.multiInstance() && def.isNegative()) {
                if (!EffectActivity.isActive(def, effect, threshold)) continue; // dormant: nothing ticks
                tickThresholdEffect(target, def, effect, threshold, result);
            } else if (effect.getRemainingRounds() != null) {
                int remaining = effect.getRemainingRounds() - 1;
                effect.setRemainingRounds(remaining);
                if (remaining <= 0) {
                    removeInstance(target, effect, result, def.id() + " expired");
                }
            }
        }
        return result;
    }

    private void tickThresholdEffect(GameCharacter target, EffectDefinition def, ActiveEffect effect,
                                     int threshold, ResolutionResult result) {
        // N9: consume threshold-many stacks — only in full bites (8→5→2 keeps the 2).
        if (effect.getStacks() >= threshold) {
            int before = effect.getStacks();
            effect.setStacks(before - threshold);
            result.addStep("stack-consumption",
                    def.id() + " consumed " + threshold + " stacks (end of turn)",
                    before, effect.getStacks());
        }
        if (EffectActivity.hasOpenWindow(effect)) {
            effect.setRemainingRounds(effect.getRemainingRounds() - 1);
        }

        boolean stillActive = effect.getStacks() >= threshold || EffectActivity.hasOpenWindow(effect);
        if (!stillActive) {
            effect.setRemainingRounds(null); // window closed; sub-threshold stacks linger until rest
            if (effect.getStacks() == 0) {
                removeInstance(target, effect, result, def.id() + " expired");
            } else {
                result.addStep("effect-dormant",
                        def.id() + " dormant (" + effect.getStacks() + "/" + threshold + " stacks linger)",
                        effect.getStacks(), effect.getStacks());
            }
        }
    }

    // ---- Helpers ----

    private ActiveEffect findFirst(GameCharacter target, String effectId) {
        return target.getActiveEffects().stream()
                .filter(e -> e.getEffectId().equals(effectId))
                .findFirst()
                .orElse(null);
    }

    private boolean hasNoStackAbsorb(EffectDefinition def) {
        return def.mechanicsOfType(MechanicType.DAMAGE_ABSORB).stream()
                .anyMatch(EffectMechanic::noStack);
    }

    private boolean hasStandUpCost(EffectDefinition def) {
        return def.hasMechanicOfType(MechanicType.STAND_UP_COST);
    }

    /** Explicit expiry model from the request (M3 Part B); null keeps the derived default. */
    private void stampDuration(ActiveEffect effect, EffectApplication app) {
        if (app.durationType() != null) effect.setDurationType(app.durationType());
    }

    /** temporary-hp: the ActiveEffect.value is the source of truth; hp.temp mirrors it. */
    private void mirrorTempHp(GameCharacter target, EffectDefinition def, int value) {
        if (hasNoStackAbsorb(def) && target.getHp() != null) {
            target.getHp().setTemp(value);
        }
    }
}
