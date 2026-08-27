import { useCallback, useEffect, useState } from 'react';
import { useCharacterStore } from '../application/characterStore';
import type { AuditView, DamageTypeId, PoolView } from '../platform/types';
import { DAMAGE_TYPE_OPTIONS, EFFECT_OPTIONS, effectName, effectOption } from '../domain/combatCatalog';
import { fetchCombatLog } from '../platform/http';
import { itemLabel } from '../domain/itemCatalog';
import { camelToWords } from '../domain/stats';
import { EncounterTracker } from './EncounterTracker';
import { HoverInfo } from './HoverInfo';
import { ResolutionLog } from './ResolutionLog';

function parsePositive(v: string): number | null {
  const n = Number.parseInt(v, 10);
  return Number.isNaN(n) || n <= 0 ? null : n;
}

export function CombatPanel() {
  const snapshot = useCharacterStore((s) => s.snapshot);
  const acting = useCharacterStore((s) => s.acting);
  const error = useCharacterStore((s) => s.error);
  const lastResolution = useCharacterStore((s) => s.lastResolution);
  const doTargetedDamage = useCharacterStore((s) => s.doTargetedDamage);
  const doTargetedHeal = useCharacterStore((s) => s.doTargetedHeal);
  const doTargetedApplyEffect = useCharacterStore((s) => s.doTargetedApplyEffect);
  const lastResolutionTarget = useCharacterStore((s) => s.lastResolutionTarget);
  const roster = useCharacterStore((s) => s.roster);
  const monsters = useCharacterStore((s) => s.monsters);
  const doTurnStart = useCharacterStore((s) => s.doTurnStart);
  const doTurnEnd = useCharacterStore((s) => s.doTurnEnd);
  const doSpendResource = useCharacterStore((s) => s.doSpendResource);
  const doGainResource = useCharacterStore((s) => s.doGainResource);
  const doPrepareReaction = useCharacterStore((s) => s.doPrepareReaction);
  const doResolveReaction = useCharacterStore((s) => s.doResolveReaction);
  const encounter = useCharacterStore((s) => s.encounter);
  const customItems = useCharacterStore((s) => s.customItems);
  const selectedPlayerId = useCharacterStore((s) => s.selectedPlayerId);
  const role = useCharacterStore((s) => s.role);
  const doRemoveEffect = useCharacterStore((s) => s.doRemoveEffect);
  const doRevive = useCharacterStore((s) => s.doRevive);
  const doCombatStart = useCharacterStore((s) => s.doCombatStart);
  const doRest = useCharacterStore((s) => s.doRest);
  const clearResolution = useCharacterStore((s) => s.clearResolution);

  const doWeaponAttack = useCharacterStore((s) => s.doWeaponAttack);
  const [dmgValue, setDmgValue] = useState('10');
  const [dmgType, setDmgType] = useState<DamageTypeId>('SLASHING');
  const [dmgMight, setDmgMight] = useState('');
  /** '' = unnamed attacker; a monster's combatant id fills its might/source in server-side (Story 2.4). */
  const [dmgAttacker, setDmgAttacker] = useState('');
  const [attackWeapon, setAttackWeapon] = useState('');
  const [healValue, setHealValue] = useState('10');
  const [fxId, setFxId] = useState(EFFECT_OPTIONS[0]?.id ?? '');
  const [fxStacks, setFxStacks] = useState('1');
  const [fxValue, setFxValue] = useState('');
  const [fxDuration, setFxDuration] = useState('');
  const [restTier, setRestTier] = useState('100');
  const [resAmount, setResAmount] = useState('1');
  const [targetId, setTargetId] = useState('');
  // Free-form AP spend + prepared reactions (2026-08-27 — custom reactions cost AP on the prep turn).
  const [apSpend, setApSpend] = useState('1');
  const [apNote, setApNote] = useState('');
  const [prepCost, setPrepCost] = useState('1');
  const [prepNote, setPrepNote] = useState('');

  if (!snapshot) return <div className="panel-msg">No character loaded.</div>;

  const hpPct = snapshot.hp.max > 0 ? (snapshot.hp.current / snapshot.hp.max) * 100 : 0;
  const selectedFx = effectOption(fxId);

  // Damage/heal/effects can target any party member (trusted table); everything
  // else (attack rolls, turns, rest, pools) stays on the viewed character.
  const party = roster.filter((r) => r.playerId !== selectedPlayerId);
  // Monsters in the room's fight are targets for everyone (ruling E5, trusted table).
  const foes = monsters.filter((m) => m.status !== 'DEAD');
  const effectiveTarget =
    targetId &&
    (party.some((r) => r.playerId === targetId) || foes.some((m) => m.combatantId === targetId))
      ? targetId
      : selectedPlayerId!;
  const targetingOther = effectiveTarget !== selectedPlayerId;
  const targetingMonster = foes.some((m) => m.combatantId === effectiveTarget);
  const targetLabel = (id: string): string =>
    party.find((r) => r.playerId === id)?.name ?? foes.find((m) => m.combatantId === id)?.name ?? id;

  // Turn gating mirrors the server rules: turns begin automatically in an encounter
  // (the GM opens combat, ending a turn starts the next), so participants only ever
  // END their turn. Manual start/combat-start ticking stays for free play / the GM.
  const inEncounter =
    !!encounter?.active && encounter.entries.some((e) => e.playerId === selectedPlayerId);
  const isMyTurn = inEncounter && encounter!.currentPlayerId === selectedPlayerId;
  const endBlocked = inEncounter && !isMyTurn;

  function submitDamage() {
    const v = parsePositive(dmgValue);
    if (!v) return;
    const might = parsePositive(dmgMight);
    // Damage dealt to someone else is attributed to this sheet unless a monster attacker is
    // named — so taunts on this character are enforced and wounded-by is recorded.
    const attacker = dmgAttacker || (targetingOther ? selectedPlayerId! : undefined);
    void doTargetedDamage(effectiveTarget, v, dmgType, undefined, might ?? undefined, attacker);
  }

  function submitHeal() {
    const v = parsePositive(healValue);
    if (v) void doTargetedHeal(effectiveTarget, v);
  }

  function submitEffect() {
    if (!fxId) return;
    void doTargetedApplyEffect(effectiveTarget, {
      effectId: fxId,
      stacks: parsePositive(fxStacks) ?? 1,
      value: parsePositive(fxValue) ?? undefined,
      duration: parsePositive(fxDuration) ?? undefined,
      // Effects put on someone else are attributed to this sheet — that is what makes a
      // taunt work (the effect's source IS the taunter) and what wounded-by reads.
      source: targetingOther ? selectedPlayerId! : 'sheet',
    });
  }

  return (
    <>
      <EncounterTracker />

      {error && <p className="inline-error">{error}</p>}

      {snapshot.status === 'DOWNED' && (
        <div className="life-banner life-banner--downed">
          <div className="life-banner-text">
            <strong>DOWNED</strong> — {snapshot.downedRoundsRemaining} round
            {snapshot.downedRoundsRemaining === 1 ? '' : 's'} to revive · Medicine check <strong>DC {snapshot.reviveDC}</strong> (3 AP)
          </div>
          <div className="life-banner-actions">
            <button className="btn btn--gold" onClick={() => void doRevive({})} disabled={acting}>
              Revive (1 HP)
            </button>
            <button
              className="btn btn--ghost life-danger"
              onClick={() => void doRevive({ criticalFail: true })}
              disabled={acting}
            >
              Crit fail
            </button>
          </div>
        </div>
      )}

      {snapshot.status === 'DEAD' && (
        <div className="life-banner life-banner--dead">
          <div className="life-banner-text">
            <strong>DEAD</strong>
            {snapshot.pendingDeathFight && ' — Death fight pending (after this combat)'}
            {snapshot.deathStacks > 0 && ` · ${snapshot.deathStacks} death stack${snapshot.deathStacks === 1 ? '' : 's'}`}
          </div>
          <div className="life-banner-actions">
            <button
              className="btn btn--gold"
              title="Return from a won Death fight: +1 death stack, revert HP DM-side"
              onClick={() => void doRevive({ deathStackGained: true })}
              disabled={acting}
            >
              Death fight won
            </button>
          </div>
        </div>
      )}

      <div className="combat-hp">
        <div className="combat-hp-head">
          <span>HP</span>
          <span className="combat-hp-val">
            {snapshot.hp.current} / {snapshot.hp.max}
            {snapshot.hp.temp > 0 && <span className="combat-temp"> +{snapshot.hp.temp} temp</span>}
          </span>
        </div>
        <div className="carry-bar">
          <div
            className={hpPct <= 10 ? 'carry-fill carry-fill--over' : 'carry-fill'}
            style={{ width: `${Math.max(0, Math.min(100, hpPct))}%` }}
          />
        </div>
        {(snapshot.conditions.length > 0 || snapshot.deathStacks > 0 || snapshot.proficiencyPenalties.length > 0) && (
          <div className="combat-conditions">
            {snapshot.conditions.map((c) => (
              <span className="combat-condition" key={c}>
                {camelToWords(c)}
              </span>
            ))}
            {snapshot.proficiencyPenalties.map((p) => (
              <span className="combat-condition" key={`pen-${p.itemId}`} title={p.penalty}>
                ⚠ {p.itemId} (no proficiency)
              </span>
            ))}
            {snapshot.deathStacks > 0 && (
              <span
                className="combat-death-stacks"
                title={`${snapshot.deathStacks} Death fight${snapshot.deathStacks === 1 ? '' : 's'} won — Death grows stronger each time`}
              >
                ☠ death ×{snapshot.deathStacks}
              </span>
            )}
          </div>
        )}
      </div>

      <div className="combat-actions">
        {(party.length > 0 || foes.length > 0) && (
          <div className={targetingOther ? 'combat-form combat-form--target' : 'combat-form'}>
            <span className="combat-form-label">Target</span>
            <select
              value={effectiveTarget === selectedPlayerId ? '' : effectiveTarget}
              onChange={(e) => setTargetId(e.target.value)}
              title="Damage, heal, and effects below apply to this combatant"
            >
              <option value="">{snapshot.name} (this sheet)</option>
              {party.length > 0 && (
                <optgroup label="Party">
                  {party.map((r) => (
                    <option key={r.playerId} value={r.playerId}>
                      {r.name}
                    </option>
                  ))}
                </optgroup>
              )}
              {foes.length > 0 && (
                <optgroup label="Monsters">
                  {foes.map((m) => (
                    <option key={m.combatantId} value={m.combatantId}>
                      {m.name} · {m.hp.current}/{m.hp.max} HP
                    </option>
                  ))}
                </optgroup>
              )}
            </select>
            {targetingOther && (
              <span className="combat-target-note">
                {targetingMonster
                  ? 'Damage, heal & effects hit the monster — the GM board and turn order update'
                  : 'Damage, heal & effects hit them — their sheet updates live'}
              </span>
            )}
          </div>
        )}

        {snapshot.equippedWeapons.length > 0 && (
          <div className="combat-form">
            <span className="combat-form-label">Attack</span>
            {snapshot.equippedWeapons.length > 1 && (
              <select value={attackWeapon} onChange={(e) => setAttackWeapon(e.target.value)}>
                <option value="">Choose a weapon…</option>
                {snapshot.equippedWeapons.map((id) => (
                  <option key={id} value={id}>
                    {itemLabel(id, customItems)}
                  </option>
                ))}
              </select>
            )}
            <button
              className="btn btn--gold"
              title={
                targetingOther
                  ? 'd20 + proficiency + weapon stat vs the target’s AC; a hit lands the damage on them'
                  : 'd20 + proficiency + weapon stat (when proficient); crits double the damage. Pick a Target above to resolve the hit on them.'
              }
              onClick={() =>
                void doWeaponAttack(
                  snapshot.equippedWeapons.length > 1 ? attackWeapon || undefined : undefined,
                  targetingOther ? effectiveTarget : undefined,
                )
              }
              disabled={acting || (snapshot.equippedWeapons.length > 1 && !attackWeapon)}
            >
              {targetingOther
                ? `Attack ${targetLabel(effectiveTarget)}`
                : snapshot.equippedWeapons.length === 1
                  ? `Attack (${itemLabel(snapshot.equippedWeapons[0], customItems)})`
                  : 'Attack'}
            </button>
          </div>
        )}

        <div className="combat-form">
          <span className="combat-form-label">Damage</span>
          <input
            className="combat-num"
            type="number"
            min={1}
            value={dmgValue}
            onChange={(e) => setDmgValue(e.target.value)}
          />
          <select value={dmgType} onChange={(e) => setDmgType(e.target.value as DamageTypeId)}>
            {DAMAGE_TYPE_OPTIONS.map((t) => (
              <option key={t.id} value={t.id}>
                {t.label} ({t.category})
              </option>
            ))}
          </select>
          {foes.length > 0 && (
            <select
              value={dmgAttacker}
              onChange={(e) => setDmgAttacker(e.target.value)}
              title="Who dealt it — a monster attacker supplies its might (concentration DC) and is recorded as the source"
            >
              <option value="">attacker: unnamed</option>
              {foes.map((m) => (
                <option key={m.combatantId} value={m.combatantId}>
                  attacker: {m.name}
                  {m.might != null ? ` (might ${m.might})` : ''}
                </option>
              ))}
            </select>
          )}
          <input
            className="combat-num"
            type="number"
            min={0}
            title="Attacker's might — rolls the concentration-break WILL save (DC 5 + might); empty = the named attacker's might, or the DM resolves manually"
            placeholder="might"
            value={dmgMight}
            onChange={(e) => setDmgMight(e.target.value)}
          />
          <button className="btn btn--gold" onClick={submitDamage} disabled={acting}>
            Apply
          </button>
        </div>

        <div className="combat-form">
          <span className="combat-form-label">Heal</span>
          <input
            className="combat-num"
            type="number"
            min={1}
            value={healValue}
            onChange={(e) => setHealValue(e.target.value)}
          />
          <button className="btn btn--gold" onClick={submitHeal} disabled={acting}>
            Apply
          </button>
        </div>

        <div className="combat-form">
          <span className="combat-form-label">AP</span>
          <span className="combat-resource-val" title="Current / max AP (recovery at turn start)">
            {snapshot.ap.current} / {snapshot.ap.max}
          </span>
          <input
            className="combat-num"
            type="number"
            min={1}
            value={apSpend}
            onChange={(e) => setApSpend(e.target.value)}
          />
          <input
            className="combat-text"
            type="text"
            maxLength={120}
            placeholder="on what? (optional — e.g. moved 20 ft)"
            value={apNote}
            onChange={(e) => setApNote(e.target.value)}
          />
          <button
            className="btn btn--ghost"
            title="Validated: rejects if you have less AP than the amount"
            onClick={() => {
              const v = parsePositive(apSpend);
              if (!v) return;
              void doSpendResource('ap', v, apNote.trim() || undefined);
              setApNote('');
            }}
            disabled={acting}
          >
            Spend
          </button>
        </div>

        <div className="combat-form">
          <span className="combat-form-label">Prepare</span>
          <input
            className="combat-num"
            type="number"
            min={0}
            title="AP paid now, on this turn — the prep IS the cost (0 = free by ruling)"
            value={prepCost}
            onChange={(e) => setPrepCost(e.target.value)}
          />
          <input
            className="combat-text"
            type="text"
            maxLength={120}
            placeholder="reaction — e.g. roll out of the way when the ogre swings"
            value={prepNote}
            onChange={(e) => setPrepNote(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && prepNote.trim() && !acting) {
                void doPrepareReaction(prepNote.trim(), Math.max(0, Number.parseInt(prepCost, 10) || 0));
                setPrepNote('');
              }
            }}
          />
          <button
            className="btn btn--gold"
            title="Ready a custom reaction: the AP is spent now; the table sees it (⚑ in the tracker) until it triggers, you cancel it, or your next turn starts"
            onClick={() => {
              const note = prepNote.trim();
              if (!note) return;
              void doPrepareReaction(note, Math.max(0, Number.parseInt(prepCost, 10) || 0));
              setPrepNote('');
            }}
            disabled={acting || !prepNote.trim()}
          >
            Prepare
          </button>
        </div>
        {snapshot.preparedReactions.length > 0 && (
          <div className="combat-effect-list combat-prepared-list">
            {snapshot.preparedReactions.map((r, i) => (
              <span className="combat-effect combat-prepared" key={`${r.note}-${i}`}>
                <span className="combat-effect-name">⚑ {r.note}</span>
                <span className="combat-effect-meta">{r.apCost > 0 ? `${r.apCost} AP` : 'free'}</span>
                <button
                  className="combat-effect-remove combat-prepared-used"
                  title="It triggered — resolve the outcome at the table (no refund)"
                  onClick={() => void doResolveReaction(i, true)}
                  disabled={acting}
                >
                  used
                </button>
                <button
                  className="combat-effect-remove"
                  title="Call it off — the AP stays spent"
                  onClick={() => void doResolveReaction(i, false)}
                  disabled={acting}
                >
                  ×
                </button>
              </span>
            ))}
          </div>
        )}

        {snapshot.pools.map((pool) => (
          <PoolRow
            key={pool.id}
            pool={pool}
            acting={acting}
            onSpend={(v) => void doSpendResource(pool.id, v)}
            onGain={(v) => void doGainResource(pool.id, v)}
          />
        ))}

        {snapshot.resource && (
          <div className="combat-form">
            <span className="combat-form-label">{camelToWords(snapshot.resource.type).replace(/-/g, ' ')}</span>
            <span className="combat-resource-val">
              {snapshot.resource.current}
              {snapshot.resource.max != null ? ` / ${snapshot.resource.max}` : ' / ∞'}
            </span>
            <input
              className="combat-num"
              type="number"
              min={1}
              value={resAmount}
              onChange={(e) => setResAmount(e.target.value)}
            />
            <button
              className="btn btn--ghost"
              title="Validated: rejects if the pool has less than the amount"
              onClick={() => {
                const v = parsePositive(resAmount);
                if (v && snapshot.resource) void doSpendResource(snapshot.resource.type, v);
              }}
              disabled={acting}
            >
              Spend
            </button>
            <button
              className="btn btn--ghost"
              title="Capped at the derived max (builders are unbounded)"
              onClick={() => {
                const v = parsePositive(resAmount);
                if (v && snapshot.resource) void doGainResource(snapshot.resource.type, v);
              }}
              disabled={acting}
            >
              Gain
            </button>
          </div>
        )}

        <div className="combat-form">
          <span className="combat-form-label">Turn</span>
          {!inEncounter && (
            <button
              className="btn btn--ghost"
              title="Free-play tick: DoTs, then AP recovery (turns start automatically in combat)"
              onClick={() => void doTurnStart()}
              disabled={acting}
            >
              Start (AP)
            </button>
          )}
          <button
            className="btn btn--ghost"
            title={endBlocked ? 'Not your turn yet' : 'End your turn: HoTs tick, durations expire, play passes on'}
            onClick={() => void doTurnEnd()}
            disabled={acting || endBlocked}
          >
            End turn
          </button>
          {role === 'gm' && (
            <button
              className="btn btn--ghost"
              title="Combat start: AP to starting value, revive-DC counter reset (encounter start does this for everyone)"
              onClick={() => void doCombatStart()}
              disabled={acting}
            >
              Combat start
            </button>
          )}
        </div>

        <div className="combat-form">
          <span className="combat-form-label">Rest</span>
          <input
            className="combat-num"
            type="number"
            min={0}
            max={100}
            title="Rest quality, 0–100% — restores that share of HP/mana/resources (GM sets the number)"
            value={restTier}
            onChange={(e) => setRestTier(e.target.value)}
          />
          <span className="combat-form-label">%</span>
          <button
            className="btn btn--gold"
            title="Restores HP/mana/resources by the given %; clears until-rest effects and all accumulated stacks"
            onClick={() => {
              const t = Number.parseInt(restTier, 10);
              if (!Number.isNaN(t)) void doRest(Math.max(0, Math.min(100, t)));
            }}
            disabled={acting}
          >
            Rest
          </button>
        </div>

        <div className="combat-form combat-form--effect">
          <span className="combat-form-label">Effect</span>
          <select value={fxId} onChange={(e) => setFxId(e.target.value)}>
            {EFFECT_OPTIONS.map((e) => (
              <option key={e.id} value={e.id}>
                {e.polarity === 'negative' ? '▼' : '▲'} {e.name}
              </option>
            ))}
          </select>
          <input
            className="combat-num"
            type="number"
            min={1}
            title="Stacks"
            placeholder="stacks"
            value={fxStacks}
            onChange={(e) => setFxStacks(e.target.value)}
          />
          {selectedFx?.hasValue && (
            <input
              className="combat-num"
              type="number"
              min={1}
              title="Value (required for this effect)"
              placeholder="value"
              value={fxValue}
              onChange={(e) => setFxValue(e.target.value)}
            />
          )}
          <input
            className="combat-num"
            type="number"
            min={1}
            title="Duration in rounds (optional — direct active window)"
            placeholder="rounds"
            value={fxDuration}
            onChange={(e) => setFxDuration(e.target.value)}
          />
          <button className="btn btn--gold" onClick={submitEffect} disabled={acting || !fxId}>
            Apply
          </button>
        </div>
      </div>

      <div className="combat-effects">
        <h3 className="combat-section-title">Active effects</h3>
        {snapshot.activeEffects.length === 0 && <p className="deck-empty">None.</p>}
        <div className="combat-effect-list">
          {snapshot.activeEffects.map((e, i) => (
            <span
              className={e.active ? 'combat-effect' : 'combat-effect combat-effect--dormant'}
              key={`${e.id}-${i}`}
              title={
                e.threshold == null
                  ? undefined
                  : e.active
                    ? `Firing — ${e.threshold} stacks are consumed at the end of this turn`
                    : `Dormant — ${e.threshold - e.stacks} more stack(s) to reach the threshold of ${e.threshold}`
              }
            >
              <HoverInfo info={effectOption(e.id)?.description}>
                <span className="combat-effect-name">{effectName(e.id)}</span>
              </HoverInfo>
              {/* Threshold-gated effects show stacks against the bar they must clear. */}
              {e.threshold != null ? (
                <span className="combat-effect-meta">
                  {e.stacks}/{e.threshold}
                </span>
              ) : (
                <span className="combat-effect-meta">×{e.stacks}</span>
              )}
              {e.threshold != null && !e.active && (
                <span className="combat-effect-meta combat-effect-meta--dormant">dormant</span>
              )}
              {e.value != null && <span className="combat-effect-meta">({e.value})</span>}
              {e.rounds != null && <span className="combat-effect-meta">{e.rounds}r</span>}
              <button
                className="combat-effect-remove"
                title={`Remove ${effectName(e.id)}`}
                onClick={() => void doRemoveEffect(e.id)}
                disabled={acting}
              >
                ×
              </button>
            </span>
          ))}
        </div>
      </div>

      {lastResolution && (
        <ResolutionLog
          resolution={lastResolution}
          targetName={lastResolutionTarget}
          onClose={clearResolution}
        />
      )}

      <CombatLog resolutionKey={lastResolution} />
    </>
  );
}

