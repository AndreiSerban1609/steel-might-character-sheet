import { useEffect, useState } from 'react';
import { useCharacterStore } from '../application/characterStore';
import {
  abilitiesForClass,
  abilityById,
  abilityCostParts,
  formatGroup,
  KIND_LABELS,
  type AbilityEntry,
} from '../domain/abilityCatalog';
import { ResolutionLog } from './ResolutionLog';

const KIND_ORDER: AbilityEntry['kind'][] = ['active', 'attack-enhancer', 'reaction', 'passive'];

export function AbilitiesPanel() {
  const snapshot = useCharacterStore((s) => s.snapshot);
  const abilities = useCharacterStore((s) => s.abilities);
  const acting = useCharacterStore((s) => s.acting);
  const saving = useCharacterStore((s) => s.saving);
  const error = useCharacterStore((s) => s.error);
  const lastResolution = useCharacterStore((s) => s.lastResolution);
  const loadAbilities = useCharacterStore((s) => s.loadAbilities);
  const saveAbilities = useCharacterStore((s) => s.saveAbilities);
  const doUseAbility = useCharacterStore((s) => s.doUseAbility);
  const clearResolution = useCharacterStore((s) => s.clearResolution);

  const [draft, setDraft] = useState<Set<string> | null>(null);

  useEffect(() => {
    void loadAbilities();
  }, [loadAbilities]);

  if (!snapshot) return <div className="panel-msg">No character loaded.</div>;
  if (!abilities) return <div className="panel-msg">Loading abilities…</div>;

  const catalog = abilitiesForClass(snapshot.classId);
  const editing = draft !== null;

  // ── Picker (edit mode): choice-group abilities the character may learn ──
  if (editing) {
    const groups = new Map<string, AbilityEntry[]>();
    for (const entry of catalog) {
      if (!entry.group) continue; // class-granted, not pickable
      const list = groups.get(entry.group) ?? [];
      list.push(entry);
      groups.set(entry.group, list);
    }

    function toggle(id: string) {
      setDraft((d) => {
        if (!d) return d;
        const next = new Set(d);
        if (next.has(id)) next.delete(id);
        else next.add(id);
        return next;
      });
    }

    async function save() {
      if (!draft) return;
      await saveAbilities([...draft]);
      if (!useCharacterStore.getState().error) setDraft(null);
    }

    return (
      <>
        <div className="sheet-actionbar">
          <div className="edit-actions">
            <button className="btn btn--ghost" onClick={() => setDraft(null)} disabled={saving}>
              Cancel
            </button>
            <button className="btn btn--gold" onClick={() => void save()} disabled={saving}>
              {saving ? 'Saving…' : 'Save'}
            </button>
          </div>
        </div>
        <p className="skills-hint">
          Mark the abilities this character has chosen. Groups reflect level-up choices — the
          picker doesn't enforce pick counts (free-form by design); follow your class rules.
        </p>
        {error && <p className="inline-error">{error}</p>}

        {[...groups.entries()].map(([group, entries]) => (
          <div className="ability-group" key={group}>
            <h3 className="skills-group-title">
              <span>{formatGroup(group)}</span>
            </h3>
            {entries.map((entry) => {
              const locked = entry.minLevel > snapshot.level;
              return (
                <label className={'ability-pick' + (locked ? ' ability-pick--locked' : '')} key={entry.id}>
                  <input
                    type="checkbox"
                    checked={draft.has(entry.id)}
                    disabled={locked}
                    onChange={() => toggle(entry.id)}
                  />
                  <span className="ability-pick-name">
                    {entry.name} <span className="ability-lvl">L{entry.minLevel}</span>
                  </span>
                  <span className="ability-pick-desc">{entry.description}</span>
                </label>
              );
            })}
          </div>
        ))}
      </>
    );
  }

  // ── View mode: known abilities grouped by kind ──
  const known = abilities.known
    .map((id) => abilityById(id))
    .filter((a): a is AbilityEntry => a !== undefined);
  const usesById = new Map(abilities.uses.map((u) => [u.abilityId, u]));

  return (
    <>
      <div className="sheet-actionbar">
        <button className="btn btn--ghost" onClick={() => setDraft(new Set(abilities.picked))}>
          Edit picks
        </button>
      </div>

      {error && <p className="inline-error">{error}</p>}
      {known.length === 0 && (
        <p className="panel-msg">No abilities yet — pick your class choices via "Edit picks".</p>
      )}

      {KIND_ORDER.map((kind) => {
        const entries = known.filter((a) => a.kind === kind);
        if (entries.length === 0) return null;
        return (
          <div className="ability-group" key={kind}>
            <h3 className="skills-group-title">
              <span>{KIND_LABELS[kind]}</span>
            </h3>
            {entries.map((entry) => {
              const costs = abilityCostParts(entry, snapshot);
              const use = usesById.get(entry.id);
              const outOfUses = use?.perRestRemaining === 0;
              const usedThisTurn = use?.perTurnRemaining === 0;
              const short = costs.some((p) => p.short);
              const useTitle = outOfUses
                ? 'No uses left until a rest'
                : usedThisTurn
                  ? 'Already used this turn'
                  : entry.resolution === 'auto'
                    ? 'The server rolls and applies this'
                    : 'Spends the costs; the rules text lands in the log for the table';
              return (
                <div className="ability-row" key={entry.id}>
                  <div className="ability-row-head">
                    <span className="ability-name">{entry.name}</span>
                    <span className="ability-cost">
                      {costs.length === 0 && 'free'}
                      {costs.map((p, i) => (
                        <span
                          key={i}
                          className={p.short ? 'ability-cost-part ability-cost-part--short' : 'ability-cost-part'}
                          title={p.available !== null ? `You have ${p.available}` : undefined}
                        >
                          {i > 0 && ' · '}
                          {p.label}
                        </span>
                      ))}
                    </span>
                    {use && use.perRestMax !== null && (
                      <span
                        className={outOfUses ? 'ability-budget ability-budget--spent' : 'ability-budget'}
                        title="Uses left until a rest"
                      >
                        {use.perRestRemaining}/{use.perRestMax} rest
                      </span>
                    )}
                    {use && use.perTurnMax !== null && (
                      <span
                        className={usedThisTurn ? 'ability-budget ability-budget--spent' : 'ability-budget'}
                        title="Uses left this turn"
                      >
                        {use.perTurnRemaining}/{use.perTurnMax} turn
                      </span>
                    )}
                    {kind !== 'passive' && (
                      <button
                        className="btn btn--gold ability-use"
                        title={useTitle}
                        onClick={() => void doUseAbility(entry.id)}
                        disabled={acting || outOfUses || usedThisTurn}
                      >
                        Use
                      </button>
                    )}
                  </div>
                  <div className="ability-desc">{entry.description}</div>
                  {short && !outOfUses && !usedThisTurn && (
                    <div className="ability-short-note">Not enough resources right now — highlighted costs exceed what you have.</div>
                  )}
                </div>
              );
            })}
          </div>
        );
      })}

      {lastResolution && <ResolutionLog resolution={lastResolution} onClose={clearResolution} />}
    </>
  );
}
