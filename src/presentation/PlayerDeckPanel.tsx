import { useEffect, useState } from 'react';
import { useCharacterStore } from '../application/characterStore';
import type { DeckCard, PlayerDeckConfig } from '../platform/types';
import { CardFace } from './CardFace';
import skillsRaw from '../data/skills.json';

const SKILLS = skillsRaw as unknown as { id: string; name: string }[];

function clamp(v: number, lo: number, hi: number): number {
  return Math.max(lo, Math.min(hi, v));
}

export function PlayerDeckPanel() {
  const playerDeck = useCharacterStore((s) => s.playerDeck);
  const pathId = useCharacterStore((s) => s.snapshot?.pathId);
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
  const liveExtras = draft.extraCards.filter((c) => !c.consumed).length;
  // Opt-outs match by card NAME (case-insensitive) — stable across GM deck edits.
  const cardKey = (c: DeckCard) => (c.name || 'Encounter').toLowerCase();
  const disabled = new Set(draft.disabledEncounters);
  const liveEncounters = room.encounterCards.filter((c) => !disabled.has(cardKey(c))).length;
  const deckSize = 2 + room.neutralCards.length + effStat + liveEncounters + liveExtras;

  function toggleEncounter(card: DeckCard) {
    if (!draft) return;
    const key = cardKey(card);
    const next = new Set(draft.disabledEncounters);
    if (next.has(key)) next.delete(key);
    else next.add(key);
    setDraft({ ...draft, disabledEncounters: [...next].sort() });
  }

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
          <h3>Room encounter cards</h3>
        </div>
        {room.encounterCards.length === 0 && <p className="deck-empty">The GM has none set.</p>}
        {room.encounterCards.map((c, i) => (
          <label
            className={'deck-encounter-row' + (disabled.has(cardKey(c)) ? ' deck-encounter-row--off' : '')}
            key={i}
          >
            <input
              type="checkbox"
              checked={!disabled.has(cardKey(c))}
              onChange={() => toggleEncounter(c)}
            />
            <span className="deck-encounter-name">{c.name || 'Encounter'}</span>
            <span className="deck-encounter-mod">
              {c.modifier > 0 ? `+${c.modifier}` : c.modifier}
            </span>
            {c.description && <span className="deck-encounter-desc">{c.description}</span>}
          </label>
        ))}
        <p className="skills-hint">
          Untick an encounter card to leave it out of your deck (e.g. a reward lets you remove
          one). The GM's base deck itself is unchanged.
        </p>
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
          <div className={'deck-card-block' + (c.consumed ? ' deck-card-block--consumed' : '')} key={i}>
            <div className="deck-card-row">
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
              {c.consumed && <span className="deck-consumed-badge">consumed</span>}
              <button className="btn btn--ghost deck-remove" onClick={() => removeExtra(i)}>
                ×
              </button>
            </div>
            <div className="deck-card-row deck-card-row--opts">
              <label className="deck-opt">
                <span>Check</span>
                <select
                  value={c.checkType ?? ''}
                  onChange={(e) => setExtra(i, { checkType: e.target.value || null })}
                >
                  <option value="">Any</option>
                  {SKILLS.map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.name}
                    </option>
                  ))}
                </select>
              </label>
              <label className="deck-opt">
                <span>Redraw bonus</span>
                <input
                  className="deck-card-mod"
                  type="number"
                  placeholder="—"
                  value={c.redrawModifier ?? ''}
                  onChange={(e) =>
                    setExtra(i, {
                      redrawModifier: e.target.value === '' ? null : Number.parseInt(e.target.value, 10) || 0,
                    })
                  }
                />
              </label>
              <label className="deck-opt">
                <span>On use</span>
                <select
                  value={c.removal ?? ''}
                  onChange={(e) =>
                    setExtra(i, { removal: (e.target.value || null) as DeckCard['removal'] })
                  }
                >
                  <option value="">Keep</option>
                  <option value="consume">Consume (until rest)</option>
                  <option value="burn">Burn (forever)</option>
                </select>
              </label>
            </div>
          </div>
        ))}
      </div>

      <p className="skills-hint">
        A <em>check</em>-restricted card auto-passes on other checks; a <em>redraw bonus</em> card is
        passed and its bonus adds to the final total; consume/burn removes the card once its result
        is accepted.
      </p>

      <p className="deck-total">
        Your deck: <strong>{deckSize} cards</strong>{' '}
        <span className="deck-locked">(2 criticals locked)</span>
      </p>

      <details className="deck-section">
        <summary className="deck-gallery-summary">
          View whole deck ({playerDeck.cards.length} cards as saved)
        </summary>
        <div className="deck-gallery">
          {playerDeck.cards.map((c, i) => (
            <div className="deck-gallery-cell" key={`${c.name}-${i}`} title={c.description || c.name}>
              <CardFace card={c} pathId={pathId ?? undefined} size={56} />
              <span className="deck-gallery-name">{c.name}</span>
            </div>
          ))}
        </div>
        <p className="skills-hint">
          What you can actually draw from right now — consumed and burned cards are not in it.
          Unsaved edits above are not reflected until you save.
        </p>
      </details>
    </>
  );
}
