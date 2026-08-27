package com.steelmight.charactersheet.service;

import com.steelmight.charactersheet.dto.ActionResponse;
import com.steelmight.charactersheet.dto.ApplyEffectRequest;
import com.steelmight.charactersheet.dto.CombatSnapshot;
import com.steelmight.charactersheet.dto.DamageRequest;
import com.steelmight.charactersheet.dto.HealRequest;
import com.steelmight.charactersheet.dto.MonsterTemplateRequest;
import com.steelmight.charactersheet.dto.MonsterTemplateView;
import com.steelmight.charactersheet.dto.MonsterView;
import com.steelmight.charactersheet.dto.SpawnMonstersRequest;
import com.steelmight.charactersheet.engine.StatDerivationEngine;
import com.steelmight.charactersheet.engine.TurnTickService;
import com.steelmight.charactersheet.gamedata.GameDataProvider;
import com.steelmight.charactersheet.model.AbilityScore;
import com.steelmight.charactersheet.model.GameCharacter;
import com.steelmight.charactersheet.model.LifeStatus;
import com.steelmight.charactersheet.model.MonsterBlock;
import com.steelmight.charactersheet.model.MonsterInstance;
import com.steelmight.charactersheet.model.MonsterTemplate;
import com.steelmight.charactersheet.model.Stats;
import com.steelmight.charactersheet.repository.CharacterRepository;
import com.steelmight.charactersheet.repository.MonsterInstanceRepository;
import com.steelmight.charactersheet.repository.MonsterTemplateRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Monster templates (the GM's room library) and instances (monsters in the current fight),
 * plus the combat actions on an instance — which run through the SAME pipelines as a
 * player's via {@link CombatActionService} (ADR-001, Epic 2 Story 2.2).
 */
@Service
@Transactional
public class MonsterService {

    private static final int MAX_SPAWN = 20;

    private final MonsterTemplateRepository templates;
    private final MonsterInstanceRepository instances;
    private final CharacterRepository characters;
    private final CombatActionService combatActions;
    private final TurnTickService turnTicks;
    private final StatDerivationEngine statEngine;
    private final GameDataProvider gameData;
    private final EncounterService encounters;
    private final TurnFlowService turnFlow;
    private final AuditService audit;

    public MonsterService(MonsterTemplateRepository templates,
                          MonsterInstanceRepository instances,
                          CharacterRepository characters,
                          CombatActionService combatActions,
                          TurnTickService turnTicks,
                          StatDerivationEngine statEngine,
                          GameDataProvider gameData,
                          EncounterService encounters,
                          TurnFlowService turnFlow,
                          AuditService audit,
                          XpService xp) {
        this.xp = xp;
        this.templates = templates;
        this.instances = instances;
        this.characters = characters;
        this.combatActions = combatActions;
        this.turnTicks = turnTicks;
        this.statEngine = statEngine;
        this.gameData = gameData;
        this.encounters = encounters;
        this.turnFlow = turnFlow;
        this.audit = audit;
    }

    // ---- Templates (room library, ruling E9) ----

    @Transactional(readOnly = true)
    public List<MonsterTemplateView> listTemplates(String room) {
        return templates.findByRoomNameOrderByNameAsc(room).stream().map(this::toView).toList();
    }

    public MonsterTemplateView createTemplate(String room, MonsterTemplateRequest req) {
        var t = new MonsterTemplate(room, req.name().trim());
        applyRequest(t, req);
        return toView(templates.save(t));
    }

    public MonsterTemplateView updateTemplate(String room, Long id, MonsterTemplateRequest req) {
        var t = requireTemplate(room, id);
        t.setName(req.name().trim());
        applyRequest(t, req);
        return toView(templates.save(t));
    }

    /** Instances keep their own copy of the block, so a live fight survives the deletion. */
    public void deleteTemplate(String room, Long id) {
        templates.delete(requireTemplate(room, id));
    }

    /** JSON import: the export shape (a template view) minus id/room is exactly a request. */
    public List<MonsterTemplateView> importTemplates(String room, List<MonsterTemplateRequest> reqs) {
        if (reqs == null || reqs.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "nothing to import");
        }
        var created = new ArrayList<MonsterTemplateView>();
        for (var req : reqs) created.add(createTemplate(room, req));
        return created;
    }

    private void applyRequest(MonsterTemplate t, MonsterTemplateRequest req) {
        if (req.name() == null || req.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "template name is required");
        }
        if (req.level() < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "level must be at least 1");
        if (req.maxHp() < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "maxHp must be at least 1");
        if (req.damageTaken() != null) {
            for (var e : req.damageTaken().entrySet()) {
                if (e.getValue() == null || e.getValue() < 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "damageTaken multiplier for " + e.getKey() + " must be >= 0 (0 = immune)");
                }
            }
        }
        if (req.stackThreshold() != null && req.stackThreshold() < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "stackThreshold must be at least 1");
        }

        var block = t.getBlock() != null ? t.getBlock() : MonsterBlock.empty();
        block.setLevel(req.level());
        block.setMaxHp(req.maxHp());
        block.setAc(req.ac());
        block.setPa(req.pa());
        block.setMa(req.ma());
        block.setSpeed(req.speed());
        block.setMight(req.might());
        block.setInitiativeBonus(req.initiativeBonus());
        block.setStackThreshold(req.stackThreshold());
        block.setAbilitiesText(req.abilitiesText());
        var stats = new Stats(10, 10, 10, 10, 10, 10, 10);
        if (req.stats() != null) {
            req.stats().forEach((ability, score) -> {
                if (ability != null && score != null) stats.set(ability, score);
            });
        }
        block.setStats(stats);
        t.setBlock(block);

        t.getSavingThrowProficiencies().clear();
        if (req.savingThrowProficiencies() != null) {
            req.savingThrowProficiencies().stream().distinct().forEach(t.getSavingThrowProficiencies()::add);
        }
        t.getDamageTaken().clear();
        if (req.damageTaken() != null) t.getDamageTaken().putAll(req.damageTaken());
    }

    private MonsterTemplate requireTemplate(String room, Long id) {
        return templates.findByIdAndRoomName(id, room).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "no monster template " + id + " in room '" + room + "'"));
    }

    private MonsterTemplateView toView(MonsterTemplate t) {
        var b = t.getBlock();
        return new MonsterTemplateView(t.getId(), t.getRoomName(), t.getName(),
                b.getLevel(), b.getMaxHp(), b.getAc(), b.getPa(), b.getMa(), b.getSpeed(), b.getMight(),
                b.getInitiativeBonus(), b.getStats().toMap(),
                List.copyOf(t.getSavingThrowProficiencies()), Map.copyOf(t.getDamageTaken()),
                b.getAbilitiesText(), b.getStackThreshold());
    }

    // ---- Instances (the fight) ----

    @Transactional(readOnly = true)
    public List<MonsterView> list(String room) {
        return instances.findByRoomNameOrderByIdAsc(room).stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public MonsterView get(String room, Long id) {
        return toView(requireInstance(room, id));
    }

    /**
     * Stamp a template into the fight. Names auto-number per template: a lone first spawn
     * keeps the bare name ("Goblin"); anything after that, or a batch, is numbered from the
     * count already in the room ("Goblin 2", "Goblin 3" …).
     */
    public List<MonsterView> spawn(String room, SpawnMonstersRequest req) {
        var template = requireTemplate(room, req.templateId());
        int count = req.count() != null ? req.count() : 1;
        if (count < 1 || count > MAX_SPAWN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "count must be between 1 and " + MAX_SPAWN);
        }
        long existing = instances.countByRoomNameAndTemplateId(room, template.getId());
        var spawned = new ArrayList<MonsterView>();
        for (int i = 0; i < count; i++) {
            long ordinal = existing + i + 1;
            String name = (existing == 0 && count == 1) ? template.getName() : template.getName() + " " + ordinal;
            var m = instances.save(new MonsterInstance(template, name));
            // Reinforcements: a running encounter rolls them in where their initiative lands.
            encounters.join(room, m);
            audit.log(room, m.getCombatantId(), m.getDisplayName(), "spawn",
                    "Joined the fight (" + template.getName() + ", level " + m.getLevel() + ")");
            spawned.add(toView(m));
        }
        return spawned;
    }

    public void delete(String room, Long id) {
        var m = requireInstance(room, id);
        encounters.leave(room, m.getCombatantId());
        instances.delete(m);
        audit.log(room, m.getCombatantId(), m.getDisplayName(), "despawn", "Removed from the fight");
    }

    public void clear(String room) {
        for (var m : instances.findByRoomNameOrderByIdAsc(room)) {
            encounters.leave(room, m.getCombatantId());
        }
        instances.deleteByRoomName(room);
    }

    /**
     * Story 2.5 / N11c: the Death fight is an ordinary encounter against a full-resource
     * mirror of the character — a template built from their live derived block so the
     * GM can spawn it like any monster (and tweak it first).
     */
    public MonsterTemplateView templateFromCharacter(String room, String playerId) {
        GameCharacter c = characters.findById(playerId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Character not found"));
        if (c.getRoomName() != null && !room.equals(c.getRoomName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    c.getName() + " is not in room '" + room + "'");
        }
        var stats = c.getStats();
        var req = new MonsterTemplateRequest(
                c.getName() + " (death fight)", c.getLevel(),
                statEngine.computeMaxHP(c), statEngine.computeAC(c), statEngine.computePA(c), statEngine.computeMA(c),
                statEngine.computeSpeed(c), null, c.getBonusInitiative(),
                stats.toMap(), List.copyOf(c.getSavingThrowProficiencies()),
                gameData.getRaceDamageTaken(c.getRaceId()),
                "Death fight mirror of " + c.getName() + " at full resources (N11c). "
                        + "Class: " + c.getClassId() + ", race: " + c.getRaceId() + ".",
                null);
        return createTemplate(room, req);
    }

    public MonsterInstance requireInstance(String room, Long id) {
        return instances.findByIdAndRoomName(id, room).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "no monster " + id + " in room '" + room + "'"));
    }

    // ---- Combat actions (same pipelines as players) ----

    public ActionResponse<MonsterView> damage(String room, Long id, DamageRequest req) {
        var m = requireInstance(room, id);
        var out = combatActions.damage(m, req);
        return finish(m, "damage", out);
    }

    public ActionResponse<MonsterView> heal(String room, Long id, HealRequest req) {
        var m = requireInstance(room, id);
        var out = combatActions.heal(m, req);
        return finish(m, "heal", out);
    }

    public ActionResponse<MonsterView> applyEffect(String room, Long id, ApplyEffectRequest req) {
        var m = requireInstance(room, id);
        var out = combatActions.applyEffect(m, req);
        return finish(m, "apply-effect", out);
    }

    public ActionResponse<MonsterView> removeEffect(String room, Long id, String effectId) {
        var m = requireInstance(room, id);
        var out = combatActions.removeEffect(m, effectId);
        return finish(m, "remove-effect", out);
    }

    /**
     * DoT ticks + start-of-turn triggers (no AP, E1). Gated like a player's turn when the
     * room's encounter runs; in combat turns begin automatically, so this is the free-play
     * / GM path.
     */
    public ActionResponse<MonsterView> turnStart(String room, Long id) {
        var m = requireInstance(room, id);
        encounters.validateAndMarkTurnStart(m);
        var result = turnTicks.turnStart(m, false);
        persist(m);
        return new ActionResponse<>(result, toView(m));
    }

    /**
     * HoT ticks, end-of-turn triggers, duration expiry / threshold consumption — then the
     * order advances and the next combatant's turn (player or monster) auto-starts, its
     * ticks merged into this log. The GM ends monster turns.
     */
    public ActionResponse<MonsterView> turnEnd(String room, Long id) {
        var m = requireInstance(room, id);
        encounters.validateTurnEnd(m);
        var result = turnTicks.turnEnd(m);
        persist(m);
        turnFlow.autoStartNext(room, encounters.completeTurn(m), result);
        return new ActionResponse<>(result, toView(m));
    }

    private final XpService xp;

    /** Every monster write goes through here so a kill banks its XP exactly once (ruling 2026-08-27). */
    private void persist(MonsterInstance m) {
        xp.creditKill(m);
        instances.save(m);
    }

    private ActionResponse<MonsterView> finish(MonsterInstance m, String action, CombatActionService.Outcome out) {
        persist(m);
        audit.log(m.getRoomName(), m.getCombatantId(), m.getDisplayName(), action, out.auditSummary());
        return new ActionResponse<>(out.resolution(), toView(m));
    }

    public MonsterView toView(MonsterInstance m) {
        var b = m.getBlock();
        Map<AbilityScore, Integer> modifiers = new HashMap<>(m.getStats().modifierMap());
        return new MonsterView(
                m.getId(), m.getCombatantId(), m.getTemplateId(), m.getDisplayName(), m.getLevel(),
                new CombatSnapshot.HpView(m.getHp().getCurrent(), statEngine.computeMaxHP(m), m.getHp().getTemp()),
                statEngine.computeAC(m), statEngine.computePA(m), statEngine.computeMA(m),
                statEngine.computeSpeed(m), b.getMight(),
                m.getStats().toMap(), modifiers,
                m.getLifeStatus().name(),
                statEngine.computeStackThreshold(m),
                List.copyOf(m.getSavingThrowProficiencies()),
                Map.copyOf(m.getDamageTaken()),
                combatActions.effectViews(m),
                combatActions.conditions(m),
                b.getAbilitiesText());
    }
}
