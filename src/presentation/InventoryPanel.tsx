import { useEffect, useMemo, useState } from 'react';
import { useCharacterStore } from '../application/characterStore';
import type { InventoryItemInput } from '../platform/types';
import { ITEM_CATALOG, KIND_LABEL, itemById, itemName } from '../domain/itemCatalog';

function spaceOf(itemId: string): number {
  return itemById(itemId)?.space ?? 1;
}

function round1(n: number): number {
  return Math.round(n * 10) / 10;
}

export function InventoryPanel() {
  const inventory = useCharacterStore((s) => s.inventory);
  const saving = useCharacterStore((s) => s.saving);
  const error = useCharacterStore((s) => s.error);
  const loadInventory = useCharacterStore((s) => s.loadInventory);
  const saveInventory = useCharacterStore((s) => s.saveInventory);

  const [draft, setDraft] = useState<{ items: InventoryItemInput[]; gold: number } | null>(null);
  const [pick, setPick] = useState('');

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
          <button className="btn btn--ghost" onClick={startEdit}>
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
            value={draft!.gold}
            onChange={(e) => setDraft({ ...draft!, gold: Math.max(0, Number.parseInt(e.target.value, 10) || 0) })}
          />
        ) : (
          <span className="inv-gold-val">{inventory.gold} cp</span>
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
                <div className="inv-row" key={`${it.itemId}-${i}`}>
                  <div className="inv-name">
                    <span>{itemName(it.itemId)}</span>
                    {cat && <span className="inv-kind">{KIND_LABEL[cat.kind]}</span>}
                    {it.equipped && <span className="inv-equipped">equipped</span>}
                  </div>
                  <span className="inv-qty-view">×{it.quantity}</span>
                  <span className="inv-space">{round1(it.space * it.quantity)} sl</span>
                </div>
              );
            })}
      </div>
    </>
  );
}
