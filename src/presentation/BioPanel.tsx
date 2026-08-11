import { useEffect, useState } from 'react';
import { useCharacterStore } from '../application/characterStore';
import { titleCase } from '../domain/stats';
import type { BioPatch, BioSnapshot } from '../platform/types';
import racesRaw from '../data/races.json';

const RACE_NAME = new Map(
  (racesRaw as unknown as { races: { id: string; name: string | null }[] }).races.map((r) => [
    r.id,
    r.name,
  ]),
);

interface Draft {
  alignment: string;
  background: string;
  age: string;
  heightCm: string;
  weightKg: string;
  eyeColor: string;
  hair: string;
  skin: string;
  personalityTraits: string;
  ideals: string;
  bonds: string;
  flaws: string;
  backstory: string;
  allies: string;
  organizations: string;
  titles: string;
  notes: string;
}

const str = (v: string | null | undefined) => v ?? '';
const num = (v: number | null | undefined) => (v == null ? '' : String(v));

function toDraft(b: BioSnapshot): Draft {
  const a = b.appearance;
  return {
    alignment: str(b.alignment),
    background: str(b.background),
    age: num(a?.age),
    heightCm: num(a?.heightCm),
    weightKg: num(a?.weightKg),
    eyeColor: str(a?.eyeColor),
    hair: str(a?.hair),
    skin: str(a?.skin),
    personalityTraits: str(b.personalityTraits),
    ideals: str(b.ideals),
    bonds: str(b.bonds),
    flaws: str(b.flaws),
    backstory: str(b.backstory),
    allies: str(b.allies),
    organizations: str(b.organizations),
    titles: str(b.titles),
    notes: str(b.notes),
  };
}

function numOrNull(v: string): number | null {
  const t = v.trim();
  if (!t) return null;
  const n = Number.parseInt(t, 10);
  return Number.isNaN(n) ? null : n;
}

function toPatch(d: Draft): BioPatch {
  return {
    alignment: d.alignment,
    background: d.background,
    appearance: {
      age: numOrNull(d.age),
      heightCm: numOrNull(d.heightCm),
      weightKg: numOrNull(d.weightKg),
      eyeColor: d.eyeColor.trim() || null,
      hair: d.hair.trim() || null,
      skin: d.skin.trim() || null,
    },
    personalityTraits: d.personalityTraits,
    ideals: d.ideals,
    bonds: d.bonds,
    flaws: d.flaws,
    backstory: d.backstory,
    allies: d.allies,
    organizations: d.organizations,
    titles: d.titles,
    notes: d.notes,
  };
}

const TEXT_FIELDS: { key: keyof Draft; label: string; rows: number }[] = [
  { key: 'personalityTraits', label: 'Personality traits', rows: 2 },
  { key: 'ideals', label: 'Ideals', rows: 2 },
  { key: 'bonds', label: 'Bonds', rows: 2 },
  { key: 'flaws', label: 'Flaws', rows: 2 },
  { key: 'backstory', label: 'Backstory', rows: 6 },
  { key: 'allies', label: 'Allies', rows: 3 },
  { key: 'organizations', label: 'Organizations & allegiances', rows: 3 },
  { key: 'titles', label: 'Titles', rows: 2 },
  { key: 'notes', label: 'Notes', rows: 3 },
];

function ReadField({ label, value }: { label: string; value: string | null }) {
  return (
    <div className="bio-field">
      <div className="bio-label">{label}</div>
      {value && value.trim() ? (
        <div className="bio-value">{value}</div>
      ) : (
        <div className="bio-value bio-value--empty">—</div>
      )}
    </div>
  );
}

