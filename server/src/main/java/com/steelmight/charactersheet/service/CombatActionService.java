package com.steelmight.charactersheet.service;

import com.steelmight.charactersheet.dto.ApplyEffectRequest;
import com.steelmight.charactersheet.dto.CombatSnapshot;
import com.steelmight.charactersheet.dto.DamageRequest;
import com.steelmight.charactersheet.dto.HealRequest;
import com.steelmight.charactersheet.engine.ActiveMechanics;
import com.steelmight.charactersheet.engine.DamageEvent;
import com.steelmight.charactersheet.engine.DamageResolutionPipeline;
import com.steelmight.charactersheet.engine.EffectActivity;
import com.steelmight.charactersheet.engine.ForcedBehavior;
import com.steelmight.charactersheet.engine.MechanicType;
import com.steelmight.charactersheet.engine.ModifiableStat;
import com.steelmight.charactersheet.engine.RandomSource;
import com.steelmight.charactersheet.model.AbilityScore;
import com.steelmight.charactersheet.model.DamageType;
import com.steelmight.charactersheet.model.LifeStatus;
import java.util.Locale;
import com.steelmight.charactersheet.engine.EffectApplication;
import com.steelmight.charactersheet.engine.EffectApplicationEngine;
import com.steelmight.charactersheet.engine.HealEvent;
import com.steelmight.charactersheet.engine.HealingResolutionPipeline;
import com.steelmight.charactersheet.engine.ResolutionResult;
import com.steelmight.charactersheet.engine.StatDerivationEngine;
import com.steelmight.charactersheet.gamedata.GameDataProvider;
import com.steelmight.charactersheet.model.Combatant;
import com.steelmight.charactersheet.model.MonsterInstance;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

/**
 * The combatant-agnostic half of a combat action (ADR-001 + 2026-08-26 ruling): request
 * validation, event construction, the pipeline call and the audit summary — everything
 * that is the same whether the target is a player or a monster.
 *
 * Deliberately stateless and persistence-free: the caller ({@code CharacterService} for
 * players, {@code MonsterService} for monsters) loads the entity, calls in, saves, logs
 * the returned summary and builds its own snapshot. Adding a third combatant kind means
 * adding a caller, not touching this class.
 */
@Service
public class CombatActionService {

    /** What one action did: the step-by-step resolution and the one-line audit summary. */
    public record Outcome(ResolutionResult resolution, String auditSummary) {}

    private final DamageResolutionPipeline damagePipeline;
    private final HealingResolutionPipeline healingPipeline;
    private final EffectApplicationEngine effectEngine;
    private final StatDerivationEngine statEngine;
    private final GameDataProvider gameData;
    private final CombatantLookup combatants;
    private final RandomSource random;

    public CombatActionService(DamageResolutionPipeline damagePipeline,
                               HealingResolutionPipeline healingPipeline,
                               EffectApplicationEngine effectEngine,
                               StatDerivationEngine statEngine,
                               GameDataProvider gameData,
                               CombatantLookup combatants,
                               RandomSource random) {
        this.damagePipeline = damagePipeline;
        this.healingPipeline = healingPipeline;
        this.effectEngine = effectEngine;
        this.statEngine = statEngine;
        this.gameData = gameData;
        this.combatants = combatants;
        this.random = random;
    }

    // ---- Actions ----

