package com.steelmight.charactersheet.model;

import com.steelmight.charactersheet.gamedata.GameDataProvider;

import java.util.List;
import java.util.Map;

/**
 * Anything the combat pipelines can act on (ADR-001, Epic 2). Players ({@link GameCharacter})
 * and monsters flow through the SAME rules — armor, resistance, DoTs, threshold stacking,
 * death checks — so every rule is implemented exactly once ("derivation = enforcement").
 *
 * The surface is deliberately narrow: what the rules and engines actually read and write.
 * Player-only concerns (equipment, mana, decks, talents, spellcasting) stay on
 * {@code GameCharacter}; {@code StatDerivationEngine} reaches them through its single
 * authored-vs-derived dispatch, never the rules.
 */
public interface Combatant {

    /** Stable id in one namespace: players use their playerId, monsters {@code monster:{id}}. */
    String getCombatantId();

    String getName();

    /** The room whose encounter / audit log this combatant belongs to (null for room-less dev rows). */
    String getRoomName();

    /** Drives the negative-effect stack threshold ceil(level/2). */
    int getLevel();

    /** Flat initiative bonus on top of d20 + DEX mod (racial for players, authored for monsters; E8). */
    int getInitiativeBonus();

    Stats getStats();

    HitPoints getHp();

    /** Null for combatants without an AP economy (monsters, ruling E1) — callers must guard. */
    ActionPoints getAp();

    /** Base movement before effects (ft per AP). */
    int getSpeed();

    LifeStatus getLifeStatus();

    void setLifeStatus(LifeStatus status);

    List<ActiveEffect> getActiveEffects();

    void addEffect(ActiveEffect effect);

    void removeEffect(ActiveEffect effect);

    List<AbilityScore> getSavingThrowProficiencies();

    /**
     * A pinned value that replaces the FORMULA for a derived stat (effects still apply on
     * top), or null to derive it. Players: the GM's stat overrides (demo feedback #12).
     * Monsters: their authored stat block — a monster IS an override for every combat stat.
     */
    Integer overrideFor(OverridableStat stat);

    /**
     * Ruling E2: an authored negative-effect stack threshold (a boss that soaks more stacks
     * before an effect fires), or null for the standard ceil(level/2).
     */
    default Integer authoredStackThreshold() { return null; }

    /**
     * Innate damage-taken multipliers: races.json {@code damageTaken} for players,
     * authored resist / vulnerability / immunity for monsters.
     */
    Map<DamageType, Double> innateDamageTaken(GameDataProvider gameData);

    /** Label for the resolution-log step, e.g. {@code racial: elf}. */
    String innateDamageTakenSource();

    // ---- Death & dying (M2-D) ----

    /**
     * Players use the downed window + Medicine revival; monsters die at 0 HP (ruling E4,
     * with a per-template flag for named villains that should fight like players).
     */
    boolean usesDeathRules();

    Integer getDownedRoundsRemaining();

    void setDownedRoundsRemaining(Integer rounds);

    int getDownsThisCombat();

    void setDownsThisCombat(int downs);

    boolean isPendingDeathFight();

    void setPendingDeathFight(boolean pending);
}