export function BioPanel() {
  const bio = useCharacterStore((s) => s.bio);
  const saving = useCharacterStore((s) => s.saving);
  const error = useCharacterStore((s) => s.error);
  const loadBio = useCharacterStore((s) => s.loadBio);
  const saveBio = useCharacterStore((s) => s.saveBio);

  const [draft, setDraft] = useState<Draft | null>(null);

  useEffect(() => {
    void loadBio();
  }, [loadBio]);

  if (!bio) return <div className="panel-msg">Loading bio…</div>;

  const editing = draft !== null;
  const a = bio.appearance;

  function set(patch: Partial<Draft>) {
    setDraft((d) => (d ? { ...d, ...patch } : d));
  }

  async function save() {
    if (!draft) return;
    await saveBio(toPatch(draft));
    if (!useCharacterStore.getState().error) setDraft(null);
  }

  const appearanceLine = [
    a?.age != null ? `Age ${a.age}` : null,
    a?.heightCm != null ? `${a.heightCm} cm` : null,
    a?.weightKg != null ? `${a.weightKg} kg` : null,
    a?.eyeColor ? `${a.eyeColor} eyes` : null,
    a?.hair ? `${a.hair} hair` : null,
    a?.skin ? `${a.skin} skin` : null,
  ].filter(Boolean);

  return (
    <>
      <div className="sheet-actionbar">
        {editing ? (
          <div className="edit-actions">
            <button className="btn btn--ghost" onClick={() => setDraft(null)} disabled={saving}>
              Cancel
            </button>
            <button className="btn btn--gold" onClick={save} disabled={saving}>
              {saving ? 'Saving…' : 'Save'}
            </button>
          </div>
        ) : (
          <button className="btn btn--ghost" onClick={() => setDraft(toDraft(bio))}>
            Edit
          </button>
        )}
      </div>

      {error && <p className="inline-error">{error}</p>}

      <div className="bio-grid">
        <ReadField
          label="Race"
          value={bio.raceId ? RACE_NAME.get(bio.raceId) ?? titleCase(bio.raceId) : null}
        />
        <ReadField
          label="Path / Class"
          value={
            bio.pathId && bio.classId
              ? `${titleCase(bio.pathId)} / ${titleCase(bio.classId)}` +
                (bio.specializationId ? ` (${titleCase(bio.specializationId)})` : '')
              : null
          }
        />
      </div>

      <div className="bio-grid">
        <ReadField label="Alignment" value={editing ? null : bio.alignment} />
        {!editing && <ReadField label="Background" value={bio.background} />}
      </div>

      {editing && draft ? (
        <>
          <div className="bio-row2">
            <label className="bio-edit-field">
              <span className="bio-label">Alignment</span>
              <input value={draft.alignment} onChange={(e) => set({ alignment: e.target.value })} />
            </label>
            <label className="bio-edit-field">
              <span className="bio-label">Background</span>
              <input value={draft.background} onChange={(e) => set({ background: e.target.value })} />
            </label>
          </div>

          <div className="bio-label bio-section-title">Appearance</div>
          <div className="bio-appearance-edit">
            <label>
              <span>Age</span>
              <input type="number" value={draft.age} onChange={(e) => set({ age: e.target.value })} />
            </label>
            <label>
              <span>Height (cm)</span>
              <input type="number" value={draft.heightCm} onChange={(e) => set({ heightCm: e.target.value })} />
            </label>
            <label>
              <span>Weight (kg)</span>
              <input type="number" value={draft.weightKg} onChange={(e) => set({ weightKg: e.target.value })} />
            </label>
            <label>
              <span>Eyes</span>
              <input value={draft.eyeColor} onChange={(e) => set({ eyeColor: e.target.value })} />
            </label>
            <label>
              <span>Hair</span>
              <input value={draft.hair} onChange={(e) => set({ hair: e.target.value })} />
            </label>
            <label>
              <span>Skin</span>
              <input value={draft.skin} onChange={(e) => set({ skin: e.target.value })} />
            </label>
          </div>

          {TEXT_FIELDS.map((f) => (
            <label className="bio-edit-field" key={f.key}>
              <span className="bio-label">{f.label}</span>
              <textarea
                rows={f.rows}
                value={draft[f.key]}
                onChange={(e) => set({ [f.key]: e.target.value } as Partial<Draft>)}
              />
            </label>
          ))}
        </>
      ) : (
        <>
          <ReadField label="Appearance" value={appearanceLine.length ? appearanceLine.join(' · ') : null} />
          {TEXT_FIELDS.map((f) => (
            <ReadField key={f.key} label={f.label} value={bio[f.key as keyof BioSnapshot] as string | null} />
          ))}
        </>
      )}
    </>
  );
}
