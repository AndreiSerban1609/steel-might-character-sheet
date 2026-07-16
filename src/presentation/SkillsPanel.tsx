import { useState, useEffect } from 'react';
import { useCharacterStore } from '../application/characterStore';
import { ABILITY_LABELS, formatModifier } from '../domain/stats';
import type { AbilityScore, SkillCheckResult } from '../platform/types';
import { CardFace } from './CardFace';
import { DiceRoll } from './DiceRoll';
import skillsRaw from '../data/skills.json';

interface SkillDef {
  id: string;
  name: string;
  ability: string;
}

const SKILLS = skillsRaw as unknown as SkillDef[];
const SKILL_NAME = new Map(SKILLS.map((s) => [s.id, s.name]));

const ABILITY_UP: Record<string, AbilityScore> = {
  str: 'STR',
  dex: 'DEX',
  con: 'CON',
  int: 'INT',
  wis: 'WIS',
  will: 'WILL',
  cha: 'CHA',
};
const GROUP_ORDER: AbilityScore[] = ['STR', 'DEX', 'INT', 'WIS', 'CHA', 'WILL'];

type AdvMode = 'none' | 'advantage' | 'disadvantage';

export function SkillsPanel() {
  const snapshot = useCharacterStore((s) => s.snapshot);
  const saving = useCharacterStore((s) => s.saving);
  const error = useCharacterStore((s) => s.error);
  const drawing = useCharacterStore((s) => s.drawing);
  const drawResult = useCharacterStore((s) => s.drawResult);
  const saveProficiencies = useCharacterStore((s) => s.saveProficiencies);
  const drawSkill = useCharacterStore((s) => s.drawSkill);
  const redrawSkill = useCharacterStore((s) => s.redrawSkill);
  const clearDraw = useCharacterStore((s) => s.clearDraw);

  const [draft, setDraft] = useState<Set<string> | null>(null);
  // Chosen BEFORE the draw (GM rule); resets to a normal draw after each check starts.
  const [advMode, setAdvMode] = useState<AdvMode>('none');

  if (!snapshot) return <div className="panel-msg">No character loaded.</div>;

  const editing = draft !== null;
  const proficient = draft ?? new Set(snapshot.proficiencies);

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
    await saveProficiencies([...draft]);
    if (!useCharacterStore.getState().error) setDraft(null);
  }

  const byAbility = new Map<AbilityScore, SkillDef[]>();
  for (const sk of SKILLS) {
    const ab = ABILITY_UP[sk.ability];
    if (!ab) continue;
    const list = byAbility.get(ab) ?? [];
    list.push(sk);
    byAbility.set(ab, list);
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
          <button className="btn btn--ghost" onClick={() => setDraft(new Set(snapshot.proficiencies))}>
            Edit
          </button>
        )}
      </div>

      {drawResult && !editing && (
        <DrawBanner
          result={drawResult}
          pathId={snapshot.pathId}
          drawing={drawing}
          onRedraw={() => void redrawSkill()}
          onClose={clearDraw}
        />
      )}

      <p className="skills-hint">
        {editing
          ? 'Toggle the skills this character is proficient in.'
          : 'Click a skill to draw a card. A Stat card applies your ability modifier; proficiency (●) grants redraws equal to your proficiency bonus — the die stays.'}
      </p>
      {error && editing && <p className="inline-error">{error}</p>}

      {!editing && (
        <div className="adv-toggle">
          <span className="adv-toggle-label">Next draw:</span>
          {(['none', 'advantage', 'disadvantage'] as AdvMode[]).map((m) => (
            <button
              key={m}
              className={'adv-toggle-btn' + (advMode === m ? ' adv-toggle-btn--active' : '')}
              title={
                m === 'none'
                  ? 'One d10, as normal'
                  : m === 'advantage'
                    ? 'Roll two d10s, keep the higher'
                    : 'Roll two d10s, keep the lower'
              }
              onClick={() => setAdvMode(m)}
            >
              {m === 'none' ? 'Normal' : m === 'advantage' ? 'Advantage' : 'Disadvantage'}
            </button>
          ))}
        </div>
      )}

      <div className="skills-groups">
        {GROUP_ORDER.filter((ab) => byAbility.has(ab)).map((ab) => (
          <div className="skills-group" key={ab}>
            <h3 className="skills-group-title">
              <span>{ABILITY_LABELS[ab]}</span>
              <span className="skills-group-mod">{formatModifier(snapshot.modifiers[ab])}</span>
            </h3>
            <div className="skills-list">
              {byAbility.get(ab)!.map((sk) => {
                const isProf = proficient.has(sk.id);
                const isActive = !editing && drawResult?.skillId === sk.id;
                const cls =
                  'skill-row skill-row--click' +
                  (isProf ? ' skill-row--prof' : '') +
                  (isActive ? ' skill-row--active' : '');
                return (
                  <div
                    className={cls}
                    key={sk.id}
                    onClick={
                      editing
                        ? () => toggle(sk.id)
                        : () => {
                            void drawSkill(sk.id, advMode === 'none' ? undefined : advMode);
                            setAdvMode('none');
                          }
                    }
                  >
                    <span className="skill-prof-dot">{isProf ? '●' : '○'}</span>
                    <span className="skill-name">{sk.name}</span>
                    <span className="skill-mod">{formatModifier(snapshot.modifiers[ab])}</span>
                  </div>
                );
              })}
            </div>
          </div>
        ))}
      </div>
    </>
  );
}

