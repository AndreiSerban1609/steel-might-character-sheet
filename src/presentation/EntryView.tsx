import { useCharacterStore } from '../application/characterStore';
import { ServerConnection } from './ServerConnection';

export function EntryView() {
  const roomName = useCharacterStore((s) => s.roomName);
  const email = useCharacterStore((s) => s.email);
  const loading = useCharacterStore((s) => s.loading);
  const error = useCharacterStore((s) => s.error);
  const obrMode = useCharacterStore((s) => s.obrMode);
  const role = useCharacterStore((s) => s.role);
  const setRoom = useCharacterStore((s) => s.setRoom);
  const setEmail = useCharacterStore((s) => s.setEmail);
  const enterAsPlayer = useCharacterStore((s) => s.enterAsPlayer);
  const enterAsGm = useCharacterStore((s) => s.enterAsGm);

  const connBox = <ServerConnection />;

  // Inside Owlbear identity comes from the SDK — no form, just the handshake
  // (and a retry + server field in case the backend is unreachable).
  if (obrMode) {
    return (
      <section className="form-view entry">
        <header className="form-header">
          <h1 className="form-title">Steel &amp; Might</h1>
          <p className="form-sub">{error ? 'Connection problem' : 'Connecting to your table…'}</p>
        </header>

        {error && <p className="inline-error">{error}</p>}
        {error && (
          <div className="form-actions">
            <button
              className="btn btn--gold"
              onClick={() => void (role === 'gm' ? enterAsGm() : enterAsPlayer())}
              disabled={loading}
            >
              {loading ? 'Retrying…' : 'Retry'}
            </button>
          </div>
        )}

        {connBox}
      </section>
    );
  }

  return (
    <section className="form-view entry">
      <header className="form-header">
        <h1 className="form-title">Steel &amp; Might</h1>
        <p className="form-sub">Enter your table</p>
      </header>

      {error && <p className="inline-error">{error}</p>}

      <label className="field">
        <span>Room</span>
        <input value={roomName} onChange={(e) => setRoom(e.target.value)} placeholder="e.g. dragons-lair" />
      </label>
      <label className="field">
        <span>Email</span>
        <input
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          placeholder="you@example.com"
        />
      </label>

      <div className="form-actions">
        <button className="btn btn--gold" onClick={() => void enterAsPlayer()} disabled={loading}>
          {loading ? 'Entering…' : 'Enter as Player'}
        </button>
        <button className="btn btn--ghost" onClick={() => void enterAsGm()} disabled={loading}>
          View as GM
        </button>
      </div>

      <p className="entry-hint">
        Players enter their room and email to open or create their own character. The GM enters a room to see
        everyone at the table.
      </p>

      {connBox}
    </section>
  );
}