/**
 * The player's own combat history (demo feedback #22). Self-scoped and combat-only by
 * construction — this is the fight recap you scroll back through when the table asks
 * "wait, how much did that hit me for?", not a table-wide feed.
 */
function CombatLog({ resolutionKey }: { resolutionKey: unknown }) {
  const playerId = useCharacterStore((s) => s.selectedPlayerId);
  const [open, setOpen] = useState(false);
  const [entries, setEntries] = useState<AuditView[] | null>(null);
  const [failed, setFailed] = useState(false);

  const refresh = useCallback(async () => {
    if (!playerId) return;
    try {
      setFailed(false);
      setEntries(await fetchCombatLog(playerId));
    } catch {
      setFailed(true);
    }
  }, [playerId]);

  // Reload on open, on character switch, and whenever an action resolves — a log that
  // goes stale the moment you act is worse than no log.
  useEffect(() => {
    if (open) void refresh();
  }, [open, refresh, resolutionKey]);

  if (!playerId) return null;

  return (
    <div className="audit">
      <div className="audit-head">
        <button className="btn btn--ghost" onClick={() => setOpen(!open)}>
          {open ? '▾ My combat log' : '▸ My combat log'}
        </button>
        {open && (
          <button className="btn btn--ghost" onClick={() => void refresh()}>
            Refresh
          </button>
        )}
      </div>
      {open && failed && <p className="inline-error">Could not load your combat log.</p>}
      {open && entries && entries.length === 0 && <p className="deck-empty">Nothing yet.</p>}
      {open && entries && entries.length > 0 && (
        <div className="audit-list">
          {entries.map((e, i) => (
            <div className="audit-row" key={i}>
              <span className="audit-time">
                {new Date(e.time).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
              </span>
              <span className="audit-summary">{e.summary}</span>
              <span className="audit-action">{e.action}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

/** One sub-resource pool (perseverance/fury/…) with its own spend/gain amount. */
function PoolRow({
  pool,
  acting,
  onSpend,
  onGain,
}: {
  pool: PoolView;
  acting: boolean;
  onSpend: (amount: number) => void;
  onGain: (amount: number) => void;
}) {
  const [amount, setAmount] = useState('1');
  const negative = pool.current < 0;

  return (
    <div className="combat-form">
      <span className="combat-form-label">{pool.name}</span>
      <span
        className={'combat-resource-val' + (negative ? ' combat-resource-val--danger' : '')}
        title={negative ? 'Pool is negative — disaster rule, DM adjudicates' : undefined}
      >
        {pool.current}
        {pool.max != null ? ` / ${pool.max}` : ' / ∞'}
      </span>
      <input
        className="combat-num"
        type="number"
        min={1}
        value={amount}
        onChange={(e) => setAmount(e.target.value)}
      />
      <button
        className="btn btn--ghost"
        onClick={() => {
          const v = parsePositive(amount);
          if (v) onSpend(v);
        }}
        disabled={acting}
      >
        Spend
      </button>
      <button
        className="btn btn--ghost"
        onClick={() => {
          const v = parsePositive(amount);
          if (v) onGain(v);
        }}
        disabled={acting}
      >
        Gain
      </button>
    </div>
  );
}
