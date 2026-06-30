import { useEffect, useState } from 'react';
import { useCharacterStore } from '../application/characterStore';
import type { DeckCard, DeckTemplate } from '../platform/types';

type CardKind = 'neutralCards' | 'encounterCards';

export function DeckEditor() {
  const roomName = useCharacterStore((s) => s.roomName);
  const roomDeck = useCharacterStore((s) => s.roomDeck);
  const saving = useCharacterStore((s) => s.saving);
  const error = useCharacterStore((s) => s.error);
  const saveRoomDeck = useCharacterStore((s) => s.saveRoomDeck);
  const back = useCharacterStore((s) => s.back);

  const [draft, setDraft] = useState<DeckTemplate | null>(null);

  useEffect(() => {
    if (roomDeck) setDraft(roomDeck);
  }, [roomDeck]);

  if (!draft) return <div className="panel-msg">Loading deck…</div>;

  const deckSize = 2 + draft.neutralCards.length + draft.statCount + draft.encounterCards.length;

  function setCard(kind: CardKind, i: number, patch: Partial<DeckCard>) {
    if (!draft) return;
    setDraft({ ...draft, [kind]: draft[kind].map((c, idx) => (idx === i ? { ...c, ...patch } : c)) });
  }
  function addCard(kind: CardKind) {
    if (!draft) return;
    const card: DeckCard =
      kind === 'neutralCards'
        ? { name: 'Neutral', modifier: 0, description: '' }
        : { name: 'Encounter', modifier: -1, description: '' };
    setDraft({ ...draft, [kind]: [...draft[kind], card] });
  }
  function removeCard(kind: CardKind, i: number) {
    if (!draft) return;
    setDraft({ ...draft, [kind]: draft[kind].filter((_, idx) => idx !== i) });
  }

  return (
    <section className="form-view deck-editor">
      <div className="sheet-topbar">
        <button className="btn btn--ghost" onClick={back}>
          ← Roster
        </button>
        <span className="sheet-topbar-spacer" />
      </div>

      <header className="form-header">
        <h1 className="form-title">Room Deck</h1>
        <p className="form-sub">
          {roomName} · {deckSize} cards <span className="deck-locked">(2 criticals locked)</span>
        </p>
      </header>

      {error && <p className="inline-error">{error}</p>}

      <label className="field field--narrow">
        <span>Stat cards</span>
        <input
          type="number"
          min={0}
          max={30}
          value={draft.statCount}
          onChange={(e) =>
            setDraft({ ...draft, statCount: Math.max(0, Number.parseInt(e.target.value, 10) || 0) })
          }
        />
      </label>

      <DeckSection
        title="Neutral cards"
        kind="neutralCards"
        cards={draft.neutralCards}
        onSet={setCard}
        onAdd={addCard}
        onRemove={removeCard}
      />
      <DeckSection
        title="Encounter cards"
        kind="encounterCards"
        cards={draft.encounterCards}
        onSet={setCard}
        onAdd={addCard}
        onRemove={removeCard}
      />

      <div className="form-actions">
        <button className="btn btn--ghost" onClick={back} disabled={saving}>
          Back
        </button>
        <button className="btn btn--gold" onClick={() => void saveRoomDeck(draft)} disabled={saving}>
          {saving ? 'Saving…' : 'Save Deck'}
        </button>
      </div>
    </section>
  );
}

function DeckSection({
  title,
  kind,
  cards,
  onSet,
  onAdd,
  onRemove,
}: {
  title: string;
  kind: CardKind;
  cards: DeckCard[];
  onSet: (kind: CardKind, i: number, patch: Partial<DeckCard>) => void;
  onAdd: (kind: CardKind) => void;
  onRemove: (kind: CardKind, i: number) => void;
}) {
  return (
    <div className="deck-section">
      <div className="deck-section-head">
        <h3>{title}</h3>
        <button className="btn btn--ghost" onClick={() => onAdd(kind)}>
          + Add
        </button>
      </div>
      {cards.length === 0 && <p className="deck-empty">None.</p>}
      {cards.map((c, i) => (
        <div className="deck-card-row" key={i}>
          <input
            className="deck-card-name"
            value={c.name}
            onChange={(e) => onSet(kind, i, { name: e.target.value })}
          />
          <input
            className="deck-card-mod"
            type="number"
            value={c.modifier}
            onChange={(e) => onSet(kind, i, { modifier: Number.parseInt(e.target.value, 10) || 0 })}
          />
          <button className="btn btn--ghost deck-remove" onClick={() => onRemove(kind, i)}>
            ×
          </button>
        </div>
      ))}
    </div>
  );
}
