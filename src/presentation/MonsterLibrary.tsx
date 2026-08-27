import { useEffect, useState } from 'react';
import { useCharacterStore } from '../application/characterStore';
import type { AbilityScore, DamageTypeId, MonsterTemplateRequest, MonsterTemplateView } from '../platform/types';
import { DAMAGE_TYPE_OPTIONS } from '../domain/combatCatalog';

const ABILITIES: AbilityScore[] = ['STR', 'DEX', 'CON', 'INT', 'WIS', 'WILL', 'CHA'];

function blankTemplate(): MonsterTemplateRequest {
  return {
    name: '',
    level: 1,
    maxHp: 20,
    ac: 10,
    pa: 0,
    ma: 0,
    speed: 30,
    might: null,
    initiativeBonus: 0,
    stats: { STR: 10, DEX: 10, CON: 10, INT: 10, WIS: 10, WILL: 10, CHA: 10 },
    savingThrowProficiencies: [],
    damageTaken: {},
    abilitiesText: '',
    stackThreshold: null,
  };
}

/** The export shape: a template view minus its identity is exactly an import request (E9). */
function toRequest(t: MonsterTemplateView): MonsterTemplateRequest {
  const { id: _id, roomName: _room, ...rest } = t;
  return rest;
}

function num(v: string, fallback: number): number {
  const n = Number.parseInt(v, 10);
  return Number.isNaN(n) ? fallback : n;
}

/**
 * The GM's room library of monster stat blocks (ADR-001, ruling E9: per room, with JSON
 * export/import so a block travels between rooms). Also home of the Death-fight clone
 * (Story 2.5): mirror a character into a template and spawn it like any monster.
 */
