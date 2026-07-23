import { useState } from 'react';
import { useCharacterStore } from '../application/characterStore';
import { formatModifier } from '../domain/stats';
import { CardFace } from './CardFace';
import skillsRaw from '../data/skills.json';

const SKILL_NAME = new Map(
  (skillsRaw as { id: string; name: string }[]).map((s) => [s.id, s.name]),
);

/**
 * Someone else's live draw, adopted from the room's mirrored draw key.
 * The drawer sees their full banner instead, so this renders only for others.
 * Closing hides that draw locally; a new one (fresh `at`) re-shows the toast.
 */
export function TableDrawToast() {
  const tableDraw = useCharacterStore((s) => s.tableDraw);
  const selectedPlayerId = useCharacterStore((s) => s.selectedPlayerId);
  const [hiddenAt, setHiddenAt] = useState<number | null>(null);

  if (!tableDraw || tableDraw.playerId === selectedPlayerId) return null;
  if (hiddenAt === tableDraw.at) return null;
  const r = tableDraw.result;

  return (
    <aside className="table-draw-toast">
      <CardFace
        key={`${tableDraw.at}-${r.card.name}-${r.redrawsUsed}`}
        card={r.card}
        pathId={tableDraw.pathId ?? undefined}
        size={56}
        animating
      />
      <div className="table-draw-info">
        <div className="table-draw-who">{tableDraw.playerName}</div>
        <div className="table-draw-what">
          {SKILL_NAME.get(r.skillId) ?? r.skillId}
          {r.advantage && ` · ${r.advantage}`}
          {r.redrawsUsed > 0 && ` · redraw ${r.redrawsUsed}`}
        </div>
        <div className="table-draw-total">
          {r.critical ? (
            'The GM decides'
          ) : (
            <>
              d10 {r.d10} {formatModifier(r.effectiveModifier ?? 0)}
              {r.bonusTotal !== 0 && <> {formatModifier(r.bonusTotal)}</>} ={' '}
              <strong>{r.total}</strong>
            </>
          )}
        </div>
      </div>
      <button
        className="table-draw-close"
        onClick={() => setHiddenAt(tableDraw.at)}
        aria-label="Hide this draw"
      >
        ×
      </button>
    </aside>
  );
}
