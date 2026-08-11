import { useState } from 'react';
import { useCharacterStore } from '../application/characterStore';
import { ABILITY_ORDER, ABILITY_LABELS, formatModifier, titleCase } from '../domain/stats';
import { featInfo, talentInfo } from '../domain/progression';
import { HoverInfo } from './HoverInfo';
import type { AbilityScore } from '../platform/types';
import { LevelUpSection } from './LevelUpSection';

interface SheetDraft {
  name: string;
  level: number;
  stats: Record<AbilityScore, number>;
  currentHp: number;
  tempHp: number;
  currentAp: number;
  currentMana: number;
}

function toInt(raw: string): number {
  const v = Number.parseInt(raw, 10);
  return Number.isNaN(v) ? 0 : v;
}

export function StatsPanel() {
  const snapshot = useCharacterStore((s) => s.snapshot);
  const loading = useCharacterStore((s) => s.loading);
  const saving = useCharacterStore((s) => s.saving);
  const error = useCharacterStore((s) => s.error);
  const saveStats = useCharacterStore((s) => s.saveStats);
  const saveVitals = useCharacterStore((s) => s.saveVitals);
  const saveIdentity = useCharacterStore((s) => s.saveIdentity);

  const [draft, setDraft] = useState<SheetDraft | null>(null);

  if (loading && !snapshot) return <div className="panel-msg">Summoning character…</div>;
  if (!snapshot) return <div className="panel-msg panel-msg--error">{error ?? 'No character loaded.'}</div>;

  const editing = draft !== null;
  const profProfs = new Set(snapshot.savingThrowProficiencies);

  function startEdit() {
    if (!snapshot) return;
    setDraft({
      name: snapshot.name,
      level: snapshot.level,
      stats: { ...snapshot.stats },
      currentHp: snapshot.hp.current,
      tempHp: snapshot.hp.temp,
      currentAp: snapshot.ap.current,
      currentMana: snapshot.mana.current,
    });
  }

  async function save() {
    if (!draft || !snapshot) return;
    if (draft.name !== snapshot.name || draft.level !== snapshot.level) {
      await saveIdentity({ name: draft.name, level: draft.level });
    }
    if (
      draft.currentHp !== snapshot.hp.current ||
      draft.tempHp !== snapshot.hp.temp ||
      draft.currentAp !== snapshot.ap.current ||
      draft.currentMana !== snapshot.mana.current
    ) {
      await saveVitals({
        currentHp: draft.currentHp,
        tempHp: draft.tempHp,
        currentAp: draft.currentAp,
        currentMana: draft.currentMana,
      });
    }
    if (ABILITY_ORDER.some((a) => draft.stats[a] !== snapshot.stats[a])) {
      await saveStats(draft.stats);
    }
    if (!useCharacterStore.getState().error) setDraft(null);
  }

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
          <button className="btn btn--ghost" onClick={startEdit}>
            Edit
          </button>
        )}
      </div>

      <header className="char-header">
        {editing && draft ? (
          <input
            className="name-input"
            value={draft.name}
            onChange={(e) => setDraft({ ...draft, name: e.target.value })}
          />
        ) : (
          <h1 className="char-name">{snapshot.name}</h1>
        )}
        <p className="char-sub">
          Level{' '}
          {editing && draft ? (
            <input
              className="level-input"
              type="number"
              min={1}
              max={20}
              value={draft.level}
              onChange={(e) => setDraft({ ...draft, level: toInt(e.target.value) })}
            />
          ) : (
            snapshot.level
          )}{' '}
          · {titleCase(snapshot.pathId)} <span className="sep">/</span> {titleCase(snapshot.classId)}
        </p>
      </header>

      {error && editing && <p className="inline-error">{error}</p>}

      <div className="vitals">
        <Vital
          label="HP"
          accent
          view={`${snapshot.hp.current} / ${snapshot.hp.max}`}
          edit={draft ? { value: draft.currentHp, onChange: (v) => setDraft({ ...draft, currentHp: v }) } : undefined}
        />
        <Vital
          label="Temp"
          view={`${snapshot.hp.temp}`}
          edit={draft ? { value: draft.tempHp, onChange: (v) => setDraft({ ...draft, tempHp: v }) } : undefined}
        />
        <Vital
          label="Mana"
          view={`${snapshot.mana.current} / ${snapshot.mana.max}`}
          edit={draft ? { value: draft.currentMana, onChange: (v) => setDraft({ ...draft, currentMana: v }) } : undefined}
        />
        <Vital
          label="AP"
          view={`${snapshot.ap.current} / ${snapshot.ap.max}`}
          edit={draft ? { value: draft.currentAp, onChange: (v) => setDraft({ ...draft, currentAp: v }) } : undefined}
        />
        <Vital label="AC" view={`${snapshot.ac}`} />
        <Vital label="PA" view={`${snapshot.pa}`} />
        <Vital label="MA" view={`${snapshot.ma}`} />
        <Vital label="Speed" view={`${snapshot.speed} ft`} />
        {snapshot.deathStacks > 0 && <Vital label="Death Stacks" view={`☠ ${snapshot.deathStacks}`} />}
      </div>

      <div className="section-bar">
        <h2 className="section-title">Ability Scores</h2>
      </div>
      <div className="stat-grid">
        {ABILITY_ORDER.map((ability) => (
          <div className="stat-card" key={ability}>
            <div className="stat-abbr">
              {ability}
              {profProfs.has(ability) && (
                <span className="stat-prof" title="Saving throw proficiency">
                  ●
                </span>
              )}
            </div>
            {editing && draft ? (
              <input
                className="stat-input"
                type="number"
                min={1}
                max={40}
                value={draft.stats[ability]}
                onChange={(e) =>
                  setDraft({ ...draft, stats: { ...draft.stats, [ability]: toInt(e.target.value) } })
                }
              />
            ) : (
              <>
                <div className="stat-mod">{formatModifier(snapshot.modifiers[ability])}</div>
                <div className="stat-score">{snapshot.stats[ability]}</div>
              </>
            )}
            <div className="stat-name">{ABILITY_LABELS[ability]}</div>
          </div>
        ))}
      </div>

      {!editing && (snapshot.talents.length > 0 || snapshot.specFeats.length > 0) && (
        <>
          <div className="section-bar">
            <h2 className="section-title">Talents &amp; Feats</h2>
          </div>
          <div className="talent-chips">
            {snapshot.talents.map((id) => {
              const t = talentInfo(snapshot.classId, snapshot.specializationId, id);
              return (
                <HoverInfo info={t?.description} key={id}>
                  <span className="talent-chip">{t?.name ?? titleCase(id)}</span>
                </HoverInfo>
              );
            })}
            {snapshot.specFeats.map((slot) => {
              const f = featInfo(snapshot.classId, snapshot.specializationId, slot);
              return (
                <HoverInfo info={f?.description} key={slot}>
                  <span className="talent-chip talent-chip--feat">
                    {f?.name ?? titleCase(slot)}
                  </span>
                </HoverInfo>
              );
            })}
          </div>
        </>
      )}

      {!editing && <LevelUpSection />}
    </>
  );
}

interface VitalEdit {
  value: number;
  onChange: (v: number) => void;
}

function Vital({
  label,
  view,
  accent,
  edit,
}: {
  label: string;
  view: string;
  accent?: boolean;
  edit?: VitalEdit;
}) {
  return (
    <div className={accent ? 'vital vital--accent' : 'vital'}>
      <span className="vital-label">{label}</span>
      {edit ? (
        <input
          className="vital-input"
          type="number"
          min={0}
          value={edit.value}
          onChange={(e) => edit.onChange(toInt(e.target.value))}
        />
      ) : (
        <span className="vital-value">{view}</span>
      )}
    </div>
  );
}
