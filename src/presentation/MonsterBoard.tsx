import { useEffect, useState } from 'react';
import { useCharacterStore } from '../application/characterStore';
import type { DamageTypeId, MonsterView } from '../platform/types';
import { DAMAGE_TYPE_OPTIONS, EFFECT_OPTIONS } from '../domain/combatCatalog';
import { ResolutionLog } from './ResolutionLog';

function parsePositive(v: string): number | null {
  const n = Number.parseInt(v, 10);
  return Number.isNaN(n) || n <= 0 ? null : n;
}

/**
 * GM board for the monsters in the room's fight (Epic 2, Story 2.3): spawn from the room
 * library, see vitals / effects / conditions at a glance, and run damage, healing and
 * effects on any of them through the same pipelines a player's sheet uses. Monster
 * turns are ended from here (or from the tracker); nobody starts turns in combat.
 */
export function MonsterBoard() {
  const monsters = useCharacterStore((s) => s.monsters);
  const templates = useCharacterStore((s) => s.monsterTemplates);
  const encounter = useCharacterStore((s) => s.encounter);
  const acting = useCharacterStore((s) => s.acting);
  const lastResolution = useCharacterStore((s) => s.lastResolution);
  const lastResolutionTarget = useCharacterStore((s) => s.lastResolutionTarget);
  const loadMonsters = useCharacterStore((s) => s.loadMonsters);
  const loadMonsterTemplates = useCharacterStore((s) => s.loadMonsterTemplates);
  const spawnMonsters = useCharacterStore((s) => s.spawnMonsters);
  const removeMonster = useCharacterStore((s) => s.removeMonster);
  const clearMonsters = useCharacterStore((s) => s.clearMonsters);
  const clearResolution = useCharacterStore((s) => s.clearResolution);

  const [templateId, setTemplateId] = useState('');
  const [count, setCount] = useState('1');

  useEffect(() => {
    void loadMonsters();
    void loadMonsterTemplates();
  }, [loadMonsters, loadMonsterTemplates]);

  const chosenTemplate = templateId || (templates[0] ? String(templates[0].id) : '');
  const isMonsterResolution =
    !!lastResolution && !!lastResolutionTarget && monsters.some((m) => m.name === lastResolutionTarget);

  return (
    <section className="monster-board">
      <div className="monster-board-head">
        <h2>Monsters</h2>
        <span className="roster-sub">
          {monsters.length === 0 ? 'none in the fight' : `${monsters.length} in the fight`}
        </span>
        <span className="spacer" />
        {templates.length > 0 ? (
          <div className="monster-spawn">
            <select value={chosenTemplate} onChange={(e) => setTemplateId(e.target.value)}>
              {templates.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name} · Lv {t.level}
                </option>
              ))}
            </select>
            <input
              type="number"
              min={1}
              max={20}
              value={count}
              onChange={(e) => setCount(e.target.value)}
              title="How many to spawn"
            />
            <button
              className="btn btn--gold"
              disabled={acting || !chosenTemplate}
              title="Stamps the template into the fight. If combat is running they roll initiative and join the order."
              onClick={() => void spawnMonsters(Number(chosenTemplate), parsePositive(count) ?? 1)}
            >
              Spawn
            </button>
          </div>
        ) : (
          <span className="skills-hint">Add a template in the Monster library below to spawn from.</span>
        )}
        {monsters.length > 0 && (
          <button className="btn btn--ghost" disabled={acting} onClick={() => void clearMonsters()}>
            Clear all
          </button>
        )}
      </div>

      {isMonsterResolution && lastResolution && (
        <ResolutionLog resolution={lastResolution} targetName={lastResolutionTarget} onClose={clearResolution} />
      )}

      {monsters.length > 0 && (
        <div className="monster-grid">
          {monsters.map((m) => (
            <MonsterCard
              key={m.id}
              monster={m}
              isCurrent={!!encounter?.active && encounter.currentPlayerId === m.combatantId}
              onRemove={() => void removeMonster(m.id)}
            />
          ))}
        </div>
      )}
    </section>
  );
}

