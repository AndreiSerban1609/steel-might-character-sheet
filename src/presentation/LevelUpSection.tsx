import { useEffect, useMemo, useState } from 'react';
import { useCharacterStore } from '../application/characterStore';
import type { AbilityScore } from '../platform/types';
import { ABILITY_ORDER } from '../domain/stats';
import { maxSpellLevel, spellsForClass } from '../domain/spellCatalog';
import {
  BONUS_POINTS,
  FEAT_LEVELS,
  MAX_BONUS_PER_STAT,
  SPEC_TALENT_LEVEL,
  TALENT_LEVELS,
  featOptions,
  isStatIncreaseLevel,
  missingSpecTalents,
  spellsToLearn,
  talentPool,
} from '../domain/progression';
import { ResolutionLog } from './ResolutionLog';

/** Collapsible level-up wizard on the Stats tab: shows what the next level
 *  grants, collects the required choices, and submits POST /actions/level-up. */
export function LevelUpSection() {
  const snapshot = useCharacterStore((s) => s.snapshot);
  const spellbook = useCharacterStore((s) => s.spellbook);
  const acting = useCharacterStore((s) => s.acting);
  const error = useCharacterStore((s) => s.error);
  const lastResolution = useCharacterStore((s) => s.lastResolution);
  const loadSpellbook = useCharacterStore((s) => s.loadSpellbook);
  const doLevelUp = useCharacterStore((s) => s.doLevelUp);
  const clearResolution = useCharacterStore((s) => s.clearResolution);

  const [open, setOpen] = useState(false);
  const [bonus, setBonus] = useState<Record<AbilityScore, number>>({
    STR: 0, DEX: 0, CON: 0, INT: 0, WIS: 0, WILL: 0, CHA: 0,
  });
  const [spellPicks, setSpellPicks] = useState<string[]>([]);
  const [talentId, setTalentId] = useState('');
  const [featId, setFeatId] = useState('');

  const newLevel = (snapshot?.level ?? 0) + 1;
  const spellsNeeded = snapshot ? spellsToLearn(snapshot.classId, newLevel) : 0;

  useEffect(() => {
    // the spell picker filters out already-known spells
    if (open && spellsNeeded > 0 && !spellbook) void loadSpellbook();
  }, [open, spellsNeeded, spellbook, loadSpellbook]);

  const learnable = useMemo(() => {
    if (!snapshot || spellsNeeded === 0) return [];
    const known = new Set([...(spellbook?.knownSpells ?? []), ...(spellbook?.preparedSpells ?? [])]);
    const access = maxSpellLevel(snapshot.classId, newLevel);
    return spellsForClass(snapshot.classId).filter((s) => s.level <= access && !known.has(s.id));
  }, [snapshot, spellbook, newLevel, spellsNeeded]);

  if (!snapshot) return null;
  if (snapshot.level >= 20) return null;

  const statLevel = isStatIncreaseLevel(newLevel);
  const talentLevel = TALENT_LEVELS.includes(newLevel);
  const featLevel = FEAT_LEVELS.includes(newLevel);
  const missingSpec =
    newLevel === SPEC_TALENT_LEVEL
      ? missingSpecTalents(snapshot.classId, snapshot.specializationId, snapshot.talents)
      : [];
  const needsSpecPick = missingSpec.length === 2; // one owned → server auto-grants

  const talents = talentLevel
    ? talentPool(snapshot.classId, snapshot.specializationId, newLevel, snapshot.talents)
    : needsSpecPick
      ? missingSpec
      : [];
  const feats = featLevel
    ? featOptions(snapshot.classId, snapshot.specializationId, snapshot.specFeats)
    : [];

  const bonusSum = ABILITY_ORDER.reduce((sum, a) => sum + bonus[a], 0);
  const ready =
    (!statLevel || bonusSum === BONUS_POINTS) &&
    spellPicks.filter(Boolean).length === spellsNeeded &&
    (!(talentLevel || needsSpecPick) || !!talentId) &&
    (!featLevel || feats.length === 0 || !!featId);

  function submit() {
    const statIncreases: Partial<Record<AbilityScore, number>> = {};
    for (const a of ABILITY_ORDER) {
      if (bonus[a] > 0) statIncreases[a] = bonus[a];
    }
    void doLevelUp({
      statIncreases: statLevel ? statIncreases : undefined,
      newSpells: spellsNeeded > 0 ? spellPicks.filter(Boolean) : undefined,
      talentId: talentId || undefined,
      featId: featId || undefined,
    }).then(() => {
      if (!useCharacterStore.getState().error) {
        setOpen(false);
        setBonus({ STR: 0, DEX: 0, CON: 0, INT: 0, WIS: 0, WILL: 0, CHA: 0 });
        setSpellPicks([]);
        setTalentId('');
        setFeatId('');
      }
    });
  }

  if (!open) {
    return (
      <div className="levelup">
        <button
          className={snapshot.levelAvailable ? 'btn btn--gold' : 'btn btn--ghost'}
          title={
            snapshot.levelAvailable
              ? 'Your XP covers this level'
              : snapshot.xpToNext != null
                ? `${snapshot.xpToNext - snapshot.xp} XP short — the GM can still level you by hand`
                : 'Level cap reached'
          }
          onClick={() => setOpen(true)}
        >
          Level up → {newLevel}
          {snapshot.levelAvailable ? ' ✦ unlocked' : ''}
        </button>
        {lastResolution?.payload?.newLevel != null && (
          <ResolutionLog resolution={lastResolution} onClose={clearResolution} />
        )}
      </div>
    );
  }

  return (
    <div className="levelup levelup--open">
      <div className="combat-log-head">
        <h3 className="combat-section-title">Level {snapshot.level} → {newLevel}</h3>
        <button className="btn btn--ghost" onClick={() => setOpen(false)}>
          Cancel
        </button>
      </div>

      {error && <p className="inline-error">{error}</p>}

      {statLevel && (
        <div className="create-section">
          <h4 className="combat-section-title">
            Stat increases: {bonusSum} / {BONUS_POINTS} (max {MAX_BONUS_PER_STAT} per stat)
          </h4>
          <div className="form-stats">
            {ABILITY_ORDER.map((a) => (
              <label className="stat-field" key={a}>
                <span>{a}</span>
                <input
                  type="number"
                  min={0}
                  max={MAX_BONUS_PER_STAT}
                  value={bonus[a]}
                  onChange={(e) =>
                    setBonus({
                      ...bonus,
                      [a]: Math.max(0, Math.min(MAX_BONUS_PER_STAT, Number.parseInt(e.target.value, 10) || 0)),
                    })
                  }
                />
              </label>
            ))}
          </div>
        </div>
      )}

      {spellsNeeded > 0 && (
        <div className="create-section">
          <h4 className="combat-section-title">
            New spell{spellsNeeded === 1 ? '' : 's'} ({spellsNeeded})
          </h4>
          {Array.from({ length: spellsNeeded }, (_, i) => (
            <select
              key={i}
              className="levelup-spell"
              value={spellPicks[i] ?? ''}
              onChange={(e) => {
                const next = [...spellPicks];
                next[i] = e.target.value;
                setSpellPicks(next);
              }}
            >
              <option value="">Choose a spell…</option>
              {learnable
                .filter((s) => s.id === spellPicks[i] || !spellPicks.includes(s.id))
                .map((s) => (
                  <option key={s.id} value={s.id}>
                    L{s.level} · {s.name}
                  </option>
                ))}
            </select>
          ))}
        </div>
      )}

      {(talentLevel || needsSpecPick) && (
        <div className="create-section">
          <h4 className="combat-section-title">
            {needsSpecPick ? 'Specialization talent (level 17)' : 'Talent pick'}
          </h4>
          <select value={talentId} onChange={(e) => setTalentId(e.target.value)}>
            <option value="">Choose a talent…</option>
            {talents.map((t) => (
              <option key={t.id} value={t.id} title={t.description}>
                {t.name}
                {t.fromSpec ? ' (specialization)' : ''}
              </option>
            ))}
          </select>
        </div>
      )}
      {newLevel === SPEC_TALENT_LEVEL && missingSpec.length === 1 && (
        <p className="spell-prep-count">
          The remaining specialization talent ({missingSpec[0].name}) is granted automatically.
        </p>
      )}

      {featLevel && feats.length > 0 && (
        <div className="create-section">
          <h4 className="combat-section-title">Specialization feat</h4>
          <select value={featId} onChange={(e) => setFeatId(e.target.value)}>
            <option value="">Choose a feat…</option>
            {feats.map((f) => (
              <option key={f.slot} value={f.slot} title={f.description}>
                {f.name} ({f.slot})
              </option>
            ))}
          </select>
        </div>
      )}

      <div className="form-actions">
        <button className="btn btn--gold" onClick={submit} disabled={acting || !ready}>
          {acting ? 'Leveling…' : `Confirm level ${newLevel}`}
        </button>
      </div>
    </div>
  );
}
