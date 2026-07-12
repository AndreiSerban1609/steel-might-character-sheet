package com.steelmight.charactersheet.engine;

import com.steelmight.charactersheet.gamedata.GameDataProvider;
import com.steelmight.charactersheet.model.DamageType;
import com.steelmight.charactersheet.model.GameCharacter;
import com.steelmight.charactersheet.model.LifeStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Turn lifecycle orchestration (M0-C wiring + M2-B ticks).
 *
 * turn-start: DoT ticks first (Q13 — start-of-turn effects resolve before AP recovery;
 * every instance ticks independently and all tick even if one downs the character, Q14),
 * then AP recovery, then generic startOfTurn trigger dispatch (none in current data).
 *
 * turn-end: HoT ticks (through the healing pipeline so Cursed/Decaying apply), then
 * endOfTurn triggers (suffocating → exhaustion), then duration expiry / threshold
 * stack consumption via the effect engine.
 */
@Component
public class TurnTickService {

    private static final Logger log = LoggerFactory.getLogger(TurnTickService.class);

    private final GameDataProvider gameData;
    private final StatDerivationEngine statEngine;
    private final EffectApplicationEngine effectEngine;
    private final DamageResolutionPipeline damagePipeline;
    private final HealingResolutionPipeline healingPipeline;

    public TurnTickService(GameDataProvider gameData,
                           StatDerivationEngine statEngine,
                           EffectApplicationEngine effectEngine,
                           DamageResolutionPipeline damagePipeline,
                           HealingResolutionPipeline healingPipeline) {
        this.gameData = gameData;
        this.statEngine = statEngine;
        this.effectEngine = effectEngine;
        this.damagePipeline = damagePipeline;
        this.healingPipeline = healingPipeline;
    }

    // ---- Turn start ----

    public ResolutionResult turnStart(GameCharacter character) {
        var result = new ResolutionResult();
        int threshold = statEngine.computeStackThreshold(character);

        // 1. Start-of-turn DoT ticks (burning, envenomed) — before AP recovery (Q13).
        for (var hit : ActiveMechanics.collect(character, gameData, threshold, MechanicType.DOT)) {
            if (hit.mechanic().timing() != Timing.START_OF_TURN) continue;
            tickDot(character, hit, result);
        }

        // 2. AP recovery — carryover, capped at max; derivation handles dazed/stunned/haste.
        int recovery = statEngine.computeAPRecovery(character);
        int before = character.getAp().getCurrent();
        int after = Math.min(character.getAp().getMax(), before + recovery);
        if (after != before) {
            character.getAp().setCurrent(after);
            result.addStep("ap-recovery", "Recovered " + (after - before) + " AP", before, after);
        }

        // 3. Generic startOfTurn trigger dispatch (none in current data).
        dispatchTriggers(character, threshold, TriggerEvent.START_OF_TURN, result);

        return result;
    }

    private void tickDot(GameCharacter character, ActiveMechanics.Hit hit, ResolutionResult result) {
        int perStack = hit.mechanic().value() != null ? hit.mechanic().value() : 1;
        int value = hit.mechanic().valueFromStacks()
                ? perStack * hit.effect().getStacks() : perStack;
        if (value <= 0) return;

        DamageType type;
        try {
            type = DamageType.valueOf(hit.mechanic().damageType().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            log.warn("DoT on '{}' has unknown damage type '{}' — skipping",
                    hit.def().id(), hit.mechanic().damageType());
            return;
        }

        var event = new DamageEvent(value, type, gameData.getDamageCategory(type), List.of("dot"));
        var sub = damagePipeline.resolve(event, character);
        sub.getSteps().forEach(s ->
                result.addStep(hit.def().id() + ":" + s.rule(), s.note(), s.valueBefore(), s.valueAfter()));
        sub.getEffectsTriggered().forEach(result::addTriggeredEffect);
    }

    // ---- Turn end ----

    public ResolutionResult turnEnd(GameCharacter character) {
        var result = new ResolutionResult();
        int threshold = statEngine.computeStackThreshold(character);

        // 1. End-of-turn HoT ticks (regenerating) — through the healing pipeline,
        //    so Cursed blocks and Decaying converts.
        for (var hit : ActiveMechanics.collect(character, gameData, threshold, MechanicType.HOT)) {
            if (hit.mechanic().timing() != Timing.END_OF_TURN) continue;
            int perStack = hit.mechanic().value() != null ? hit.mechanic().value() : 1;
            int value = hit.mechanic().valueFromStacks()
                    ? perStack * hit.effect().getStacks() : perStack;
            if (value <= 0) continue;
            var sub = healingPipeline.resolve(new HealEvent(value), character);
            sub.getSteps().forEach(s ->
                    result.addStep(hit.def().id() + ":" + s.rule(), s.note(), s.valueBefore(), s.valueAfter()));
            sub.getEffectsTriggered().forEach(result::addTriggeredEffect);
        }

        // 2. End-of-turn triggers (suffocating → 1 exhaustion stack).
        dispatchTriggers(character, threshold, TriggerEvent.END_OF_TURN, result);

        // 3. Duration expiry + threshold stack consumption (N9) via the effect engine.
        var tick = effectEngine.tickTurnEnd(character);
        tick.getSteps().forEach(s ->
                result.addStep(s.rule(), s.note(), s.valueBefore(), s.valueAfter()));
        tick.getEffectsTriggered().forEach(result::addTriggeredEffect);

        // 4. Downed countdown (M2-D): the WILL-mod window shrinks at the character's
        //    own turn-end; expiring un-revived → dead, Death fight pending (N11a).
        if (character.getLifeStatus() == LifeStatus.DOWNED
                && character.getDownedRoundsRemaining() != null) {
            int before = character.getDownedRoundsRemaining();
            int remaining = before - 1;
            character.setDownedRoundsRemaining(remaining);
            result.addStep("downed-countdown", remaining > 0
                    ? remaining + " round(s) left to revive"
                    : "the revive window has closed", before, remaining);
            if (remaining <= 0) {
                character.setLifeStatus(LifeStatus.DEAD);
                character.setDownedRoundsRemaining(null);
                character.setPendingDeathFight(true);
                result.addStep("death",
                        character.getName() + " dies un-revived — Death fight after this combat", 0, 0);
                result.addTriggeredEffect("death");
            }
        }

        return result;
    }

    // ---- Trigger dispatch ----

    private void dispatchTriggers(GameCharacter character, int threshold,
                                  TriggerEvent when, ResolutionResult result) {
        for (var hit : ActiveMechanics.collect(character, gameData, threshold, MechanicType.TRIGGER_ON_EVENT)) {
            if (hit.mechanic().event() != when) continue;

            if (hit.mechanic().triggerAction() == TriggerAction.APPLY_EFFECT
                    && hit.mechanic().actionEffect() != null) {
                int stacks = hit.mechanic().actionStacks() != null ? hit.mechanic().actionStacks() : 1;
                var sub = effectEngine.apply(character, new EffectApplication(
                        hit.mechanic().actionEffect(), hit.def().id(), stacks, null, null));
                sub.getSteps().forEach(s ->
                        result.addStep(hit.def().id() + ":" + s.rule(), s.note(),
                                s.valueBefore(), s.valueAfter()));
                sub.getEffectsTriggered().forEach(result::addTriggeredEffect);
            } else {
                log.warn("Unhandled {} triggerAction '{}' on effect '{}' — skipping",
                        when, hit.mechanic().triggerAction(), hit.def().id());
            }
        }
    }
}