function MonsterCard({
  monster: m,
  isCurrent,
  onRemove,
}: {
  monster: MonsterView;
  isCurrent: boolean;
  onRemove: () => void;
}) {
  const acting = useCharacterStore((s) => s.acting);
  const doTargetedDamage = useCharacterStore((s) => s.doTargetedDamage);
  const doTargetedHeal = useCharacterStore((s) => s.doTargetedHeal);
  const doTargetedApplyEffect = useCharacterStore((s) => s.doTargetedApplyEffect);
  const doMonsterRemoveEffect = useCharacterStore((s) => s.doMonsterRemoveEffect);
  const endMonsterTurn = useCharacterStore((s) => s.endMonsterTurn);

  const [dmg, setDmg] = useState('10');
  const [dmgType, setDmgType] = useState<DamageTypeId>('SLASHING');
  const [heal, setHeal] = useState('10');
  const [fxId, setFxId] = useState(EFFECT_OPTIONS[0]?.id ?? '');
  const [fxStacks, setFxStacks] = useState('1');
  /** Who applied the effect — for `taunted` this IS the taunter the monster must attack. */
  const [fxSource, setFxSource] = useState('');
  const roster = useCharacterStore((s) => s.roster);
  const monsters = useCharacterStore((s) => s.monsters);
  const sourcePickerNeeded = fxId === 'taunted';

  const dead = m.status === 'DEAD';
  const pct = m.hp.max > 0 ? Math.max(0, Math.min(100, (m.hp.current / m.hp.max) * 100)) : 0;
  const cls =
    'monster-card' + (isCurrent ? ' monster-card--current' : '') + (dead ? ' monster-card--dead' : '');

  return (
    <div className={cls}>
      <div className="monster-card-head">
        <span className="monster-card-name">{m.name}</span>
        <span className="monster-card-level">Lv {m.level}</span>
        {m.status !== 'ALIVE' && (
          <span className={'monster-status monster-status--' + m.status.toLowerCase()}>
            {m.status.toLowerCase()}
          </span>
        )}
        {isCurrent && <span className="encounter-arrow" title="Its turn">▶</span>}
      </div>

      <div className="roster-hpbar">
        <div className="roster-hpbar-fill" style={{ width: `${pct}%` }} />
      </div>
      <div className="monster-stats">
        <span>
          HP <strong>{m.hp.current}</strong>/{m.hp.max}
          {m.hp.temp > 0 && <> +{m.hp.temp}</>}
        </span>
        <span>AC <strong>{m.ac}</strong></span>
        <span>PA <strong>{m.pa}</strong></span>
        <span>MA <strong>{m.ma}</strong></span>
        <span title="Negative-effect stack threshold">thr {m.stackThreshold}</span>
        {m.conditions.map((c) => (
          <span key={c} className="monster-status">
            {c}
          </span>
        ))}
      </div>

      {m.activeEffects.length > 0 && (
        <div className="monster-chips">
          {m.activeEffects.map((e) => (
            <span
              key={e.id + e.stacks}
              className={'monster-chip' + (e.active ? '' : ' monster-chip--dormant')}
              title={e.active ? e.name : `${e.name} — dormant until ${e.threshold} stacks`}
            >
              {e.name}
              {e.threshold != null ? ` ${e.stacks}/${e.threshold}` : e.stacks > 1 ? ` ×${e.stacks}` : ''}
              {e.value != null && ` (${e.value})`}
              {e.rounds != null && ` ${e.rounds}r`}
              <button
                type="button"
                title="Remove this effect"
                disabled={acting}
                onClick={() => void doMonsterRemoveEffect(m.combatantId, e.id)}
              >
                ×
              </button>
            </span>
          ))}
        </div>
      )}

      {m.abilitiesText && (
        <details className="monster-abilities">
          <summary>Abilities</summary>
          {m.abilitiesText}
        </details>
      )}

      <div className="monster-actions">
        <div className="monster-row">
          <input type="number" min={1} value={dmg} onChange={(e) => setDmg(e.target.value)} />
          <select value={dmgType} onChange={(e) => setDmgType(e.target.value as DamageTypeId)}>
            {DAMAGE_TYPE_OPTIONS.map((d) => (
              <option key={d.id} value={d.id}>
                {d.label}
              </option>
            ))}
          </select>
          <button
            className="btn btn--ghost"
            disabled={acting || dead}
            onClick={() => {
              const v = parsePositive(dmg);
              if (v) void doTargetedDamage(m.combatantId, v, dmgType);
            }}
          >
            Damage
          </button>
        </div>
        <div className="monster-row">
          <input type="number" min={1} value={heal} onChange={(e) => setHeal(e.target.value)} />
          <button
            className="btn btn--ghost"
            disabled={acting || dead}
            onClick={() => {
              const v = parsePositive(heal);
              if (v) void doTargetedHeal(m.combatantId, v);
            }}
          >
            Heal
          </button>
          <select value={fxId} onChange={(e) => setFxId(e.target.value)}>
            {EFFECT_OPTIONS.map((e) => (
              <option key={e.id} value={e.id}>
                {e.name}
              </option>
            ))}
          </select>
          <input type="number" min={1} value={fxStacks} onChange={(e) => setFxStacks(e.target.value)} title="Stacks" />
          {sourcePickerNeeded && (
            <select
              value={fxSource}
              onChange={(e) => setFxSource(e.target.value)}
              title="The taunter — this monster may only aim offensive actions at them while they stand"
            >
              <option value="">taunted by…</option>
              {roster.map((r) => (
                <option key={r.playerId} value={r.playerId}>
                  {r.name}
                </option>
              ))}
              {monsters
                .filter((o) => o.combatantId !== m.combatantId && o.status !== 'DEAD')
                .map((o) => (
                  <option key={o.combatantId} value={o.combatantId}>
                    {o.name}
                  </option>
                ))}
            </select>
          )}
          <button
            className="btn btn--ghost"
            disabled={acting || dead || !fxId || (sourcePickerNeeded && !fxSource)}
            onClick={() =>
              void doTargetedApplyEffect(m.combatantId, {
                effectId: fxId,
                stacks: parsePositive(fxStacks) ?? 1,
                source: sourcePickerNeeded ? fxSource : 'gm-board',
              })
            }
          >
            Apply
          </button>
        </div>
        <div className="monster-row">
          {isCurrent && (
            <button
              className="btn btn--gold"
              disabled={acting}
              title="Ends this monster's turn — the order advances and the next turn begins"
              onClick={() => void endMonsterTurn(m.combatantId)}
            >
              End turn
            </button>
          )}
          <span className="spacer" style={{ flex: 1 }} />
          <button className="btn btn--ghost" disabled={acting} title="Remove from the fight" onClick={onRemove}>
            Remove
          </button>
        </div>
      </div>
    </div>
  );
}
