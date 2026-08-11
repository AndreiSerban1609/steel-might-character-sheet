import { useEffect, useState } from 'react';
import { useCharacterStore } from '../application/characterStore';
import { formatModifier } from '../domain/stats';
import type { TableDraw } from '../platform/types';
import { CardFace } from './CardFace';
import skillsRaw from '../data/skills.json';

const SKILL_NAME = new Map(
  (skillsRaw as { id: string; name: string }[]).map((s) => [s.id, s.name]),
);

/** Draws linger this long before auto-hiding (each draw/redraw restarts the clock). */
const AUTO_HIDE_MS = 12_000;

/**
 * Other people's live draws, adopted from the room's per-character draw keys —
 * concurrent checks stack instead of overwriting each other. The drawer sees
 * their full banner instead, so this renders only draws for characters this
 * client is NOT viewing. Closing hides a draw locally; a new one (fresh `at`)
 * re-shows it.
 */
export function TableDrawToast() {
  const tableDraws = useCharacterStore((s) => s.tableDraws);
  const selectedPlayerId = useCharacterStore((s) => s.selectedPlayerId);

  const draws = Object.values(tableDraws)
    .filter((d) => d.playerId !== selectedPlayerId)
    .sort((a, b) => a.at - b.at);
  if (draws.length === 0) return null;

  return (
    <div className="table-draw-stack">
      {draws.map((d) => (
        <SingleDrawToast key={d.playerId} draw={d} />
      ))}
    </div>
  );
}

function SingleDrawToast({ draw }: { draw: TableDraw }) {
  const [hiddenAt, setHiddenAt] = useState<number | null>(null);

  useEffect(() => {
    const at = draw.at;
    const timer = window.setTimeout(() => setHiddenAt(at), AUTO_HIDE_MS);
    return () => window.clearTimeout(timer);
  }, [draw]);

  if (hiddenAt === draw.at) return null;
  const r = draw.result;

  return (
    <aside className="table-draw-toast">
      <CardFace
        key={`${draw.at}-${r.card.name}-${r.redrawsUsed}`}
        card={r.card}
        pathId={draw.pathId ?? undefined}
        size={56}
        animating
      />
      <div className="table-draw-info">
        <div className="table-draw-who">{draw.playerName}</div>
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
        onClick={() => setHiddenAt(draw.at)}
        aria-label="Hide this draw"
      >
        ×
      </button>
    </aside>
  );
}
