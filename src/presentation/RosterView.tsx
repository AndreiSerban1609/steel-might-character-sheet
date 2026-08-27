import { useEffect, useState } from 'react';
import { useCharacterStore } from '../application/characterStore';
import { fetchAudit } from '../platform/http';
import type { AuditView } from '../platform/types';
import { liveVitalsFromSlice } from '../domain/partyMirror';
import { titleCase } from '../domain/stats';
import { EncounterTracker } from './EncounterTracker';
import { MonsterBoard } from './MonsterBoard';
import { MonsterLibrary } from './MonsterLibrary';

export function RosterView() {
  const roster = useCharacterStore((s) => s.roster);
  const partyViewports = useCharacterStore((s) => s.partyViewports);
  const roomName = useCharacterStore((s) => s.roomName);
  const loading = useCharacterStore((s) => s.loading);
  const error = useCharacterStore((s) => s.error);
  const obrMode = useCharacterStore((s) => s.obrMode);
  const loadRoster = useCharacterStore((s) => s.loadRoster);
  const selectPlayer = useCharacterStore((s) => s.selectPlayer);
  const openDeckEditor = useCharacterStore((s) => s.openDeckEditor);
  const resetTableMirror = useCharacterStore((s) => s.resetTableMirror);
  const [mirrorNote, setMirrorNote] = useState<string | null>(null);

  useEffect(() => {
    void loadRoster();
  }, [loadRoster]);

  async function resetMirror() {
    const removed = await resetTableMirror();
    setMirrorNote(`Cleared ${removed} mirrored key${removed === 1 ? '' : 's'} — slices rebuild as players act.`);
    window.setTimeout(() => setMirrorNote(null), 6000);
  }

  return (
    <section className="roster">
      <div className="sheet-bar">
        <button className="btn btn--ghost" onClick={() => void openDeckEditor()}>
          Room Deck
        </button>
        {obrMode && (
          <button
            className="btn btn--ghost"
            title="Flush this app's keys from OBR room metadata (fixes a full/stale room). A live turn order survives; sheets re-mirror on each player's next action."
            onClick={() => void resetMirror()}
          >
            Reset table sync
          </button>
        )}
        {mirrorNote && <span className="mirror-note">{mirrorNote}</span>}
      </div>

      <header className="roster-header">
        {/* Inside OBR the "room name" is the opaque room hash — never show it. */}
        <h1 className="roster-title">{obrMode ? 'Party' : roomName || 'Party'} Roster</h1>
        <p className="roster-sub">
          GM view · {roster.length} character{roster.length === 1 ? '' : 's'}
        </p>
      </header>

      <EncounterTracker />

      <MonsterBoard />

      {loading && roster.length === 0 && <div className="panel-msg">Gathering the party…</div>}
      {error && <div className="panel-msg panel-msg--error">{error}</div>}
      {!loading && !error && roster.length === 0 && (
        <div className="panel-msg">No characters in this room yet.</div>
      )}

      <div className="roster-grid">
        {roster.map((r) => {
          // live vitals from the player's mirrored combat viewport, when broadcast
          const live = liveVitalsFromSlice(partyViewports[r.playerId]);
          const currentHp = live?.currentHp ?? r.currentHp;
          const maxHp = live?.maxHp ?? r.maxHp;
          const ac = live?.ac ?? r.ac;
          const pct = maxHp > 0 ? Math.max(0, Math.min(100, (currentHp / maxHp) * 100)) : 0;
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
                  HP {currentHp}/{maxHp}
                  {live && (
                    <span className="roster-live-dot" title="Live from the table (OBR mirror)" />
                  )}
                </span>
                <span>AC {ac}</span>
              </div>
            </button>
          );
        })}
      </div>

      <MonsterLibrary />

      <ActivityLog room={roomName} />
    </section>
  );
}

/**
 * The room's audit trail (trusted-table review): every state-changing action —
 * including a player dismissing one of their own effects — is one line here.
 */
function ActivityLog({ room }: { room: string }) {
  const [open, setOpen] = useState(false);
  const [entries, setEntries] = useState<AuditView[] | null>(null);
  const [failed, setFailed] = useState(false);

  async function refresh() {
    try {
      setFailed(false);
      setEntries(await fetchAudit(room));
    } catch {
      setFailed(true);
    }
  }

  useEffect(() => {
    if (open) void refresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, room]);

  if (!room.trim()) return null;

  return (
    <div className="audit">
      <div className="audit-head">
        <button className="btn btn--ghost" onClick={() => setOpen(!open)}>
          {open ? '▾ Activity log' : '▸ Activity log'}
        </button>
        {open && (
          <button className="btn btn--ghost" onClick={() => void refresh()}>
            Refresh
          </button>
        )}
      </div>
      {open && failed && <p className="inline-error">Could not load the activity log.</p>}
      {open && entries && entries.length === 0 && <p className="deck-empty">Nothing yet.</p>}
      {open && entries && entries.length > 0 && (
        <div className="audit-list">
          {entries.map((e, i) => (
            <div className="audit-row" key={i}>
              <span className="audit-time">
                {new Date(e.time).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
              </span>
              <span className="audit-name">{e.characterName}</span>
              <span className="audit-summary">{e.summary}</span>
              <span className="audit-action">{e.action}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
