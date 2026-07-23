import { useEffect, useMemo, useState } from 'react';
import { useCharacterStore } from '../application/characterStore';
import type { InventoryItemInput, InventoryItemView } from '../platform/types';
import {
  ITEM_CATALOG,
  KIND_LABEL,
  canUpgrade,
  displayPrice,
  displaySellback,
  isEquippable,
  isUsable,
  itemById,
  itemName,
  usesTierPricing,
  type CatalogItem,
  type ItemKind,
} from '../domain/itemCatalog';
import { spellName, spellsForScroll } from '../domain/spellCatalog';
import { ResolutionLog } from './ResolutionLog';

/** Scrolls carry a specific spell — show them as "Scroll of X". */
function displayName(itemId: string, spellId?: string | null): string {
  return spellId ? `Scroll of ${spellName(spellId)}` : itemName(itemId);
}

function spaceOf(itemId: string): number {
  return itemById(itemId)?.space ?? 1;
}

function round1(n: number): number {
  return Math.round(n * 10) / 10;
}

export function InventoryPanel() {
  const inventory = useCharacterStore((s) => s.inventory);
  const saving = useCharacterStore((s) => s.saving);
  const acting = useCharacterStore((s) => s.acting);
  const error = useCharacterStore((s) => s.error);
  const lastResolution = useCharacterStore((s) => s.lastResolution);
  const loadInventory = useCharacterStore((s) => s.loadInventory);
  const saveInventory = useCharacterStore((s) => s.saveInventory);
  const doPurchase = useCharacterStore((s) => s.doPurchase);
  const doSell = useCharacterStore((s) => s.doSell);
  const doUpgrade = useCharacterStore((s) => s.doUpgrade);
  const doEquip = useCharacterStore((s) => s.doEquip);
  const doUnequip = useCharacterStore((s) => s.doUnequip);
  const doUseConsumable = useCharacterStore((s) => s.doUseConsumable);
  const clearResolution = useCharacterStore((s) => s.clearResolution);

  const [draft, setDraft] = useState<{ items: InventoryItemInput[]; gold: number } | null>(null);
  const [pick, setPick] = useState('');
  const [readingScroll, setReadingScroll] = useState<string | null>(null);
  const doCastScroll = useCharacterStore((s) => s.doCastScroll);
  const lastResolutionTarget = useCharacterStore((s) => s.lastResolutionTarget);

  useEffect(() => {
    void loadInventory();
  }, [loadInventory]);

  const editing = draft !== null;

  const projected = useMemo(() => {
    if (!draft) return 0;
    return round1(draft.items.reduce((sum, it) => sum + spaceOf(it.itemId) * it.quantity, 0));
  }, [draft]);

  if (!inventory) return <div className="panel-msg">Loading inventory…</div>;

  const capacity = inventory.carryCapacity;
  const used = editing ? projected : inventory.carriedSpace;
  const over = used > capacity;
  const pct = capacity > 0 ? Math.min(100, (used / capacity) * 100) : 0;

  function startEdit() {
    setDraft({
      items: inventory!.items.map((i) => ({
        itemId: i.itemId,
        quantity: i.quantity,
        upgradeTier: i.upgradeTier,
        equipped: i.equipped,
        spellId: i.spellId ?? undefined,
      })),
      gold: inventory!.gold,
    });
  }

  function patchItem(i: number, patch: Partial<InventoryItemInput>) {
    if (!draft) return;
    setDraft({ ...draft, items: draft.items.map((it, idx) => (idx === i ? { ...it, ...patch } : it)) });
  }
  function removeItem(i: number) {
    if (!draft) return;
    setDraft({ ...draft, items: draft.items.filter((_, idx) => idx !== i) });
  }
  function addPicked(itemId: string) {
    if (!draft || !itemId) return;
    const existing = draft.items.findIndex((it) => it.itemId === itemId);
    if (existing >= 0) {
      patchItem(existing, { quantity: draft.items[existing].quantity + 1 });
    } else {
      setDraft({
        ...draft,
        items: [...draft.items, { itemId, quantity: 1, upgradeTier: 0, equipped: false }],
      });
    }
    setPick('');
  }

  async function save() {
    if (!draft) return;
    await saveInventory(draft);
    if (!useCharacterStore.getState().error) setDraft(null);
  }

  function itemActions(it: InventoryItemView) {
    const cat = itemById(it.itemId);
    if (!cat) return null;
    const tier = it.upgradeTier > 0 ? it.upgradeTier : undefined;
    const spellId = it.spellId ?? undefined;
    const sellback = displaySellback(cat, Math.max(1, it.upgradeTier), it.silvered);
    return (
      <span className="inv-actions">
        {isEquippable(cat.kind) &&
          (it.equipped ? (
            <button
              className="btn btn--ghost inv-act"
              onClick={() => void doUnequip({ itemId: it.itemId, tier })}
              disabled={acting}
            >
              Unequip
            </button>
          ) : (
            <button
              className="btn btn--ghost inv-act"
              onClick={() => void doEquip({ itemId: it.itemId, tier })}
              disabled={acting}
            >
              Equip
            </button>
          ))}
        {isUsable(cat.kind) && (
          <button
            className="btn btn--ghost inv-act"
            title={cat.kind === 'potion' ? 'Drink — heals by the potion’s own level' : 'Use'}
            onClick={() => void doUseConsumable({ itemId: it.itemId, tier })}
            disabled={acting}
          >
            Use
          </button>
        )}
        {cat.kind === 'scroll' && (
          <button
            className="btn btn--ghost inv-act"
            title="Anyone can cast the scroll's spell — no caster requirement, no mana"
            onClick={() =>
              setReadingScroll((cur) =>
                cur === `${it.itemId}:${spellId ?? ''}` ? null : `${it.itemId}:${spellId ?? ''}`,
              )
            }
            disabled={acting || !spellId}
          >
            {spellId ? 'Read…' : 'blank scroll'}
          </button>
        )}
        {canUpgrade(cat.kind) && it.upgradeTier < 20 && (
          <>
            <button
              className="btn btn--ghost inv-act"
              title="Upgrade kit: cheaper, but a failed d20 roll (DC 5 + level) wastes the cost"
              onClick={() => void doUpgrade({ itemId: it.itemId, tier, mode: 'kit' })}
              disabled={acting}
            >
              Kit ⚒
            </button>
            <button
              className="btn btn--ghost inv-act"
              title="Blacksmith: price difference + 5%, guaranteed"
              onClick={() => void doUpgrade({ itemId: it.itemId, tier, mode: 'blacksmith' })}
              disabled={acting}
            >
              Smith ⚒
            </button>
          </>
        )}
        <button
          className="btn btn--ghost inv-act"
          title={sellback != null ? `Sell for ${sellback} g (half price)` : 'Sell for half price'}
          onClick={() => void doSell({ itemId: it.itemId, tier, spellId })}
          disabled={acting || it.equipped}
        >
          Sell
        </button>
      </span>
    );
  }

  return (
    <>
      <div className="sheet-actionbar">
        {editing ? (
          <div className="edit-actions">
            <button className="btn btn--ghost" onClick={() => setDraft(null)} disabled={saving}>
              Cancel
            </button>
            <button className="btn btn--gold" onClick={save} disabled={saving || over}>
              {saving ? 'Saving…' : 'Save'}
            </button>
          </div>
        ) : (
          <button className="btn btn--ghost" title="DM edit: full inventory/gold override" onClick={startEdit}>
            Edit
          </button>
        )}
      </div>

      {error && <p className="inline-error">{error}</p>}

      <div className="carry">
        <div className="carry-head">
          <span>Carrying capacity</span>
          <span className={over ? 'carry-val carry-val--over' : 'carry-val'}>
            {used} / {capacity} slots
          </span>
        </div>
        <div className="carry-bar">
          <div className={over ? 'carry-fill carry-fill--over' : 'carry-fill'} style={{ width: `${pct}%` }} />
        </div>
        {over && <p className="inline-error">Over capacity — remove items before saving.</p>}
      </div>

      <div className="inv-gold">
        <span className="inv-gold-label">Gold</span>
        {editing ? (
          <input
            className="inv-gold-input"
            type="number"
            min={0}
            title="One generic currency — all shop prices are in it"
            value={draft!.gold}
            onChange={(e) =>
              setDraft({ ...draft!, gold: Math.max(0, Number.parseInt(e.target.value, 10) || 0) })
            }
          />
        ) : (
          <span className="inv-gold-val">{inventory.gold} g</span>
        )}
      </div>

      {editing && (
        <div className="inv-add">
          <select className="inv-picker" value={pick} onChange={(e) => addPicked(e.target.value)}>
            <option value="">+ Add an item…</option>
            {ITEM_CATALOG.map((it) => (
              <option key={it.id} value={it.id}>
                {it.name} · {KIND_LABEL[it.kind]} · {it.space} sl
              </option>
            ))}
          </select>
        </div>
      )}

      <div className="inv-list">
        {(editing ? draft!.items : inventory.items).length === 0 && (
          <p className="panel-msg">No items yet.</p>
        )}

        {editing
          ? draft!.items.map((it, i) => {
              const cat = itemById(it.itemId);
              return (
                <div className="inv-row inv-row--edit" key={`${it.itemId}-${i}`}>
                  <div className="inv-name">
                    <span>{itemName(it.itemId)}</span>
                    {cat && <span className="inv-kind">{KIND_LABEL[cat.kind]}</span>}
                  </div>
                  <label className="inv-equip">
                    <input
                      type="checkbox"
                      checked={it.equipped}
                      onChange={(e) => patchItem(i, { equipped: e.target.checked })}
                    />
                    equipped
                  </label>
                  <input
                    className="inv-qty"
                    type="number"
                    min={1}
                    value={it.quantity}
                    onChange={(e) => patchItem(i, { quantity: Math.max(1, Number.parseInt(e.target.value, 10) || 1) })}
                  />
                  <span className="inv-space">{round1(spaceOf(it.itemId) * it.quantity)} sl</span>
                  <button className="btn btn--ghost inv-remove" onClick={() => removeItem(i)}>
                    ×
                  </button>
                </div>
              );
            })
          : inventory.items.map((it, i) => {
              const cat = itemById(it.itemId);
              return (
                <div key={`${it.itemId}-${it.upgradeTier}-${it.spellId ?? ''}-${i}`}>
                  <div className="inv-row">
                    <div className="inv-name">
                      <span title={it.spellId ? itemName(it.itemId) : undefined}>
                        {displayName(it.itemId, it.spellId)}
                      </span>
                      {it.upgradeTier > 0 && <span className="inv-kind">L{it.upgradeTier}</span>}
                      {cat && <span className="inv-kind">{KIND_LABEL[cat.kind]}</span>}
                      {it.silvered && <span className="inv-kind">silvered</span>}
                      {it.chargesRemaining != null && (
                        <span className="inv-kind" title="Charges restore on rest">
                          ⚡ {it.chargesRemaining}
                        </span>
                      )}
                      {it.equipped && <span className="inv-equipped">equipped</span>}
                    </div>
                    <span className="inv-qty-view">×{it.quantity}</span>
                    <span className="inv-space">{round1(it.space * it.quantity)} sl</span>
                    {itemActions(it)}
                  </div>
                  {cat?.kind === 'scroll' &&
                    it.spellId &&
                    readingScroll === `${it.itemId}:${it.spellId}` && (
                      <ScrollReader
                        cat={cat}
                        tier={it.upgradeTier > 0 ? it.upgradeTier : undefined}
                        spellId={it.spellId}
                        acting={acting}
                        onCast={(body) => {
                          setReadingScroll(null);
                          void doCastScroll(body);
                        }}
                      />
                    )}
                </div>
              );
            })}
      </div>

      {!editing && <Shop gold={inventory.gold} acting={acting} onBuy={doPurchase} />}

      {lastResolution && (
        <ResolutionLog
          resolution={lastResolution}
          targetName={lastResolutionTarget}
          onClose={clearResolution}
        />
      )}
    </>
  );
}

