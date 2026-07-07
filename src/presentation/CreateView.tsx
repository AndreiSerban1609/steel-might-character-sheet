import { useMemo, useState } from 'react';
import { useCharacterStore } from '../application/characterStore';
import { ABILITY_ORDER, ABILITY_LABELS, titleCase } from '../domain/stats';
import type { AbilityScore } from '../platform/types';
import { casterTypeOf, spellsForClass } from '../domain/spellCatalog';
import classesRaw from '../data/classes.json';
import classAbilitiesRaw from '../data/class-abilities.json';
import racesRaw from '../data/races.json';
import skillsRaw from '../data/skills.json';
import specializationsRaw from '../data/specializations.json';
import characterCreationRaw from '../data/character-creation.json';

interface PathInfo {
  id: string;
  name: string;
  classes: string[];
}

interface SpecInfo {
  name: string;
  lore?: string;
}

const PATHS = classesRaw as unknown as PathInfo[];
const CLASS_NAMES = classAbilitiesRaw as unknown as Record<string, { name: string }>;
const RACES = (racesRaw as unknown as { races: { id: string; name: string }[] }).races;
const SKILLS = skillsRaw as unknown as { id: string; name: string }[];
const SPECS = specializationsRaw as unknown as Record<string, SpecInfo[]>;
const CREATION = characterCreationRaw as unknown as {
  statArray: number[];
  bonusPoints: number;
  maxBonusPerStat: number;
  defaultSkillProficiencies: number;
};

function slug(name: string): string {
  return name.trim().toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '');
}

const DEFAULT_ASSIGNMENT: Record<AbilityScore, number> = {
  STR: 15, DEX: 13, CON: 12, INT: 11, WIS: 10, WILL: 9, CHA: 8,
};