export function MonsterLibrary() {
  const templates = useCharacterStore((s) => s.monsterTemplates);
  const roster = useCharacterStore((s) => s.roster);
  const saving = useCharacterStore((s) => s.saving);
  const acting = useCharacterStore((s) => s.acting);
  const loadMonsterTemplates = useCharacterStore((s) => s.loadMonsterTemplates);
  const saveMonsterTemplate = useCharacterStore((s) => s.saveMonsterTemplate);
  const deleteMonsterTemplate = useCharacterStore((s) => s.deleteMonsterTemplate);
  const importMonsterTemplates = useCharacterStore((s) => s.importMonsterTemplates);
  const cloneCharacterAsTemplate = useCharacterStore((s) => s.cloneCharacterAsTemplate);
  const spawnMonsters = useCharacterStore((s) => s.spawnMonsters);

  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<{ id?: number; draft: MonsterTemplateRequest } | null>(null);
  const [jsonMode, setJsonMode] = useState<'export' | 'import' | null>(null);
  const [jsonText, setJsonText] = useState('');
  const [jsonError, setJsonError] = useState<string | null>(null);
  const [clonePick, setClonePick] = useState('');

  useEffect(() => {
    if (open) void loadMonsterTemplates();
  }, [open, loadMonsterTemplates]);

  function startExport() {
    setJsonText(JSON.stringify(templates.map(toRequest), null, 2));
    setJsonError(null);
    setJsonMode('export');
  }

  async function runImport() {
    try {
      const parsed: unknown = JSON.parse(jsonText);
      const list = (Array.isArray(parsed) ? parsed : [parsed]) as MonsterTemplateRequest[];
      if (list.length === 0 || list.some((t) => !t || typeof t.name !== 'string')) {
        setJsonError('Expected a template object or an array of them, each with a name.');
        return;
      }
      await importMonsterTemplates(list);
      setJsonMode(null);
      setJsonText('');
      setJsonError(null);
    } catch {
      setJsonError('That is not valid JSON.');
    }
  }

  async function save() {
    if (!editing) return;
    if (!editing.draft.name.trim()) return;
    await saveMonsterTemplate(editing.draft, editing.id);
    if (!useCharacterStore.getState().error) setEditing(null);
  }

  return (
    <div className="monster-library deck-section">
      <div className="deck-section-head">
        <button className="btn btn--ghost" onClick={() => setOpen(!open)}>
          {open ? '▾' : '▸'} Monster library{templates.length > 0 && ` (${templates.length})`}
        </button>
        {open && (
          <div className="mlib-actions">
            <button
              className="btn btn--ghost"
              disabled={saving}
              onClick={() => setEditing({ draft: blankTemplate() })}
            >
              + New template
            </button>
            <button className="btn btn--ghost" onClick={startExport} disabled={templates.length === 0}>
              Export JSON
            </button>
            <button
              className="btn btn--ghost"
              onClick={() => {
                setJsonText('');
                setJsonError(null);
                setJsonMode('import');
              }}
            >
              Import JSON
            </button>
          </div>
        )}
      </div>

      {open && (
        <>
          {templates.length === 0 && !editing && <p className="deck-empty">No templates yet.</p>}
          {templates.map((t) => (
            <div className="mlib-row" key={t.id}>
              <span className="mlib-name">{t.name}</span>
              <span className="mlib-meta">
                Lv {t.level} · HP {t.maxHp} · AC {t.ac} · PA {t.pa} · MA {t.ma}
              </span>
              <button className="btn btn--ghost" disabled={acting} onClick={() => void spawnMonsters(t.id, 1)}>
                Spawn
              </button>
              <button className="btn btn--ghost" onClick={() => setEditing({ id: t.id, draft: toRequest(t) })}>
                Edit
              </button>
              <button
                className="btn btn--ghost deck-remove"
                disabled={saving}
                title="Delete this template (monsters already in the fight keep their own copy)"
                onClick={() => void deleteMonsterTemplate(t.id)}
              >
                ×
              </button>
            </div>
          ))}

          {roster.length > 0 && (
            <div className="mlib-row">
              <span className="mlib-name">Death fight</span>
              <span className="mlib-meta">mirror a character at full resources (N11c)</span>
              <select value={clonePick} onChange={(e) => setClonePick(e.target.value)}>
                <option value="">choose…</option>
                {roster.map((r) => (
                  <option key={r.playerId} value={r.playerId}>
                    {r.name}
                  </option>
                ))}
              </select>
              <button
                className="btn btn--ghost"
                disabled={saving || !clonePick}
                onClick={() => void cloneCharacterAsTemplate(clonePick)}
              >
                Create template
              </button>
            </div>
          )}

          {jsonMode && (
            <div className="mlib-editor">
              <span className="skills-hint">
                {jsonMode === 'export'
                  ? 'Copy this into another room’s Import to carry the library over.'
                  : 'Paste one template or an array — the Export format from any room.'}
              </span>
              <textarea
                className="mlib-json"
                value={jsonText}
                readOnly={jsonMode === 'export'}
                onChange={(e) => setJsonText(e.target.value)}
              />
              {jsonError && <p className="inline-error">{jsonError}</p>}
              <div className="mlib-actions">
                {jsonMode === 'import' && (
                  <button className="btn btn--gold" disabled={saving || !jsonText.trim()} onClick={() => void runImport()}>
                    Import
                  </button>
                )}
                <button className="btn btn--ghost" onClick={() => setJsonMode(null)}>
                  Close
                </button>
              </div>
            </div>
          )}

          {editing && (
            <TemplateEditor
              draft={editing.draft}
              isNew={editing.id == null}
              saving={saving}
              onChange={(draft) => setEditing({ ...editing, draft })}
              onSave={() => void save()}
              onCancel={() => setEditing(null)}
            />
          )}
        </>
      )}
    </div>
  );
}

