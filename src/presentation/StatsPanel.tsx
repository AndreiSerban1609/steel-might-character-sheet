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
  tempShieldPhysical: number;
  tempShieldMagical: number;
  currentAp: number;
  currentMana: number;
  /** Pinned derived stats; '' means "derive it normally". Kept as text so a
   *  half-typed value doesn't collapse to 0 and silently pin the stat. */
  overrides: Record<string, string>;
}

/** Mirrors the server's OverridableStat enum. */
const OVERRIDABLE: { key: string; label: string; hint: string }[] = [
  { key: 'maxHp', label: 'Max HP', hint: '(hpPerLevel + 3 × CON mod) × level' },
  { key: 'maxMana', label: 'Max Mana', hint: 'manaPerLevel × level + milestone bonuses' },
  { key: 'ac', label: 'AC', hint: 'armor base + DEX mod + shield' },
  { key: 'pa', label: 'PA', hint: 'body armor PA + scaling per level' },
  { key: 'ma', label: 'MA', hint: 'body armor MA + scaling per level' },
  { key: 'speed', label: 'Speed', hint: 'race movement, ft per AP' },
  { key: 'apRecovery', label: 'AP Recovery', hint: 'AP regained at the start of your turn' },
  { key: 'maxAp', label: 'Max AP', hint: 'AP ceiling' },
  { key: 'carryCapacity', label: 'Carry Capacity', hint: '10 + 2 × STR mod, in slots' },
];

function toInt(raw: string): number {
  const v = Number.parseInt(raw, 10);
  return Number.isNaN(v) ? 0 : v;
}

/** Snapshot overrides → text draft, so blank inputs read as "not pinned". */
function overridesToDraft(overrides: Record<string, number>): Record<string, string> {
  const draft: Record<string, string> = {};
  for (const { key } of OVERRIDABLE) {
    const pinned = overrides?.[key];
    draft[key] = pinned == null ? '' : String(pinned);
  }
  return draft;
}

function draftToOverrides(draft: Record<string, string>): Record<string, number> {
  const out: Record<string, number> = {};
  for (const { key } of OVERRIDABLE) {
    const raw = draft[key]?.trim();
    if (!raw) continue;
    const v = Number.parseInt(raw, 10);
    if (!Number.isNaN(v) && v >= 0) out[key] = v;
  }
  return out;
}

function sameOverrides(a: Record<string, number>, b: Record<string, number>): boolean {
  const keys = new Set([...Object.keys(a), ...Object.keys(b)]);
  for (const k of keys) if (a[k] !== b[k]) return false;
  return true;
}

