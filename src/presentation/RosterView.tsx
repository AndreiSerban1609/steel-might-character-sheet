import { useEffect } from 'react';
import { useCharacterStore } from '../application/characterStore';
import { titleCase } from '../domain/stats';
import { EncounterTracker } from './EncounterTracker';

export function RosterView() {
  const roster = useCharacterStore((s) => s.roster);
  const roomName = useCharacterStore((s) => s.roomName);
  const loading = useCharacterStore((s) => s.loading);
  const error = useCharacterStore((s) => s.error);
  const loadRoster = useCharacterStore((s) => s.loadRoster);
  const selectPlayer = useCharacterStore((s) => s.selectPlayer);
  const openDeckEditor = useCharacterStore((s) => s.openDeckEditor);
  const back = useCharacterStore((s) => s.back);

  useEffect(() => {
    void loadRoster();
  }, [loadRoster]);

  return (
    <section className="roster">
      <div className="sheet-bar">
        <button className="btn btn--ghost" onClick={back}>
          ← Exit
        </button>
        <button className="btn btn--ghost" onClick={() => void openDeckEditor()}>
          Room Deck
        </button>
      </div>

      <header className="roster-header">
        <h1 className="roster-title">{roomName || 'Party'} Roster</h1>
        <p className="roster-sub">
          GM view · {roster.length} character{roster.length === 1 ? '' : 's'}
        </p>
      </header>

      <EncounterTracker />

      {loading && roster.length === 0 && <div className="panel-msg">Gathering the party…</div>}
      {error && <div className="panel-msg panel-msg--error">{error}</div>}
      {!loading && !error && roster.length === 0 && (
        <div className="panel-msg">No characters in this room yet.</div>
      )}

      <div className="roster-grid">
        {roster.map((r) => {
          const pct = r.maxHp > 0 ? Math.max(0, Math.min(100, (r.currentHp / r.maxHp) * 100)) : 0;
          return (
            <button className="roster-card" key={r.playerId} onClick={() => void selectPlayer(r.playerId)}>
              <div className="roster-card-name">{r.name}</div>
              <div className="roster-card-email">{r.email}</div>
              <div className="roster-card-class">
                Lv {r.level} · {titleCase(r.pathId)} / {titleCase(r.classId)}
              </div>
              <div className="roster-hpbar">
                <div className="roster-hpbar-fill" style={{ width: `${pct}%` }} />
              </div>
              <div className="roster-card-stats">
                <span>
                  HP {r.currentHp}/{r.maxHp}
                </span>
                <span>AC {r.ac}</span>
              </div>
            </button>
          );
        })}
      </div>
    </section>
  );
}
