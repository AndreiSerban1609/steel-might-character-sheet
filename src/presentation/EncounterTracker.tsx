import { useEffect, useState } from 'react';
import { useCharacterStore } from '../application/characterStore';

/**
 * Room turn order. Polls the server while mounted (no push channel yet — the OBR
 * metadata mirror will replace this). GM gets start/skip/end + initiative overrides;
 * players see the order and whose turn it is.
 */
export function EncounterTracker() {
  const encounter = useCharacterStore((s) => s.encounter);
  const role = useCharacterStore((s) => s.role);
  const acting = useCharacterStore((s) => s.acting);
  const loadEncounter = useCharacterStore((s) => s.loadEncounter);
  const startEncounter = useCharacterStore((s) => s.startEncounter);
  const endEncounter = useCharacterStore((s) => s.endEncounter);
  const skipTurn = useCharacterStore((s) => s.skipTurn);
  const overrideInitiative = useCharacterStore((s) => s.overrideInitiative);

  const isGm = role === 'gm';
  const [editing, setEditing] = useState<string | null>(null);
  const [editValue, setEditValue] = useState('');

  useEffect(() => {
    void loadEncounter();
    const timer = window.setInterval(() => void loadEncounter(), 5000);
    return () => window.clearInterval(timer);
  }, [loadEncounter]);

  if (!encounter?.active) {
    if (!isGm) return null;
    return (
      <div className="encounter encounter--idle">
        <span className="encounter-label">No encounter running</span>
        <button
          className="btn btn--gold"
          title="Rolls d20 + DEX mod + initiative bonus for everyone in the room; AP set to starting value"
          onClick={() => void startEncounter()}
          disabled={acting}
        >
          Roll Initiative
        </button>
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
        <span className="encounter-round">Round {encounter.round}</span>
        <span className="encounter-turn">
          {current ? (
            <>
              <strong>{current.name}</strong>
              {encounter.turnStarted ? ' is acting' : ' is up'}
            </>
          ) : (
            '—'
          )}
        </span>
        {isGm && (
          <span className="encounter-actions">
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
          const cls =
            'encounter-entry' +
            (isCurrent ? ' encounter-entry--current' : '') +
            (e.status === 'DEAD' ? ' encounter-entry--dead' : '');
          return (
            <span className={cls} key={e.playerId} title={e.status ?? undefined}>
              {isCurrent && <span className="encounter-arrow">▶</span>}
              {e.name}
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