export function StatsPanel() {
  const snapshot = useCharacterStore((s) => s.snapshot);
  const loading = useCharacterStore((s) => s.loading);
  const saving = useCharacterStore((s) => s.saving);
  const error = useCharacterStore((s) => s.error);
  const saveStats = useCharacterStore((s) => s.saveStats);
  const saveVitals = useCharacterStore((s) => s.saveVitals);
  const saveIdentity = useCharacterStore((s) => s.saveIdentity);
  const saveStatOverrides = useCharacterStore((s) => s.saveStatOverrides);
  const doGainXp = useCharacterStore((s) => s.doGainXp);
  const acting = useCharacterStore((s) => s.acting);
  // XP earned outside combat (2026-08-27): missions, discovery, items — typed in here.
  const [xpAmount, setXpAmount] = useState('100');
  const [xpReason, setXpReason] = useState('');

  const [draft, setDraft] = useState<SheetDraft | null>(null);

  if (loading && !snapshot) return <div className="panel-msg">Summoning character…</div>;
  if (!snapshot) return <div className="panel-msg panel-msg--error">{error ?? 'No character loaded.'}</div>;

  const editing = draft !== null;
  const profProfs = new Set(snapshot.savingThrowProficiencies);
  const pinned = (key: string) => snapshot.statOverrides?.[key] != null;

  function startEdit() {
    if (!snapshot) return;
    setDraft({
      name: snapshot.name,
      level: snapshot.level,
      stats: { ...snapshot.stats },
      currentHp: snapshot.hp.current,
      tempHp: snapshot.hp.temp,
      tempShieldPhysical: snapshot.tempShieldPhysical,
      tempShieldMagical: snapshot.tempShieldMagical,
      currentAp: snapshot.ap.current,
      currentMana: snapshot.mana.current,
      overrides: overridesToDraft(snapshot.statOverrides ?? {}),
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
      draft.tempShieldPhysical !== snapshot.tempShieldPhysical ||
      draft.tempShieldMagical !== snapshot.tempShieldMagical ||
      draft.currentAp !== snapshot.ap.current ||
      draft.currentMana !== snapshot.mana.current
    ) {
      await saveVitals({
        currentHp: draft.currentHp,
        tempHp: draft.tempHp,
        tempShieldPhysical: draft.tempShieldPhysical,
        tempShieldMagical: draft.tempShieldMagical,
        currentAp: draft.currentAp,
        currentMana: draft.currentMana,
      });
    }
    if (ABILITY_ORDER.some((a) => draft.stats[a] !== snapshot.stats[a])) {
      await saveStats(draft.stats);
    }
    // Last: pinning a max re-clamps current values, so it must not be undone by
    // a vitals write that ran against the old ceiling.
    const nextOverrides = draftToOverrides(draft.overrides);
    if (!sameOverrides(nextOverrides, snapshot.statOverrides ?? {})) {
      await saveStatOverrides(nextOverrides);
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
          pinned={pinned('maxHp')}
          edit={draft ? { value: draft.currentHp, onChange: (v) => setDraft({ ...draft, currentHp: v }) } : undefined}
        />
        <Vital
          label="Temp"
          title="Temporary HP — absorbs any damage, including true damage, before real HP"
          view={`${snapshot.hp.temp}`}
          edit={draft ? { value: draft.tempHp, onChange: (v) => setDraft({ ...draft, tempHp: v }) } : undefined}
        />
        <Vital
          label="Phys Shield"
          title="Temporary physical shield — absorbs physical damage only (crushing included); magic and true damage bypass it"
          view={`${snapshot.tempShieldPhysical}`}
          edit={
            draft
              ? {
                  value: draft.tempShieldPhysical,
                  onChange: (v) => setDraft({ ...draft, tempShieldPhysical: v }),
                }
              : undefined
          }
        />
        <Vital
          label="Mag Shield"
          title="Temporary magical shield — absorbs magical damage only; physical and true damage bypass it"
          view={`${snapshot.tempShieldMagical}`}
          edit={
            draft
              ? {
                  value: draft.tempShieldMagical,
                  onChange: (v) => setDraft({ ...draft, tempShieldMagical: v }),
                }
              : undefined
          }
        />
        <Vital
          label="Mana"
          view={`${snapshot.mana.current} / ${snapshot.mana.max}`}
          pinned={pinned('maxMana')}
          edit={draft ? { value: draft.currentMana, onChange: (v) => setDraft({ ...draft, currentMana: v }) } : undefined}
        />
        <Vital
          label="AP"
          view={`${snapshot.ap.current} / ${snapshot.ap.max}`}
          pinned={pinned('maxAp') || pinned('apRecovery')}
          edit={draft ? { value: draft.currentAp, onChange: (v) => setDraft({ ...draft, currentAp: v }) } : undefined}
        />
        <Vital label="AC" view={`${snapshot.ac}`} pinned={pinned('ac')} />
        <Vital label="PA" view={`${snapshot.pa}`} pinned={pinned('pa')} />
        <Vital label="MA" view={`${snapshot.ma}`} pinned={pinned('ma')} />
        <Vital label="Speed" view={`${snapshot.speed} ft`} pinned={pinned('speed')} />
        {snapshot.deathStacks > 0 && <Vital label="Death Stacks" view={`☠ ${snapshot.deathStacks}`} />}
        <Vital
          label="XP"
          view={snapshot.xpToNext != null ? `${snapshot.xp} / ${snapshot.xpToNext}` : `${snapshot.xp} (max level)`}
          accent={snapshot.levelAvailable}
          title={
            snapshot.levelAvailable
              ? `Level ${snapshot.level + 1} unlocked — run the level-up below`
              : snapshot.xpToNext != null
                ? `${snapshot.xpToNext - snapshot.xp} XP to level ${snapshot.level + 1}`
                : 'Level cap reached'
          }
        />
      </div>

      {!editing && (
        <div className="combat-form xp-add">
          <span className="combat-form-label">Add XP</span>
          <input
            className="combat-num"
            type="number"
            title="XP earned outside combat — missions, discovery, items. Negative = correction."
            value={xpAmount}
            onChange={(e) => setXpAmount(e.target.value)}
          />
          <input
            className="combat-text"
            type="text"
            maxLength={120}
            placeholder="for what? (optional — e.g. found the lost shrine)"
            value={xpReason}
            onChange={(e) => setXpReason(e.target.value)}
          />
          <button
            className="btn btn--ghost"
            title="Combat XP arrives on its own when the GM ends a fight; this is for everything else"
            onClick={() => {
              const v = Number.parseInt(xpAmount, 10);
              if (Number.isNaN(v) || v === 0) return;
              void doGainXp(v, xpReason.trim() || undefined);
              setXpReason('');
            }}
            disabled={acting}
          >
            Add
          </button>
        </div>
      )}

      {editing && draft && (
        <>
          <div className="section-bar">
            <h2 className="section-title">Manual overrides</h2>
          </div>
          <p className="override-note">
            Pin a derived stat when the character has something the rules engine doesn't model
            yet. Leave a field blank to derive it normally. Active effects still apply on top —
            a pinned AC of 18 still drops when something exposes you.
          </p>
          <div className="override-grid">
            {OVERRIDABLE.map(({ key, label, hint }) => (
              <label className="override-row" key={key}>
                <span className="override-label" title={`Normally: ${hint}`}>
                  {label}
                </span>
                <input
                  className="override-input"
                  type="number"
                  min={0}
                  placeholder="derived"
                  value={draft.overrides[key] ?? ''}
                  onChange={(e) =>
                    setDraft({
                      ...draft,
                      overrides: { ...draft.overrides, [key]: e.target.value },
                    })
                  }
                />
              </label>
            ))}
          </div>
        </>
      )}

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
  title,
  pinned,
}: {
  label: string;
  view: string;
  accent?: boolean;
  edit?: VitalEdit;
  title?: string;
  /** Marks a stat whose formula the GM has replaced with a literal value. */
  pinned?: boolean;
}) {
  return (
    <div className={accent ? 'vital vital--accent' : 'vital'} title={title}>
      <span className="vital-label">
        {label}
        {pinned && (
          <span className="vital-pin" title="Manually overridden — not derived from the rules">
            📌
          </span>
        )}
      </span>
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
