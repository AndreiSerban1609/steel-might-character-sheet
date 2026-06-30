import { useState } from 'react';
import { useCharacterStore } from '../application/characterStore';
import { ABILITY_ORDER, ABILITY_LABELS, titleCase } from '../domain/stats';
import type { AbilityScore } from '../platform/types';
import classesRaw from '../data/classes.json';
import classAbilitiesRaw from '../data/class-abilities.json';

interface PathInfo {
  id: string;
  name: string;
  classes: string[];
}

const PATHS = classesRaw as unknown as PathInfo[];
const CLASS_NAMES = classAbilitiesRaw as unknown as Record<string, { name: string }>;
const DEFAULT_STATS: Record<AbilityScore, number> = {
  STR: 15,
  DEX: 13,
  CON: 12,
  INT: 11,
  WIS: 10,
  WILL: 9,
  CHA: 8,
};

export function CreateView() {
  const roomName = useCharacterStore((s) => s.roomName);
  const email = useCharacterStore((s) => s.email);
  const saving = useCharacterStore((s) => s.saving);
  const error = useCharacterStore((s) => s.error);
  const createCharacter = useCharacterStore((s) => s.createCharacter);
  const back = useCharacterStore((s) => s.back);

  const [name, setName] = useState('');
  const [pathId, setPathId] = useState(PATHS[0]?.id ?? '');
  const [classId, setClassId] = useState(PATHS[0]?.classes[0] ?? '');
  const [level, setLevel] = useState(1);
  const [stats, setStats] = useState<Record<AbilityScore, number>>({ ...DEFAULT_STATS });

  const path = PATHS.find((p) => p.id === pathId);

  function onPathChange(id: string) {
    setPathId(id);
    setClassId(PATHS.find((p) => p.id === id)?.classes[0] ?? '');
  }

  const canCreate = name.trim().length > 0 && !!pathId && !!classId && !saving;

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
          <select value={classId} onChange={(e) => setClassId(e.target.value)}>
            {(path?.classes ?? []).map((cid) => (
              <option key={cid} value={cid}>
                {CLASS_NAMES[cid]?.name ?? titleCase(cid)}
              </option>
            ))}
          </select>
        </label>
        <label className="field field--narrow">
          <span>Level</span>
          <input
            type="number"
            min={1}
            max={20}
            value={level}
            onChange={(e) => setLevel(Number.parseInt(e.target.value, 10) || 1)}
          />
        </label>
      </div>

      <div className="form-stats">
        {ABILITY_ORDER.map((a) => (
          <label className="stat-field" key={a} title={ABILITY_LABELS[a]}>
            <span>{a}</span>
            <input
              type="number"
              min={1}
              max={40}
              value={stats[a]}
              onChange={(e) => setStats({ ...stats, [a]: Number.parseInt(e.target.value, 10) || 0 })}
            />
          </label>
        ))}
      </div>

      <div className="form-actions">
        <button className="btn btn--ghost" onClick={back} disabled={saving}>
          Back
        </button>
        <button
          className="btn btn--gold"
          onClick={() => void createCharacter({ name: name.trim(), pathId, classId, level, stats })}
          disabled={!canCreate}
        >
          {saving ? 'Creating…' : 'Create Character'}
        </button>
      </div>
    </section>
  );
}
