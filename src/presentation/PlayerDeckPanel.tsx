import { useEffect, useState } from 'react';
import { useCharacterStore } from '../application/characterStore';
import type { DeckCard, PlayerDeckConfig } from '../platform/types';

function clamp(v: number, lo: number, hi: number): number {
  return Math.max(lo, Math.min(hi, v));
}

export function PlayerDeckPanel() {
  const playerDeck = useCharacterStore((s) => s.playerDeck);
  const saving = useCharacterStore((s) => s.saving);
  const error = useCharacterStore((s) => s.error);
  const loadPlayerDeck = useCharacterStore((s) => s.loadPlayerDeck);
  const savePlayerDeck = useCharacterStore((s) => s.savePlayerDeck);

  const [draft, setDraft] = useState<PlayerDeckConfig | null>(null);

  useEffect(() => {
    void loadPlayerDeck();
  }, [loadPlayerDeck]);
  useEffect(() => {
    if (playerDeck) setDraft(playerDeck.config);
  }, [playerDeck]);

  if (!playerDeck || !draft) return <div className="panel-msg">Loading deck…</div>;

  const room = playerDeck.room;
  const baseStat = room.statCount;
  const effStat = Math.max(0, baseStat + draft.statAdjust);
  const deckSize = 2 + room.neutralCards.length + effStat + room.encounterCards.length + draft.extraCards.length;

  function setExtra(i: number, patch: Partial<DeckCard>) {
    if (!draft) return;
    setDraft({ ...draft, extraCards: draft.extraCards.map((c, idx) => (idx === i ? { ...c, ...patch } : c)) });
  }
  function addExtra() {
    if (!draft) return;
    setDraft({ ...draft, extraCards: [...draft.extraCards, { name: 'My Card', modifier: 1, description: '' }] });
  }
  function removeExtra(i: number) {
    if (!draft) return;
    setDraft({ ...draft, extraCards: draft.extraCards.filter((_, idx) => idx !== i) });
  }

  return (
    <>
      <div className="sheet-actionbar">
        <button className="btn btn--gold" onClick={() => void savePlayerDeck(draft)} disabled={saving}>
          {saving ? 'Saving…' : 'Save Deck'}
        </button>
      </div>

      <p className="skills-hint">
        Your deck is the room's base deck <strong>plus</strong> your changes. The GM sets the base.
      </p>
      {error && <p className="inline-error">{error}</p>}

      <div className="deck-summary">
        <span>Room base</span>
        <span className="deck-summary-val">
          {room.neutralCards.length} neutral · {baseStat} stat · {room.encounterCards.length} encounter
        </span>
      </div>

      <div className="deck-stat-adjust">
        <span>Your Stat cards</span>
        <div className="deck-stepper">
          <button
            className="btn btn--ghost"
            onClick={() => setDraft({ ...draft, statAdjust: clamp(draft.statAdjust - 1, -20, 20) })}
          >
            −
          </button>
          <span className="deck-eff">{effStat}</span>
          <button
            className="btn btn--ghost"
            onClick={() => setDraft({ ...draft, statAdjust: clamp(draft.statAdjust + 1, -20, 20) })}
          >
            +
          </button>
        </div>
        <span className="deck-adjust-note">
          room {baseStat}
          {draft.statAdjust >= 0 ? ` + ${draft.statAdjust}` : ` − ${-draft.statAdjust}`}
        </span>
      </div>

      <div className="deck-section">
        <div className="deck-section-head">
          <h3>Your cards</h3>
          <button className="btn btn--ghost" onClick={addExtra}>
            + Add
          </button>
        </div>
        {draft.extraCards.length === 0 && <p className="deck-empty">None.</p>}
        {draft.extraCards.map((c, i) => (
          <div className="deck-card-row" key={i}>
            <input
              className="deck-card-name"
              value={c.name}
              onChange={(e) => setExtra(i, { name: e.target.value })}
            />
            <input
              className="deck-card-mod"
              type="number"
              value={c.modifier}
              onChange={(e) => setExtra(i, { modifier: Number.parseInt(e.target.value, 10) || 0 })}
            />
            <button className="btn btn--ghost deck-remove" onClick={() => removeExtra(i)}>
              ×
            </button>
          </div>
        ))}
      </div>

      <p className="deck-total">
        Your deck: <strong>{deckSize} cards</strong>{' '}
        <span className="deck-locked">(2 criticals locked)</span>
      </p>
    </>
  );
}
