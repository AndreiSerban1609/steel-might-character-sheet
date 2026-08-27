import { useEffect, useState } from 'react';
import { useCharacterStore } from '../application/characterStore';
import {
  casterTypeOf,
  formatCost,
  formatDice,
  maxSpellLevel,
  spellById,
  spellsForClass,
  type SpellEntry,
} from '../domain/spellCatalog';
import { effectName } from '../domain/combatCatalog';
import { HoverInfo } from './HoverInfo';
import { ResolutionLog } from './ResolutionLog';

export function SpellbookPanel() {
  const snapshot = useCharacterStore((s) => s.snapshot);
  const spellbook = useCharacterStore((s) => s.spellbook);
  const acting = useCharacterStore((s) => s.acting);
  const error = useCharacterStore((s) => s.error);
  const lastResolution = useCharacterStore((s) => s.lastResolution);
  const loadSpellbook = useCharacterStore((s) => s.loadSpellbook);
  const doCast = useCharacterStore((s) => s.doCast);
  const doPrepareSpells = useCharacterStore((s) => s.doPrepareSpells);
  const clearResolution = useCharacterStore((s) => s.clearResolution);

  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [castLevel, setCastLevel] = useState<number | null>(null);
  // '' = no effects target (DM applies) | 'self' | a party member's playerId
  const [castTarget, setCastTarget] = useState('');
  const [prepDraft, setPrepDraft] = useState<string[] | null>(null);
  const [tagFilter, setTagFilter] = useState('');
  const lastResolutionTarget = useCharacterStore((s) => s.lastResolutionTarget);
  const monsters = useCharacterStore((s) => s.monsters);
  const roster = useCharacterStore((s) => s.roster);
  const selectedPlayerId = useCharacterStore((s) => s.selectedPlayerId);

  useEffect(() => {
    void loadSpellbook();
  }, [loadSpellbook]);

  if (!snapshot) return <div className="panel-msg">No character loaded.</div>;

  const casterType = casterTypeOf(snapshot.classId);
  if (casterType === 'none') {
    return <div className="panel-msg">{snapshot.name} is not a spellcaster.</div>;
  }
  if (!spellbook) return <div className="panel-msg">Loading spellbook…</div>;

  const accessLevel = maxSpellLevel(snapshot.classId, snapshot.level);
  const intMod = snapshot.modifiers.INT ?? 0;
  const prepAllowance = Math.max(0, intMod);
  const concentrating = snapshot.activeEffects.find((e) => e.id === 'concentrating');
  const channeling = snapshot.activeEffects.find((e) => e.id === 'channeling');
  const classSpells = spellsForClass(snapshot.classId);
  const preparable = classSpells.filter((s) => s.level <= accessLevel);

  // Tag filter over the known list — only offers tags the character actually has.
  const knownTags = [
    ...new Set(spellbook.knownSpells.flatMap((id) => spellById(id)?.tags ?? [])),
  ].sort();
  const visibleKnown = tagFilter
    ? spellbook.knownSpells.filter((id) => spellById(id)?.tags?.includes(tagFilter))
    : spellbook.knownSpells;

  function toggleExpand(id: string) {
    setExpandedId((cur) => (cur === id ? null : id));
    setCastLevel(null);
    setCastTarget('');
  }

  function submitCast(spell: SpellEntry) {
    void doCast({
      spellId: spell.id,
      castAtLevel: castLevel ?? undefined,
      applyEffectsToSelf: castTarget === 'self' || undefined,
      targetCombatantId: castTarget && castTarget !== 'self' ? castTarget : undefined,
    });
  }

  function renderSpellRow(id: string, badge: string | null) {
    const spell = spellById(id);
    if (!spell) {
      return (
        <li className="spell-row" key={id}>
          <span className="spell-name">{id}</span>
          <span className="spell-meta">unknown spell</span>
        </li>
      );
    }
    const expanded = expandedId === id;
    const upcastLevels: number[] = [];
    if (spell.scaling) {
      for (let l = spell.level; l <= accessLevel; l++) upcastLevels.push(l);
    }
    return (
      <li className={expanded ? 'spell-row spell-row--open' : 'spell-row'} key={id}>
        <button className="spell-head" onClick={() => toggleExpand(id)}>
          <HoverInfo info={expanded ? null : spell.description} focusable={false}>
            <span className="spell-name">{spell.name}</span>
          </HoverInfo>
          <span className="spell-meta">
            L{spell.level} · {formatCost(spell.apCost, 'AP')} · {formatCost(spell.manaCost, 'mana')}
            {spell.concentration && ' · conc.'}
            {spell.channeling && ' · channel'}
          </span>
          {spell.tags && spell.tags.length > 0 && (
            <span className="spell-tags">
              {spell.tags.map((t) => (
                <span className="spell-tag" key={t}>
                  {t}
                </span>
              ))}
            </span>
          )}
          {badge && <span className="spell-badge">{badge}</span>}
        </button>
        {expanded && (
          <div className="spell-details">
            <p className="spell-desc">{spell.description}</p>
            <p className="spell-facts">
              {spell.range && <span>Range {spell.range}</span>}
              {spell.duration && <span>Duration {spell.duration}</span>}
              <span>Components {spell.components.join(', ') || '—'}</span>
              {spell.damage && (
                <span>
                  Damage {formatDice(spell.damage)}
                  {spell.damageType ? ` ${spell.damageType}` : ''}
                </span>
              )}
              {spell.healing && <span>Healing {formatDice(spell.healing)}</span>}
              {spell.saveStat && <span>Save {spell.saveStat.toUpperCase()}</span>}
              {spell.effects && spell.effects.length > 0 && (
                <span>Applies {spell.effects.map(effectName).join(', ')}</span>
              )}
            </p>
            <div className="combat-form">
              {upcastLevels.length > 1 && (
                <select
                  value={castLevel ?? spell.level}
                  onChange={(e) => setCastLevel(Number.parseInt(e.target.value, 10))}
                  title="Cast at level (upcasting raises the mana cost)"
                >
                  {upcastLevels.map((l) => (
                    <option key={l} value={l}>
                      at level {l}
                    </option>
                  ))}
                </select>
              )}
              {spell.effects && spell.effects.length > 0 && (
                <select
                  value={castTarget}
                  onChange={(e) => setCastTarget(e.target.value)}
                  title="Who the spell is aimed at: attack rolls meet their AC, saves roll on them, damage/healing/effects land on them"
                >
                  <option value="">target: none (numbers only)</option>
                  <option value="self">target: self</option>
                  {roster
                    .filter((r) => r.playerId !== selectedPlayerId)
                    .map((r) => (
                      <option key={r.playerId} value={r.playerId}>
                        target: {r.name}
                      </option>
                    ))}
                  {monsters
                    .filter((m) => m.status !== 'DEAD')
                    .map((m) => (
                      <option key={m.combatantId} value={m.combatantId}>
                        target: {m.name} (monster)
                      </option>
                    ))}
                </select>
              )}
              <button className="btn btn--gold" onClick={() => submitCast(spell)} disabled={acting}>
                Cast
              </button>
            </div>
          </div>
        )}
      </li>
    );
  }

  return (
    <>
      {error && <p className="inline-error">{error}</p>}

      <div className="spellbook-head">
        <span className="spell-stat-chip">
          Mana{' '}
          <strong>
            {snapshot.mana.current} / {snapshot.mana.max}
          </strong>
        </span>
        {spellbook.spellcastingAttribute && (
          <span className="spell-stat-chip">
            Spell stat <strong>{spellbook.spellcastingAttribute}</strong>
          </span>
        )}
        <span className="spell-stat-chip">
          Save DC <strong>{spellbook.spellSaveDC}</strong>
        </span>
        <span className="spell-stat-chip">
          Attack <strong>+{spellbook.spellAttackBonus}</strong>
        </span>
        <span className="spell-stat-chip">
          Max spell level <strong>{accessLevel}</strong>
        </span>
        {concentrating && (
          <span className="spell-stat-chip spell-stat-chip--live" title={concentrating.id}>
            Concentrating
          </span>
        )}
        {channeling && <span className="spell-stat-chip spell-stat-chip--live">Channeling</span>}
      </div>

      <div className="combat-effects">
        <div className="combat-log-head">
          <h3 className="combat-section-title">Known spells</h3>
          {knownTags.length > 0 && (
            <select
              className="spell-tag-filter"
              value={tagFilter}
              onChange={(e) => setTagFilter(e.target.value)}
              title="Filter your known spells by what they do"
            >
              <option value="">all tags</option>
              {knownTags.map((t) => (
                <option key={t} value={t}>
                  {t}
                </option>
              ))}
            </select>
          )}
        </div>
        {spellbook.knownSpells.length === 0 && <p className="deck-empty">None learned yet.</p>}
        {spellbook.knownSpells.length > 0 && visibleKnown.length === 0 && (
          <p className="deck-empty">No known spell is tagged “{tagFilter}”.</p>
        )}
        <ul className="spell-list">{visibleKnown.map((id) => renderSpellRow(id, null))}</ul>
      </div>

      <div className="combat-effects">
        <div className="combat-log-head">
          <h3 className="combat-section-title">
            Prepared (INT bonus — {prepAllowance} slot{prepAllowance === 1 ? '' : 's'})
          </h3>
          {prepDraft === null ? (
            <button
              className="btn btn--ghost"
              onClick={() => setPrepDraft([...spellbook.preparedSpells])}
              disabled={acting || prepAllowance === 0}
              title="Re-chosen after each rest, up to your INT modifier"
            >
              Edit
            </button>
          ) : (
            <span className="spell-prep-actions">
              <button
                className="btn btn--gold"
                onClick={() => {
                  void doPrepareSpells(prepDraft);
                  setPrepDraft(null);
                }}
                disabled={acting}
              >
                Save
              </button>
              <button className="btn btn--ghost" onClick={() => setPrepDraft(null)}>
                Cancel
              </button>
            </span>
          )}
        </div>

        {prepDraft === null ? (
          <>
            {spellbook.preparedSpells.length === 0 && (
              <p className="deck-empty">None prepared — rest, then choose up to your INT modifier.</p>
            )}
            <ul className="spell-list">
              {spellbook.preparedSpells.map((id) => renderSpellRow(id, 'prepared'))}
            </ul>
          </>
        ) : (
          <>
            <p className="spell-prep-count">
              {prepDraft.length} / {prepAllowance} chosen
              {casterType === 'minor'
                ? ' · minors: no 5th-level, max 2 per level (1st exempt)'
                : ' · majors: max 1 per level (1st exempt)'}
            </p>
            <ul className="spell-list spell-list--picker">
              {preparable.map((s) => {
                const checked = prepDraft.includes(s.id);
                return (
                  <li className="spell-row" key={s.id}>
                    <label className="spell-pick">
                      <input
                        type="checkbox"
                        checked={checked}
                        onChange={(e) =>
                          setPrepDraft(
                            e.target.checked
                              ? [...prepDraft, s.id]
                              : prepDraft.filter((x) => x !== s.id),
                          )
                        }
                      />
                      <HoverInfo info={s.description} focusable={false}>
                        <span className="spell-name">{s.name}</span>
                      </HoverInfo>
                      <span className="spell-meta">
                        L{s.level} · {formatCost(s.apCost, 'AP')} · {formatCost(s.manaCost, 'mana')}
                      </span>
                    </label>
                  </li>
                );
              })}
            </ul>
          </>
        )}
      </div>

      {lastResolution && (
        <ResolutionLog
          resolution={lastResolution}
          targetName={lastResolutionTarget}
          onClose={clearResolution}
        />
      )}
    </>
  );
}
