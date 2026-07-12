import { useState } from 'react';
import { useCharacterStore } from '../application/characterStore';
import type { DamageTypeId } from '../platform/types';
import { DAMAGE_TYPE_OPTIONS, EFFECT_OPTIONS, effectName, effectOption } from '../domain/combatCatalog';
import { itemName } from '../domain/itemCatalog';
import { camelToWords } from '../domain/stats';
import { EncounterTracker } from './EncounterTracker';
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
  const doDamage = useCharacterStore((s) => s.doDamage);
  const doHeal = useCharacterStore((s) => s.doHeal);
  const doTurnStart = useCharacterStore((s) => s.doTurnStart);
  const doTurnEnd = useCharacterStore((s) => s.doTurnEnd);
  const doSpendResource = useCharacterStore((s) => s.doSpendResource);
  const doGainResource = useCharacterStore((s) => s.doGainResource);
  const encounter = useCharacterStore((s) => s.encounter);
  const selectedPlayerId = useCharacterStore((s) => s.selectedPlayerId);
  const doApplyEffect = useCharacterStore((s) => s.doApplyEffect);
  const doRemoveEffect = useCharacterStore((s) => s.doRemoveEffect);
  const doRevive = useCharacterStore((s) => s.doRevive);
  const doCombatStart = useCharacterStore((s) => s.doCombatStart);
  const doRest = useCharacterStore((s) => s.doRest);
  const clearResolution = useCharacterStore((s) => s.clearResolution);

  const doWeaponAttack = useCharacterStore((s) => s.doWeaponAttack);
  const [dmgValue, setDmgValue] = useState('10');
  const [dmgType, setDmgType] = useState<DamageTypeId>('SLASHING');
  const [dmgMight, setDmgMight] = useState('');
  const [attackWeapon, setAttackWeapon] = useState('');
  const [healValue, setHealValue] = useState('10');
  const [fxId, setFxId] = useState(EFFECT_OPTIONS[0]?.id ?? '');
  const [fxStacks, setFxStacks] = useState('1');
  const [fxValue, setFxValue] = useState('');
  const [fxDuration, setFxDuration] = useState('');
  const [restTier, setRestTier] = useState('100');
  const [resAmount, setResAmount] = useState('1');

  if (!snapshot) return <div className="panel-msg">No character loaded.</div>;

  const hpPct = snapshot.hp.max > 0 ? (snapshot.hp.current / snapshot.hp.max) * 100 : 0;
  const selectedFx = effectOption(fxId);

  // Turn gating mirrors the server rules: within an encounter, only the current
  // character may start, and end requires a started turn. Non-participants tick freely.
  const inEncounter =
    !!encounter?.active && encounter.entries.some((e) => e.playerId === selectedPlayerId);
  const isMyTurn = inEncounter && encounter!.currentPlayerId === selectedPlayerId;
  const startBlocked = inEncounter && (!isMyTurn || encounter!.turnStarted);
  const endBlocked = inEncounter && (!isMyTurn || !encounter!.turnStarted);

  function submitDamage() {
    const v = parsePositive(dmgValue);
    if (!v) return;
    const might = parsePositive(dmgMight);
    void doDamage(v, dmgType, undefined, might ?? undefined);
  }

  function submitHeal() {
    const v = parsePositive(healValue);
    if (v) void doHeal(v);
  }

  function submitEffect() {
    if (!fxId) return;
    void doApplyEffect({
      effectId: fxId,
      stacks: parsePositive(fxStacks) ?? 1,
      value: parsePositive(fxValue) ?? undefined,
      duration: parsePositive(fxDuration) ?? undefined,
      source: 'sheet',
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
        {snapshot.equippedWeapons.length > 0 && (
          <div className="combat-form">
            <span className="combat-form-label">Attack</span>
            {snapshot.equippedWeapons.length > 1 && (
              <select value={attackWeapon} onChange={(e) => setAttackWeapon(e.target.value)}>
                <option value="">Choose a weapon…</option>
                {snapshot.equippedWeapons.map((id) => (
                  <option key={id} value={id}>
                    {itemName(id)}
                  </option>
                ))}
              </select>
            )}
            <button
              className="btn btn--gold"
              title="d20 + proficiency + weapon stat (when proficient); crits double the damage"
              onClick={() =>
                void doWeaponAttack(
                  snapshot.equippedWeapons.length > 1 ? attackWeapon || undefined : undefined,
                )
              }
              disabled={acting || (snapshot.equippedWeapons.length > 1 && !attackWeapon)}
            >
              {snapshot.equippedWeapons.length === 1
                ? `Attack (${itemName(snapshot.equippedWeapons[0])})`
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
          <input
            className="combat-num"
            type="number"
            min={0}
            title="Attacker's might — rolls the concentration-break WILL save (DC 5 + might); empty = DM resolves manually"
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
          <button
            className="btn btn--ghost"
            title={startBlocked ? (isMyTurn ? 'Turn already started' : 'Not your turn yet') : undefined}
            onClick={() => void doTurnStart()}
            disabled={acting || startBlocked}
          >
            Start (AP)
          </button>
          <button
            className="btn btn--ghost"
            title={endBlocked ? (isMyTurn ? 'Start your turn first' : 'Not your turn yet') : undefined}
            onClick={() => void doTurnEnd()}
            disabled={acting || endBlocked}
          >
            End (tick)
          </button>
          <button
            className="btn btn--ghost"
            title="Combat start: AP to starting value, revive-DC counter reset"
            onClick={() => void doCombatStart()}
            disabled={acting}
          >
            Combat start
          </button>
        </div>

        <div className="combat-form">
          <span className="combat-form-label">Rest</span>
          <select value={restTier} onChange={(e) => setRestTier(e.target.value)}>
            <option value="25">Poor (25%)</option>
            <option value="50">Modest (50%)</option>
            <option value="75">Good (75%)</option>
            <option value="100">Full (100%)</option>
          </select>
          <button
            className="btn btn--gold"
            title="Restores HP/mana/resources by tier; clears until-rest effects and all accumulated stacks"
            onClick={() => void doRest(Number.parseInt(restTier, 10))}
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
            <span className="combat-effect" key={`${e.id}-${i}`}>
              <span className="combat-effect-name">{effectName(e.id)}</span>
              {e.stacks > 1 && <span className="combat-effect-meta">×{e.stacks}</span>}
              {e.stacks === 1 && <span className="combat-effect-meta">×1</span>}
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

      {lastResolution && <ResolutionLog resolution={lastResolution} onClose={clearResolution} />}
    </>
  );
}