function DrawBanner({
  result,
  pathId,
  drawing,
  onRedraw,
  onClose,
}: {
  result: SkillCheckResult;
  pathId: string;
  drawing: boolean;
  onRedraw: () => void;
  onClose: () => void;
}) {
  // The d10 is rolled once per check — animate it on a fresh draw only.
  // A redraw swaps the card but the die is already settled.
  const isRedraw = result.redrawsUsed > 0;
  const [rolling, setRolling] = useState(!result.critical && !isRedraw);
  const [revealed, setRevealed] = useState(result.critical || isRedraw);

  useEffect(() => {
    if (result.critical || result.redrawsUsed > 0) {
      setRolling(false);
      setRevealed(true);
    } else {
      setRolling(true);
      setRevealed(false);
    }
  }, [result]);

  return (
    <div className="draw-banner">
      <div className="draw-card-art">
        <CardFace
          key={`${result.skillId}-${result.card.name}-${result.d10}-${result.redrawsUsed}`}
          card={result.card}
          pathId={pathId}
          size={88}
          animating
        />
      </div>
      <div className="draw-info">
        <div className="draw-skill">
          {SKILL_NAME.get(result.skillId) ?? result.skillId}
          <span className="draw-ability">{result.ability}</span>
        </div>
        {result.advantage && (
          <div
            className={
              'draw-adv-chip' +
              (result.advantage === 'disadvantage' ? ' draw-adv-chip--dis' : '')
            }
          >
            {result.advantage === 'advantage' ? 'Advantage — higher die' : 'Disadvantage — lower die'}
          </div>
        )}
        {result.critical ? (
          <div className="draw-outcome draw-outcome--crit">The GM decides</div>
        ) : (
          <div className="draw-outcome">
            <span className="draw-dice">
              {result.d10Rolls.map((roll, i) => {
                const usedIndex = result.d10Rolls.indexOf(result.d10);
                const discarded = result.d10Rolls.length > 1 && i !== usedIndex;
                return (
                  <span
                    key={i}
                    className={'draw-die' + (discarded && revealed ? ' draw-die--discarded' : '')}
                    title={discarded && revealed ? 'Discarded roll' : undefined}
                  >
                    <DiceRoll
                      result={roll}
                      rolling={rolling}
                      size={54}
                      onRollComplete={
                        i === usedIndex
                          ? () => {
                              setRolling(false);
                              setRevealed(true);
                            }
                          : undefined
                      }
                    />
                  </span>
                );
              })}
            </span>
            <span className={'draw-eq' + (revealed ? ' draw-eq--shown' : '')}>
              <span className="draw-piece">{formatModifier(result.effectiveModifier ?? 0)}</span>
              {result.bonusTotal !== 0 && (
                <span className="draw-piece">{formatModifier(result.bonusTotal)}</span>
              )}
              <span className="draw-piece">=</span>
              <span className="draw-total">{result.total}</span>
            </span>
          </div>
        )}
        {result.passedCards.length > 0 && (
          <div className="draw-passed">
            Passed:{' '}
            {result.passedCards
              .map((p) =>
                p.reason === 'wrong-check'
                  ? `${p.card.name} (wrong check)`
                  : `${p.card.name} (${formatModifier(p.card.redrawModifier ?? 0)} bonus)`,
              )
              .join(' · ')}
          </div>
        )}
        {result.redrawBonuses.length > 0 && (
          <div className="draw-bonuses">
            {result.redrawBonuses.map((b, i) => (
              <span className="draw-bonus-chip" key={i}>
                {b.name} {formatModifier(b.modifier)}
              </span>
            ))}
          </div>
        )}
        {result.card.removal && (
          <div className="draw-removal-note">
            {result.card.removal === 'burn'
              ? 'This card is burned when accepted — gone for good.'
              : 'This card is consumed when accepted — back after a rest.'}
          </div>
        )}
        <div className="draw-actions">
          {result.proficient && (
            <button
              className="btn btn--ghost"
              onClick={onRedraw}
              disabled={drawing || result.redrawsRemaining === 0}
            >
              {drawing ? '…' : `Redraw (${result.redrawsRemaining} left)`}
            </button>
          )}
          <button className="btn btn--ghost" onClick={onClose}>
            {result.card.removal ? 'Accept' : 'Dismiss'}
          </button>
        </div>
      </div>
    </div>
  );
}