function TemplateEditor({
  draft,
  isNew,
  saving,
  onChange,
  onSave,
  onCancel,
}: {
  draft: MonsterTemplateRequest;
  isNew: boolean;
  saving: boolean;
  onChange: (draft: MonsterTemplateRequest) => void;
  onSave: () => void;
  onCancel: () => void;
}) {
  const patch = (p: Partial<MonsterTemplateRequest>) => onChange({ ...draft, ...p });
  const [dtPick, setDtPick] = useState<DamageTypeId>('FIRE');
  const [dtMult, setDtMult] = useState('0.5');

  function toggleSave(a: AbilityScore) {
    const has = draft.savingThrowProficiencies.includes(a);
    patch({
      savingThrowProficiencies: has
        ? draft.savingThrowProficiencies.filter((x) => x !== a)
        : [...draft.savingThrowProficiencies, a],
    });
  }

  function addDamageTaken() {
    const mult = Number.parseFloat(dtMult);
    if (Number.isNaN(mult) || mult < 0) return;
    patch({ damageTaken: { ...draft.damageTaken, [dtPick]: mult } });
  }

  function removeDamageTaken(type: string) {
    const next = { ...draft.damageTaken };
    delete next[type as DamageTypeId];
    patch({ damageTaken: next });
  }

  const numberField = (label: string, key: keyof MonsterTemplateRequest, min = 0) => (
    <label>
      {label}
      <input
        type="number"
        min={min}
        value={(draft[key] as number | null) ?? ''}
        onChange={(e) => patch({ [key]: num(e.target.value, min) } as Partial<MonsterTemplateRequest>)}
      />
    </label>
  );

  return (
    <div className="mlib-editor">
      <div className="mlib-grid">
        <label style={{ gridColumn: 'span 2' }}>
          Name
          <input value={draft.name} onChange={(e) => patch({ name: e.target.value })} autoFocus={isNew} />
        </label>
        {numberField('Level', 'level', 1)}
        {numberField('Max HP', 'maxHp', 1)}
        {numberField('AC', 'ac')}
        {numberField('PA', 'pa')}
        {numberField('MA', 'ma')}
        {numberField('Speed (ft/AP)', 'speed')}
        <label title="Sets the concentration DC (5 + might) of its attacks — optional">
          Might
          <input
            type="number"
            min={0}
            value={draft.might ?? ''}
            onChange={(e) => patch({ might: e.target.value === '' ? null : num(e.target.value, 0) })}
          />
        </label>
        {numberField('Initiative bonus', 'initiativeBonus', -20)}
        <label title="Ruling E2: stacks needed before a negative effect fires — blank = ceil(level/2)">
          Stack threshold
          <input
            type="number"
            min={1}
            placeholder={`${Math.ceil(draft.level / 2)}`}
            value={draft.stackThreshold ?? ''}
            onChange={(e) => patch({ stackThreshold: e.target.value === '' ? null : num(e.target.value, 1) })}
          />
        </label>
      </div>

      <div className="mlib-stats">
        {ABILITIES.map((a) => (
          <label className="mlib-stat" key={a}>
            {a}
            <input
              type="number"
              min={1}
              value={draft.stats[a] ?? 10}
              onChange={(e) => patch({ stats: { ...draft.stats, [a]: num(e.target.value, 10) } })}
            />
          </label>
        ))}
      </div>

      <div className="mlib-checks">
        <span className="skills-hint">Saves:</span>
        {ABILITIES.map((a) => (
          <label key={a}>
            <input
              type="checkbox"
              checked={draft.savingThrowProficiencies.includes(a)}
              onChange={() => toggleSave(a)}
            />{' '}
            {a}
          </label>
        ))}
      </div>

      <div className="mlib-dt">
        <span className="skills-hint">Damage taken (×0 immune · ×0.5 resists · ×2 vulnerable):</span>
        {Object.entries(draft.damageTaken).map(([type, mult]) => (
          <span className="monster-chip" key={type}>
            {type.toLowerCase()} ×{mult}
            <button type="button" onClick={() => removeDamageTaken(type)} title="Remove">
              ×
            </button>
          </span>
        ))}
        <select value={dtPick} onChange={(e) => setDtPick(e.target.value as DamageTypeId)}>
          {DAMAGE_TYPE_OPTIONS.map((d) => (
            <option key={d.id} value={d.id}>
              {d.label}
            </option>
          ))}
        </select>
        <input type="number" step={0.25} min={0} value={dtMult} onChange={(e) => setDtMult(e.target.value)} />
        <button className="btn btn--ghost" type="button" onClick={addDamageTaken}>
          + Add
        </button>
      </div>

      <label className="skills-hint">
        Abilities (free text — the table adjudicates; automation waits on the ability rulings)
        <textarea
          value={draft.abilitiesText ?? ''}
          onChange={(e) => patch({ abilitiesText: e.target.value })}
          placeholder="Bite: 2 AP, melee, 1d8+3 piercing. Pack tactics: advantage when an ally is adjacent."
        />
      </label>

      <div className="mlib-actions">
        <button className="btn btn--gold" disabled={saving || !draft.name.trim()} onClick={onSave}>
          {saving ? 'Saving…' : isNew ? 'Create template' : 'Save changes'}
        </button>
        <button className="btn btn--ghost" onClick={onCancel}>
          Cancel
        </button>
      </div>
    </div>
  );
}