    public Outcome damage(Combatant target, DamageRequest req) {
        if (req.value() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "damage value must be positive");
        }
        var category = gameData.getDamageCategory(req.damageType());
        if (category == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "unknown damage type: " + req.damageType());
        }
        var tags = (req.tags() != null && !req.tags().isEmpty()) ? req.tags() : List.of("directAttack");

        // Story 2.4 attacker context: a named attacker fills in what the request left blank —
        // its authored might (concentration DC) and its id as the event source (wounded-by
        // attribution, source-matched triggers). Explicit request values always win.
        Integer attackerMight = req.attackerMight();
        String sourceId = req.sourceId();
        if (req.attackerCombatantId() != null && !req.attackerCombatantId().isBlank()) {
            var attacker = combatants.find(target.getRoomName(), req.attackerCombatantId()).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "unknown attacker: " + req.attackerCombatantId()));
            if (attackerMight == null && attacker instanceof MonsterInstance monster) {
                attackerMight = monster.getBlock().getMight();
            }
            if (sourceId == null || sourceId.isBlank()) sourceId = attacker.getCombatantId();
            enforceTaunt(attacker, target);
        }

        var event = new DamageEvent(req.value(), req.damageType(), category, tags,
                req.ignoreResistance(), sourceId);
        event.setDuringOwnTurn(req.duringOwnTurn());
        event.setAttackerMight(attackerMight);

        int hpBefore = target.getHp().getCurrent();
        var result = damagePipeline.resolve(event, target);
        int hpAfter = target.getHp().getCurrent();
        int taken = hpBefore - hpAfter;
        return new Outcome(result, req.value() + " " + req.damageType()
                + (taken > 0 ? " — " + taken + " HP lost" : " — no HP lost (absorbed/immune)")
                + " (HP " + hpBefore + "→" + hpAfter + ")");
    }

    /**
     * Taunt (ruling 2026-08-26): an attacker under a {@code mustTargetSource} forced behaviour
     * (the {@code taunted} effect, whose source is the taunter's combatant id) may only aim
     * offensive actions at that taunter while the taunter lives. Whether the attacker could
     * actually reach the taunter is the table's call — the GM removes the taunt if not — so
     * the server simply refuses any other target rather than silently redirecting.
     */
    public void enforceTaunt(Combatant attacker, Combatant target) {
        if (attacker == null || target == null) return;
        if (attacker.getCombatantId().equals(target.getCombatantId())) return;
        int threshold = statEngine.computeStackThreshold(attacker);
        for (var hit : ActiveMechanics.collect(attacker, gameData, threshold, MechanicType.FORCED_BEHAVIOR)) {
            if (hit.mechanic().behavior() != ForcedBehavior.MUST_TARGET_SOURCE) continue;
            String holderId = hit.effect().getSource();
            if (holderId == null || holderId.isBlank() || holderId.equals(target.getCombatantId())) continue;
            var holder = combatants.find(attacker.getRoomName(), holderId).orElse(null);
            // A downed or dead taunter holds nobody's attention any more.
            if (holder == null || holder.getLifeStatus() != LifeStatus.ALIVE) continue;
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    attacker.getName() + " is taunted by " + holder.getName() + " — offensive actions must target "
                            + holder.getName() + " (remove the taunt if they are out of reach)");
        }
    }

    public Outcome heal(Combatant target, HealRequest req) {
        if (req.value() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "heal value must be positive");
        }
        int hpBefore = target.getHp().getCurrent();
        var result = healingPipeline.resolve(new HealEvent(req.value()), target);
        return new Outcome(result, "Healed " + req.value() + " (HP "
                + hpBefore + "→" + target.getHp().getCurrent() + ")");
    }

    public Outcome applyEffect(Combatant target, ApplyEffectRequest req) {
        var result = effectEngine.apply(target, new EffectApplication(
                req.effectId(), req.source(), req.stacks(), req.value(),
                req.duration(), req.duringOwnTurn(), req.bypassImmunity(), req.replaceExistingShield(),
                req.durationType()));
        return new Outcome(result, "Applied " + req.effectId()
                + (req.stacks() != null && req.stacks() > 1 ? " ×" + req.stacks() : "")
                + (req.value() != null ? " (value " + req.value() + ")" : "")
                + (req.duration() != null ? " for " + req.duration() + "r" : ""));
    }

    public Outcome removeEffect(Combatant target, String effectId) {
        var result = effectEngine.remove(target, effectId);
        return new Outcome(result, "Removed " + effectId);
    }

    // ---- Resolve-onto-target helpers (Story 2.3 last mile) ----

    /**
     * A saving throw on the target (ruling E6: monsters save exactly like players):
     * d10 + stat modifier + proficiency when proficient, then SAVE_BONUS effects (and
     * WILL_SAVE for WILL). One step either way.
     *
     * @return true when the save succeeds (total ≥ dc)
     */
    public boolean rollSave(Combatant target, AbilityScore stat, int dc, ResolutionResult result) {
        int roll = 1 + random.nextInt(10);
        int bonus = target.getStats().modifier(stat)
                + (target.getSavingThrowProficiencies().contains(stat)
                        ? statEngine.computeProficiencyBonus(target) : 0);
        bonus = statEngine.resolveModifiedStat(target, ModifiableStat.SAVE_BONUS, bonus);
        if (stat == AbilityScore.WILL) bonus = statEngine.resolveModifiedStat(target, ModifiableStat.WILL_SAVE, bonus);
        int total = roll + bonus;
        boolean success = total >= dc;
        result.addStep("save", target.getName() + " " + stat + " save: d10 " + roll + " + " + bonus
                + " = " + total + " vs DC " + dc + (success ? " — SAVED" : " — FAILED"), total, dc);
        return success;
    }

    /** Append another combatant's resolution under a name prefix ("Goblin 2:armor"). */
    public static void mergeSteps(ResolutionResult into, String prefix, ResolutionResult from) {
        from.getSteps().forEach(s ->
                into.addStep(prefix + ":" + s.rule(), s.note(), s.valueBefore(), s.valueAfter()));
        from.getEffectsTriggered().forEach(into::addTriggeredEffect);
    }

    /** A weapon/spell damage-type string ("fire") as the enum, or null when it isn't one ("?"). */
    public static DamageType damageTypeOf(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return DamageType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ---- Shared snapshot pieces ----

    /** Active-effect chips with threshold dormancy (demo feedback #15b) — same for both kinds. */
    public List<CombatSnapshot.EffectView> effectViews(Combatant c) {
        int stackThreshold = statEngine.computeStackThreshold(c);
        return c.getActiveEffects().stream()
                .map(e -> {
                    var def = gameData.getEffect(e.getEffectId());
                    boolean gated = EffectActivity.isThresholdGated(def);
                    return new CombatSnapshot.EffectView(
                            e.getEffectId(),
                            def != null && def.name() != null ? def.name() : e.getEffectId(),
                            e.getStacks(), e.getValue(), e.getRemainingRounds(),
                            EffectActivity.isActive(def, e, stackThreshold),
                            gated ? stackThreshold : null);
                })
                .toList();
    }

    /** HP-threshold conditions (injured / severelyInjured / downed) from condition terms. */
    public List<String> conditions(Combatant c) {
        int current = c.getHp().getCurrent();
        int max = statEngine.computeMaxHP(c);
        var conditions = new ArrayList<String>();
        if (current == 0) conditions.add("downed");
        var terms = gameData.getConditionTerms();
        if (terms != null && max > 0) {
            terms.fields().forEachRemaining(entry -> {
                double threshold = entry.getValue().path("threshold").asDouble(0);
                String comparison = entry.getValue().path("comparison").asText("below");
                if ("below".equals(comparison) && current < threshold * max) {
                    conditions.add(entry.getKey());
                }
            });
        }
        return conditions;
    }
}
