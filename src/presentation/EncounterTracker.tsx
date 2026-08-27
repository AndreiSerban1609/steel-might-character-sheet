import { useEffect, useState } from 'react';
import { useCharacterStore } from '../application/characterStore';

/**
 * Room turn order. Inside OBR the room-metadata mirror pushes updates (Story 3.2);
 * outside OBR (dev entry form) it falls back to polling the server while mounted.
 * GM gets start/skip/end + initiative overrides; players see the order and whose
 * turn it is.
 */
export function EncounterTracker() {
  const encounter = useCharacterStore((s) => s.encounter);
  const obrMode = useCharacterStore((s) => s.obrMode);
  const role = useCharacterStore((s) => s.role);
  const acting = useCharacterStore((s) => s.acting);
  const roster = useCharacterStore((s) => s.roster);
  const loadEncounter = useCharacterStore((s) => s.loadEncounter);
  const startEncounter = useCharacterStore((s) => s.startEncounter);
  const endEncounter = useCharacterStore((s) => s.endEncounter);
  const skipTurn = useCharacterStore((s) => s.skipTurn);
  const overrideInitiative = useCharacterStore((s) => s.overrideInitiative);
  const monsters = useCharacterStore((s) => s.monsters);
  const endMonsterTurn = useCharacterStore((s) => s.endMonsterTurn);

  const isGm = role === 'gm';
  const [editing, setEditing] = useState<string | null>(null);
  const [editValue, setEditValue] = useState('');
  const [surprised, setSurprised] = useState<Set<string>>(new Set());

  useEffect(() => {
    void loadEncounter();
    if (obrMode) return; // metadata mirror pushes updates — no polling needed
    const timer = window.setInterval(() => void loadEncounter(), 5000);
    return () => window.clearInterval(timer);
  }, [loadEncounter, obrMode]);

  if (!encounter?.active) {
    if (!isGm) return null;

    function toggleSurprised(playerId: string) {
      setSurprised((prev) => {
        const next = new Set(prev);
        if (next.has(playerId)) next.delete(playerId);
        else next.add(playerId);
        return next;
      });
    }

    return (
      <div className="encounter encounter--idle">
        <div className="encounter-idle-row">
          <span className="encounter-label">No encounter running</span>
          <button
            className="btn btn--gold"
            title="Rolls d20 + DEX mod + initiative bonus for everyone in the room; AP set to starting value. First turns give no AP recovery."
            onClick={() => {
              void startEncounter(surprised.size > 0 ? [...surprised] : undefined);
              setSurprised(new Set());
            }}
            disabled={acting}
          >
            Roll Initiative
          </button>
        </div>
        {roster.length > 0 && (
          <div className="encounter-surprise-setup">
            <span className="encounter-surprise-label" title="Ambushed characters skip the surprise round (round 0); the rest get a free round first">
              Surprised:
            </span>
            {roster.map((r) => (
              <label className="encounter-surprise-pick" key={r.playerId}>
                <input
                  type="checkbox"
                  checked={surprised.has(r.playerId)}
                  onChange={() => toggleSurprised(r.playerId)}
                />
                {r.name}
              </label>
            ))}
            {monsters
              .filter((m) => m.status !== 'DEAD')
              .map((m) => (
                <label className="encounter-surprise-pick" key={m.combatantId}>
                  <input
                    type="checkbox"
                    checked={surprised.has(m.combatantId)}
                    onChange={() => toggleSurprised(m.combatantId)}
                  />
                  {m.name}
                </label>
              ))}
          </div>
        )}
      </div>
    );
  }

  const current = encounter.entries.find((e) => e.playerId === encounter.currentPlayerId);

  function commitInitiative(playerId: string) {
    const v = Number.parseInt(editValue, 10);
    setEditing(null);
    if (!Number.isNaN(v)) void overrideInitiative(playerId, v);
  }

  return (
    <div className="encounter">
      <div className="encounter-head">
        <span className="encounter-round">
          {encounter.round === 0 ? 'Surprise round' : `Round ${encounter.round}`}
        </span>
        <span className="encounter-turn">
          {current ? (
            <>
              <strong>{current.name}</strong>
              {"'s turn"}
            </>
          ) : (
            '—'
          )}
        </span>
        {isGm && (
          <span className="encounter-actions">
            {current?.combatantType === 'MONSTER' && (
              <button
                className="btn btn--gold"
                title="End this monster's turn — the order advances and the next turn begins"
                onClick={() => void endMonsterTurn(current.playerId)}
                disabled={acting}
              >
                End {current.name}'s turn
              </button>
            )}
            <button className="btn btn--ghost" title="Skip the current turn (AFK)" onClick={() => void skipTurn()} disabled={acting}>
              Skip
            </button>
            <button className="btn btn--ghost" onClick={() => void endEncounter()} disabled={acting}>
              End Combat
            </button>
          </span>
        )}
      </div>
      <div className="encounter-order">
        {encounter.entries.map((e) => {
          const isCurrent = e.playerId === encounter.currentPlayerId;
          const surprisedNow = encounter.round === 0 && e.surprised;
          const cls =
            'encounter-entry' +
            (isCurrent ? ' encounter-entry--current' : '') +
            (e.status === 'DEAD' ? ' encounter-entry--dead' : '') +
            (e.combatantType === 'MONSTER' ? ' encounter-entry--monster' : '') +
            (surprisedNow ? ' encounter-entry--surprised' : '');
          return (
            <span
              className={cls}
              key={e.playerId}
              title={surprisedNow ? 'Surprised — skipped this round' : (e.status ?? undefined)}
            >
              {isCurrent && <span className="encounter-arrow">▶</span>}
              {e.name}
              {e.combatantType === 'MONSTER' && e.hp != null && (
                <span className="encounter-hp" title="Monster HP (live)">
                  {e.hp}/{e.maxHp}
                </span>
              )}
              {(e.prepared ?? []).map((note, i) => (
                <span className="encounter-prepared" key={i} title={`Prepared reaction: ${note}`}>
                  ⚑ {note}
                </span>
              ))}
              {isGm && editing === e.playerId ? (
                <input
                  className="encounter-init-input"
                  type="number"
                  autoFocus
                  value={editValue}
                  onChange={(ev) => setEditValue(ev.target.value)}
                  onBlur={() => commitInitiative(e.playerId)}
                  onKeyDown={(ev) => ev.key === 'Enter' && commitInitiative(e.playerId)}
                />
              ) : (
                <span
                  className={'encounter-init' + (isGm ? ' encounter-init--editable' : '')}
                  title={isGm ? 'Click to override' : undefined}
                  onClick={
                    isGm
                      ? () => {
                          setEditing(e.playerId);
                          setEditValue(String(e.initiative));
                        }
                      : undefined
                  }
                >
                  {e.initiative}
                </span>
              )}
              {e.status === 'DOWNED' && <span className="encounter-status">↓</span>}
              {e.status === 'DEAD' && <span className="encounter-status">☠</span>}
            </span>
          );
        })}
      </div>
    </div>
  );
}
