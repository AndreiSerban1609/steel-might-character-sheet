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

export function SkillsPanel() {
  const snapshot = useCharacterStore((s) => s.snapshot);
  const saving = useCharacterStore((s) => s.saving);
  const error = useCharacterStore((s) => s.error);
  const drawing = useCharacterStore((s) => s.drawing);
  const drawResult = useCharacterStore((s) => s.drawResult);
  const saveProficiencies = useCharacterStore((s) => s.saveProficiencies);
  const drawSkill = useCharacterStore((s) => s.drawSkill);
  const clearDraw = useCharacterStore((s) => s.clearDraw);

  const [draft, setDraft] = useState<Set<string> | null>(null);

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
          onRedraw={() => void drawSkill(drawResult.skillId)}
          onClose={clearDraw}
        />
      )}

      <p className="skills-hint">
        {editing
          ? 'Toggle the skills this character is proficient in.'
          : 'Click a skill to draw a card. A Stat card applies your ability modifier; proficiency (●) lets you redraw.'}
      </p>
      {error && editing && <p className="inline-error">{error}</p>}

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
                    onClick={editing ? () => toggle(sk.id) : () => void drawSkill(sk.id)}
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
  // A fresh result object arrives on every draw/redraw — roll the die each time.
  const [rolling, setRolling] = useState(!result.critical);
  const [revealed, setRevealed] = useState(result.critical);

  useEffect(() => {
    if (result.critical) {
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
        <CardFace key={`${result.skillId}-${result.card.name}-${result.d10}`} card={result.card} pathId={pathId} size={88} animating />
      </div>
      <div className="draw-info">
        <div className="draw-skill">
          {SKILL_NAME.get(result.skillId) ?? result.skillId}
          <span className="draw-ability">{result.ability}</span>
        </div>
        {result.critical ? (
          <div className="draw-outcome draw-outcome--crit">The GM decides</div>
        ) : (
          <div className="draw-outcome">
            <DiceRoll
              result={result.d10}
              rolling={rolling}
              size={54}
              onRollComplete={() => {
                setRolling(false);
                setRevealed(true);
              }}
            />
            <span className={'draw-eq' + (revealed ? ' draw-eq--shown' : '')}>
              <span className="draw-piece">{formatModifier(result.effectiveModifier ?? 0)}</span>
              <span className="draw-piece">=</span>
              <span className="draw-total">{result.total}</span>
            </span>
          </div>
        )}
        <div className="draw-actions">
          {result.proficient && (
            <button className="btn btn--ghost" onClick={onRedraw} disabled={drawing}>
              {drawing ? '…' : 'Redraw'}
            </button>
          )}
          <button className="btn btn--ghost" onClick={onClose}>
            Dismiss
          </button>
        </div>
      </div>
    </div>
  );
}