/** Cast the spell written on the scroll (bound at purchase). */
function ScrollReader({
  cat,
  tier,
  spellId,
  acting,
  onCast,
}: {
  cat: CatalogItem;
  tier?: number;
  spellId: string;
  acting: boolean;
  onCast: (body: {
    itemId: string;
    tier?: number;
    spellId: string;
    applyEffectsToSelf?: boolean;
    targetPlayerId?: string;
  }) => void;
}) {
  // '' = no effects target (DM applies) | 'self' | a party member's playerId
  const [castTarget, setCastTarget] = useState('');
  const roster = useCharacterStore((s) => s.roster);
  const selectedPlayerId = useCharacterStore((s) => s.selectedPlayerId);
  const spell = useMemo(
    () =>
      spellsForScroll(cat.spellLevel ?? 1, (cat.casterType as 'minor' | 'major') ?? 'major').find(
        (s) => s.id === spellId,
      ),
    [cat, spellId],
  );
  return (
    <div className="scroll-reader">
      <span className="spell-meta">
        Casts <strong>{spellName(spellId)}</strong> (level-{cat.spellLevel} {cat.casterType}-caster
        spell) — no mana, anyone can read it
        {cat.minCharLevel != null && cat.minCharLevel > 1 ? ` · requires level ${cat.minCharLevel}` : ''}
      </span>
      <div className="combat-form">
        {spell?.effects && spell.effects.length > 0 && (
          <select
            value={castTarget}
            onChange={(e) => setCastTarget(e.target.value)}
            title="Who receives the scroll's effects — the scroll is consumed either way"
          >
            <option value="">effects: DM applies</option>
            <option value="self">effects: self</option>
            {roster
              .filter((r) => r.playerId !== selectedPlayerId)
              .map((r) => (
                <option key={r.playerId} value={r.playerId}>
                  effects: {r.name}
                </option>
              ))}
          </select>
        )}
        <button
          className="btn btn--gold"
          onClick={() =>
            onCast({
              itemId: cat.id,
              tier,
              spellId,
              applyEffectsToSelf: castTarget === 'self' || undefined,
              targetPlayerId: castTarget && castTarget !== 'self' ? castTarget : undefined,
            })
          }
          disabled={acting}
        >
          Cast {spellName(spellId)}
        </button>
      </div>
    </div>
  );
}

