import { useEffect, useState } from 'react';
import type { ResolutionResult, RollBreakdown } from '../platform/types';
import { effectName } from '../domain/combatCatalog';
import { useCharacterStore } from '../application/characterStore';
import { DiceRoll } from './DiceRoll';

/** Step-by-step action resolution + cast payload (dice, DC, pending effects). */
export function ResolutionLog({
  resolution,
  targetName,
  onClose,
}: {
  resolution: ResolutionResult;
  /** Party member the action targeted, when it wasn't the viewed character. */
  targetName?: string | null;
  onClose: () => void;
}) {
  const roster = useCharacterStore((s) => s.roster);
  const payload = resolution.payload;
  const natural = payload?.attackRoll?.roll ?? null;
  // Animate once per resolution: keying the die on the resolution object means a
  // re-render (roster arriving, parent state) doesn't re-roll a settled attack.
  const [rolling, setRolling] = useState(false);
  useEffect(() => {
    setRolling(natural != null);
  }, [resolution, natural]);
  // The server reports raw player ids — show the character's name when we know it.
  const appliedToName =
    payload?.effectsAppliedTo &&
    (roster.find((r) => r.playerId === payload.effectsAppliedTo)?.name ?? payload.effectsAppliedTo);
  return (
    <div className="combat-log">
      <div className="combat-log-head">
        <h3 className="combat-section-title">
          Resolution
          {targetName && <span className="combat-log-target"> → {targetName}</span>}
        </h3>
        <button className="btn btn--ghost" onClick={onClose}>
          Clear
        </button>
      </div>

      {payload && (payload.attackRoll || payload.damage || payload.healing) && (
        <div className="cast-results">
          {payload.attackRoll && (
            <div
              className={
                payload.attackRoll.critical
                  ? 'cast-roll cast-roll--crit'
                  : payload.attackRoll.criticalFailure || payload.attackRoll.autoMiss
                    ? 'cast-roll cast-roll--fumble'
                    : 'cast-roll'
              }
            >
              <span className="cast-roll-label">
                {payload.weapon ? `${payload.weapon.name} vs AC` : 'Attack vs AC'}
              </span>
              {payload.attackRoll.autoMiss ? (
                <span className="cast-warn">stacked disadvantage — automatic miss</span>
              ) : (
                <>
                  {natural != null && (
                    <span className="cast-roll-die">
                      <DiceRoll
                        result={natural}
                        rolling={rolling}
                        sides={20}
                        size={54}
                        onRollComplete={() => setRolling(false)}
                      />
                    </span>
                  )}
                  <span className="cast-roll-dice">
                    {(payload.attackRoll.rolls ?? [payload.attackRoll.roll ?? 0]).map((r, i) => (
                      <span
                        className={
                          r === payload.attackRoll?.roll ? 'cast-die' : 'cast-die cast-die--dropped'
                        }
                        key={i}
                      >
                        {r}
                      </span>
                    ))}
                    <span className="cast-roll-part">+{payload.attackRoll.bonus}</span>
                    {payload.attackRoll.advantage && <span className="cast-roll-part">adv.</span>}
                    {payload.attackRoll.disadvantage && <span className="cast-roll-part">disadv.</span>}
                  </span>
                  <span className="cast-roll-total">
                    {payload.attackRoll.criticalFailure ? 'MISS' : payload.attackRoll.total}
                  </span>
                  {payload.attackRoll.critical && <span className="cast-crit">CRIT</span>}
                  {payload.attackRoll.criticalFailure && <span className="cast-warn">nat 1</span>}
                </>
              )}
            </div>
          )}
          {payload.damage && (
            <RollCard
              label={`Damage${payload.damageType ? ` (${payload.damageType})` : ''}`}
              roll={payload.damage}
            />
          )}
          {payload.healing && <RollCard label="Healing" roll={payload.healing} />}
        </div>
      )}

      {payload && (payload.saveDC != null || payload.attackBonus != null) && (
        <p className="cast-numbers">
          {payload.saveDC != null && payload.saveDC > 0 && (
            <span>
              Save DC <strong>{payload.saveDC}</strong>
            </span>
          )}
          {payload.attackBonus != null && payload.attackBonus > 0 && (
            <span>
              Spell attack <strong>+{payload.attackBonus}</strong>
            </span>
          )}
          {payload.concentrationDropped && <span className="cast-warn">previous concentration dropped</span>}
        </p>
      )}

      {payload?.effectsOnHit && payload.effectsOnHit.length > 0 && (
        <p className="cast-numbers">
          On hit:{' '}
          <strong>
            {payload.effectsOnHit
              .map((e) => {
                const duration = e.rounds
                  ? ` (${e.rounds} rounds)`
                  : e.durationType === 'UNTIL_LONG_REST'
                    ? ' (until rest)'
                    : '';
                return `${e.name}${duration}`;
              })
              .join(', ')}
          </strong>
        </p>
      )}
      {appliedToName && (
        <p className="cast-numbers">
          Effects applied to <strong>{appliedToName}</strong>
        </p>
      )}
      {payload?.newAbilities && payload.newAbilities.length > 0 && (
        <p className="cast-numbers">
          Newly unlocked: <strong>{payload.newAbilities.join(', ')}</strong>
        </p>
      )}

      {resolution.steps.length === 0 && <p className="deck-empty">No steps — nothing to resolve.</p>}
      <ol className="combat-steps">
        {resolution.steps.map((s, i) => (
          <li className="combat-step" key={i}>
            <span className="combat-step-rule">{s.rule}</span>
            <span className="combat-step-note">{s.note}</span>
            <span className="combat-step-delta">
              {s.valueBefore} → {s.valueAfter}
            </span>
          </li>
        ))}
      </ol>
      {resolution.effectsTriggered.length > 0 && (
        <p className="combat-triggered">
          Triggered: {resolution.effectsTriggered.map(triggeredLabel).join(', ')}
        </p>
      )}
    </div>
  );
}

/** effectsTriggered entries can be prefixed ("removed:sleep", "broken:concentrating"). */
function triggeredLabel(entry: string): string {
  const idx = entry.indexOf(':');
  if (idx < 0) return effectName(entry);
  return `${entry.slice(0, idx)} ${effectName(entry.slice(idx + 1))}`;
}

function RollCard({ label, roll }: { label: string; roll: RollBreakdown }) {
  return (
    <div className="cast-roll">
      <span className="cast-roll-label">{label}</span>
      <span className="cast-roll-dice">
        {roll.rolls.map((r, i) => (
          <span className="cast-die" key={i}>
            {r}
          </span>
        ))}
        {roll.flat !== 0 && <span className="cast-roll-part">+{roll.flat}</span>}
        {roll.modifier !== 0 && <span className="cast-roll-part">+{roll.modifier} mod</span>}
        {roll.weaponDamage != null && roll.weaponDamage !== 0 && (
          <span className="cast-roll-part">+{roll.weaponDamage} weapon</span>
        )}
        {roll.critMultiplier != null && (
          <span className="cast-crit">×{roll.critMultiplier} CRIT</span>
        )}
      </span>
      <span className="cast-roll-total">{roll.total}</span>
    </div>
  );
}