export function CreateView() {
  const roomName = useCharacterStore((s) => s.roomName);
  const email = useCharacterStore((s) => s.email);
  const saving = useCharacterStore((s) => s.saving);
  const error = useCharacterStore((s) => s.error);
  const createCharacter = useCharacterStore((s) => s.createCharacter);
  const back = useCharacterStore((s) => s.back);

  const [name, setName] = useState('');
  const [raceId, setRaceId] = useState(RACES[0]?.id ?? '');
  const [pathId, setPathId] = useState(PATHS[0]?.id ?? '');
  const [classId, setClassId] = useState(PATHS[0]?.classes[0] ?? '');
  const [specId, setSpecId] = useState(() => slug(SPECS[PATHS[0]?.classes[0] ?? '']?.[0]?.name ?? ''));
  const [stats, setStats] = useState<Record<AbilityScore, number>>({ ...DEFAULT_ASSIGNMENT });
  const [bonus, setBonus] = useState<Record<AbilityScore, number>>({
    STR: 0, DEX: 0, CON: 0, INT: 0, WIS: 0, WILL: 0, CHA: 0,
  });
  const [skills, setSkills] = useState<string[]>([]);
  const [spellId, setSpellId] = useState('');

  const path = PATHS.find((p) => p.id === pathId);
  const classSpecs = SPECS[classId] ?? [];
  const casterType = casterTypeOf(classId);
  const levelOneSpells = useMemo(
    () => spellsForClass(classId).filter((s) => s.level === 1),
    [classId],
  );

  function onPathChange(id: string) {
    setPathId(id);
    onClassChange(PATHS.find((p) => p.id === id)?.classes[0] ?? '');
  }

  function onClassChange(id: string) {
    setClassId(id);
    setSpecId(slug(SPECS[id]?.[0]?.name ?? ''));
    setSpellId('');
  }

  function toggleSkill(id: string) {
    setSkills((cur) =>
      cur.includes(id)
        ? cur.filter((s) => s !== id)
        : cur.length < CREATION.defaultSkillProficiencies
          ? [...cur, id]
          : cur,
    );
  }

  // client-side sanity mirrors of the server's M6-A validation
  const isPermutation = useMemo(() => {
    const sorted = ABILITY_ORDER.map((a) => stats[a]).sort((a, b) => b - a);
    const expected = [...CREATION.statArray].sort((a, b) => b - a);
    return sorted.length === expected.length && sorted.every((v, i) => v === expected[i]);
  }, [stats]);
  const bonusSum = ABILITY_ORDER.reduce((sum, a) => sum + bonus[a], 0);
  const needsSpell = casterType !== 'none';

  const canCreate =
    name.trim().length > 0 &&
    !!raceId && !!pathId && !!classId &&
    (classSpecs.length === 0 || !!specId) &&
    isPermutation &&
    bonusSum === CREATION.bonusPoints &&
    skills.length === CREATION.defaultSkillProficiencies &&
    (!needsSpell || !!spellId) &&
    !saving;

  function submit() {
    const bonusAllocation: Partial<Record<AbilityScore, number>> = {};
    for (const a of ABILITY_ORDER) {
      if (bonus[a] > 0) bonusAllocation[a] = bonus[a];
    }
    void createCharacter({
      name: name.trim(),
      raceId,
      pathId,
      classId,
      specializationId: classSpecs.length > 0 ? specId : undefined,
      level: 1,
      stats,
      bonusAllocation,
      skillProficiencies: skills,
      knownSpells: needsSpell ? [spellId] : [],
    });
  }

  return (
    <section className="form-view">
      <header className="form-header">
        <h1 className="form-title">Create your character</h1>
        <p className="form-sub">
          {roomName} · {email}
        </p>
      </header>

      {error && <p className="inline-error">{error}</p>}

      <label className="field">
        <span>Name</span>
        <input value={name} onChange={(e) => setName(e.target.value)} placeholder="Character name" />
      </label>

      <div className="field-row">
        <label className="field">
          <span>Race</span>
          <select value={raceId} onChange={(e) => setRaceId(e.target.value)}>
            {RACES.map((r) => (
              <option key={r.id} value={r.id}>
                {r.name ?? titleCase(r.id)}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          <span>Path</span>
          <select value={pathId} onChange={(e) => onPathChange(e.target.value)}>
            {PATHS.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          <span>Class</span>
          <select value={classId} onChange={(e) => onClassChange(e.target.value)}>
            {(path?.classes ?? []).map((cid) => (
              <option key={cid} value={cid}>
                {CLASS_NAMES[cid]?.name ?? titleCase(cid)}
              </option>
            ))}
          </select>
        </label>
      </div>

      {classSpecs.length > 0 && (
        <label className="field">
          <span>Specialization (grants its starting talent)</span>
          <select value={specId} onChange={(e) => setSpecId(e.target.value)}>
            {classSpecs.map((s) => (
              <option key={slug(s.name)} value={slug(s.name)}>
                {s.name}
              </option>
            ))}
          </select>
        </label>
      )}

      <div className="create-section">
        <h3 className="combat-section-title">
          Assign the standard array {JSON.stringify(CREATION.statArray)}
          {!isPermutation && <span className="inline-error"> — each value used exactly once</span>}
        </h3>
        <div className="form-stats">
          {ABILITY_ORDER.map((a) => (
            <label className="stat-field" key={a} title={ABILITY_LABELS[a]}>
              <span>{a}</span>
              <select
                value={stats[a]}
                onChange={(e) => setStats({ ...stats, [a]: Number.parseInt(e.target.value, 10) })}
              >
                {CREATION.statArray.map((v) => (
                  <option key={v} value={v}>
                    {v}
                  </option>
                ))}
              </select>
            </label>
          ))}
        </div>
      </div>

      <div className="create-section">
        <h3 className="combat-section-title">
          Bonus points: {bonusSum} / {CREATION.bonusPoints} (max {CREATION.maxBonusPerStat} per stat)
        </h3>
        <div className="form-stats">
          {ABILITY_ORDER.map((a) => (
            <label className="stat-field" key={a} title={`Bonus for ${ABILITY_LABELS[a]}`}>
              <span>{a}</span>
              <input
                type="number"
                min={0}
                max={CREATION.maxBonusPerStat}
                value={bonus[a]}
                onChange={(e) =>
                  setBonus({
                    ...bonus,
                    [a]: Math.max(0, Math.min(CREATION.maxBonusPerStat,
                        Number.parseInt(e.target.value, 10) || 0)),
                  })
                }
              />
            </label>
          ))}
        </div>
      </div>

      <div className="create-section">
        <h3 className="combat-section-title">
          Skill proficiencies: {skills.length} / {CREATION.defaultSkillProficiencies}
        </h3>
        <div className="create-skills">
          {SKILLS.map((s) => (
            <label className="spell-self" key={s.id}>
              <input
                type="checkbox"
                checked={skills.includes(s.id)}
                onChange={() => toggleSkill(s.id)}
              />
              {s.name ?? titleCase(s.id)}
            </label>
          ))}
        </div>
      </div>

      {needsSpell && (
        <label className="field">
          <span>Starting spell ({casterType} caster — 1 known spell)</span>
          <select value={spellId} onChange={(e) => setSpellId(e.target.value)}>
            <option value="">Choose a level-1 spell…</option>
            {levelOneSpells.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </select>
        </label>
      )}

      <div className="form-actions">
        <button className="btn btn--ghost" onClick={back} disabled={saving}>
          Back
        </button>
        <button className="btn btn--gold" onClick={submit} disabled={!canCreate}>
          {saving ? 'Creating…' : 'Create Character'}
        </button>
      </div>
    </section>
  );
}