function Shop({
  gold,
  acting,
  onBuy,
}: {
  gold: number;
  acting: boolean;
  onBuy: (body: {
    itemId: string;
    quantity?: number;
    tier?: number;
    silvered?: boolean;
    spellId?: string;
  }) => Promise<void>;
}) {
  const [kind, setKind] = useState<ItemKind>('weapon');
  const [itemId, setItemId] = useState('');
  const [tier, setTier] = useState(1);
  const [quantity, setQuantity] = useState(1);
  const [silvered, setSilvered] = useState(false);
  const [scrollSpell, setScrollSpell] = useState('');

  const options = useMemo(() => ITEM_CATALOG.filter((it) => it.kind === kind), [kind]);
  const item = itemId ? itemById(itemId) : undefined;
  const tiered = item ? usesTierPricing(item.kind) : false;
  const unit = item ? displayPrice(item, tier, silvered && item.kind === 'weapon') : null;
  const total = unit != null ? unit * quantity : null;
  // scrolls are bought FOR a specific spell ("Scroll of Magic Bolt")
  const scrollSpells = useMemo(
    () =>
      item?.kind === 'scroll'
        ? spellsForScroll(item.spellLevel ?? 1, (item.casterType as 'minor' | 'major') ?? 'major')
        : [],
    [item],
  );
  const needsSpell = item?.kind === 'scroll';

  function pickKind(k: ItemKind) {
    setKind(k);
    setItemId('');
    setTier(1);
    setSilvered(false);
    setScrollSpell('');
  }

  return (
    <div className="combat-effects shop">
      <h3 className="combat-section-title">Shop</h3>
      <div className="combat-form shop-form">
        <select value={kind} onChange={(e) => pickKind(e.target.value as ItemKind)}>
          {(Object.keys(KIND_LABEL) as ItemKind[]).map((k) => (
            <option key={k} value={k}>
              {KIND_LABEL[k]}
            </option>
          ))}
        </select>
        <select
          value={itemId}
          onChange={(e) => {
            setItemId(e.target.value);
            setScrollSpell('');
          }}
        >
          <option value="">Choose an item…</option>
          {options.map((it) => (
            <option key={it.id} value={it.id}>
              {it.name}
              {it.kind === 'caster' || it.kind === 'magic' || it.kind === 'scroll' || it.kind === 'general'
                ? ` · ${displayPrice(it, 1) ?? '?'} g`
                : ''}
            </option>
          ))}
        </select>
        {needsSpell && (
          <select value={scrollSpell} onChange={(e) => setScrollSpell(e.target.value)}>
            <option value="">Spell on the scroll…</option>
            {scrollSpells.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </select>
        )}
        {tiered && (
          <input
            className="combat-num"
            type="number"
            min={1}
            max={20}
            title="Item level (1–20)"
            value={tier}
            onChange={(e) => setTier(Math.max(1, Math.min(20, Number.parseInt(e.target.value, 10) || 1)))}
          />
        )}
        <input
          className="combat-num"
          type="number"
          min={1}
          title="Quantity"
          value={quantity}
          onChange={(e) => setQuantity(Math.max(1, Number.parseInt(e.target.value, 10) || 1))}
        />
        {item?.kind === 'weapon' && (
          <label className="spell-self" title="×5 price — bypasses silver-vulnerable defenses">
            <input type="checkbox" checked={silvered} onChange={(e) => setSilvered(e.target.checked)} />
            silvered
          </label>
        )}
        <button
          className="btn btn--gold"
          onClick={() =>
            void onBuy({
              itemId,
              quantity,
              tier: tiered ? tier : undefined,
              silvered: silvered && item?.kind === 'weapon' ? true : undefined,
              spellId: needsSpell ? scrollSpell : undefined,
            })
          }
          disabled={acting || !itemId || (needsSpell && !scrollSpell) || (total != null && total > gold)}
        >
          Buy{total != null ? ` · ${total} g` : ''}
        </button>
      </div>
      {total != null && total > gold && (
        <p className="spell-prep-count">Not enough gold ({gold} g on hand).</p>
      )}
    </div>
  );
}
