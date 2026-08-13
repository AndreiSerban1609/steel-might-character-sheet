import { useEffect, useState } from 'react';
import { useCharacterStore } from '../application/characterStore';
import type { CustomItemView } from '../platform/types';

const DAMAGE_TYPES = [
  'slashing', 'piercing', 'crushing', 'true',
  'pure', 'spectral', 'light', 'shadow', 'fire', 'ice', 'lightning',
  'poison', 'thunder', 'psychic', 'force',
];
const STATS = ['str', 'dex', 'con', 'int', 'wis', 'will', 'cha'];
const ARMOR_TYPES = ['light', 'medium', 'heavy', 'shield'];

function blank(kind: 'WEAPON' | 'ARMOR'): CustomItemView {
  return {
    id: null,
    name: '',
    kind,
    inventorySpace: 1,
    properties: '',
    proficient: true,
    damageDice: kind === 'WEAPON' ? '1d8' : null,
    damageFlat: kind === 'WEAPON' ? 0 : null,
    damageType: kind === 'WEAPON' ? 'slashing' : null,
    damageScaling: kind === 'WEAPON' ? 0 : null,
    weaponStat: kind === 'WEAPON' ? 'str' : null,
    apCost: kind === 'WEAPON' ? 3 : null,
    armorType: kind === 'ARMOR' ? 'light' : null,
    acBase: kind === 'ARMOR' ? 11 : null,
    acDexMod: kind === 'ARMOR' ? true : null,
    pa: kind === 'ARMOR' ? 0 : null,
    ma: kind === 'ARMOR' ? 0 : null,
    paScaling: kind === 'ARMOR' ? 0 : null,
    maScaling: kind === 'ARMOR' ? 0 : null,
  };
}

function num(raw: string): number | null {
  const v = Number.parseFloat(raw);
  return Number.isNaN(v) ? null : v;
}

/**
 * Player/GM-defined weapons and armor (demo feedback #19). Once defined, an item is a
 * normal id: add it through the inventory editor, equip it, attack with it. Definitions
 * are per-character and granted rather than bought — there is no price.
 */
export function CustomGearEditor() {
  const customItems = useCharacterStore((s) => s.customItems);
  const loadCustomItems = useCharacterStore((s) => s.loadCustomItems);
  const saveCustomItems = useCharacterStore((s) => s.saveCustomItems);
  const saving = useCharacterStore((s) => s.saving);

  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState<CustomItemView[] | null>(null);

  useEffect(() => {
    void loadCustomItems();
  }, [loadCustomItems]);

  const list = draft ?? customItems;

  function patch(i: number, changes: Partial<CustomItemView>) {
    setDraft((d) => (d ?? customItems).map((it, idx) => (idx === i ? { ...it, ...changes } : it)));
  }

  async function save() {
    if (!draft) return;
    await saveCustomItems(draft);
    if (!useCharacterStore.getState().error) setDraft(null);
  }

  return (
    <div className="audit">
      <div className="audit-head">
        <button className="btn btn--ghost" onClick={() => setOpen(!open)}>
          {open ? '▾ Custom gear' : '▸ Custom gear'}
          {customItems.length > 0 && <span className="custom-gear-count"> {customItems.length}</span>}
        </button>
        {open && draft === null && (
          <button className="btn btn--ghost" onClick={() => setDraft([...customItems])}>
            Edit
          </button>
        )}
        {open && draft !== null && (
          <div className="edit-actions">
            <button className="btn btn--ghost" onClick={() => setDraft(null)} disabled={saving}>
              Cancel
            </button>
            <button className="btn btn--gold" onClick={save} disabled={saving}>
              {saving ? 'Saving…' : 'Save'}
            </button>
          </div>
        )}
      </div>

      {open && (
        <>
          <p className="override-note">
            Homebrew weapons and armor for this character. They behave like catalog gear —
            equip them, attack with them, they feed AC/PA/MA and carry weight — but they are
            granted, not bought, so they have no price and the shop ignores them. Add one to
            your inventory with the <strong>Edit</strong> button above once it's defined.
          </p>

          {list.length === 0 && <p className="deck-empty">Nothing defined yet.</p>}

          {list.map((item, i) => (
            <div className="ability-row" key={item.id ?? `new-${i}`}>
              <div className="inv-custom-head">
                <input
                  className="inv-custom-name"
                  placeholder="Item name"
                  value={item.name}
                  disabled={draft === null}
                  onChange={(e) => patch(i, { name: e.target.value })}
                />
                <select
                  value={item.kind}
                  disabled={draft === null}
                  onChange={(e) => {
                    const kind = e.target.value as 'WEAPON' | 'ARMOR';
                    patch(i, { ...blank(kind), id: item.id, name: item.name, kind });
                  }}
                >
                  <option value="WEAPON">Weapon</option>
                  <option value="ARMOR">Armor</option>
                </select>
                {draft !== null && (
                  <button
                    className="combat-effect-remove"
                    title="Delete this definition"
                    onClick={() => setDraft(list.filter((_, idx) => idx !== i))}
                  >
                    ×
                  </button>
                )}
              </div>

              <div className="inv-custom-grid">
                <Field label="Slots">
                  <input
                    type="number"
                    min={0}
                    step={0.5}
                    value={item.inventorySpace ?? 1}
                    disabled={draft === null}
                    onChange={(e) => patch(i, { inventorySpace: num(e.target.value) })}
                  />
                </Field>

                {item.kind === 'WEAPON' ? (
                  <>
                    <Field label="Dice">
                      <input
                        placeholder="1d8"
                        value={item.damageDice ?? ''}
                        disabled={draft === null}
                        onChange={(e) => patch(i, { damageDice: e.target.value })}
                      />
                    </Field>
                    <Field label="Flat dmg">
                      <input
                        type="number"
                        value={item.damageFlat ?? 0}
                        disabled={draft === null}
                        onChange={(e) => patch(i, { damageFlat: num(e.target.value) })}
                      />
                    </Field>
                    <Field label="Type">
                      <select
                        value={item.damageType ?? 'slashing'}
                        disabled={draft === null}
                        onChange={(e) => patch(i, { damageType: e.target.value })}
                      >
                        {DAMAGE_TYPES.map((d) => (
                          <option key={d} value={d}>
                            {d}
                          </option>
                        ))}
                      </select>
                    </Field>
                    <Field label="Stat">
                      <select
                        value={item.weaponStat ?? 'str'}
                        disabled={draft === null}
                        onChange={(e) => patch(i, { weaponStat: e.target.value })}
                      >
                        {STATS.map((s) => (
                          <option key={s} value={s}>
                            {s.toUpperCase()}
                          </option>
                        ))}
                      </select>
                    </Field>
                    <Field label="AP cost">
                      <input
                        type="number"
                        min={0}
                        max={30}
                        value={item.apCost ?? 3}
                        disabled={draft === null}
                        onChange={(e) => patch(i, { apCost: num(e.target.value) })}
                      />
                    </Field>
                    <Field label="Dmg/tier" hint="Flat damage gained per upgrade tier">
                      <input
                        type="number"
                        value={item.damageScaling ?? 0}
                        disabled={draft === null}
                        onChange={(e) => patch(i, { damageScaling: num(e.target.value) })}
                      />
                    </Field>
                  </>
                ) : (
                  <>
                    <Field label="Armor type">
                      <select
                        value={item.armorType ?? 'light'}
                        disabled={draft === null}
                        onChange={(e) => patch(i, { armorType: e.target.value })}
                      >
                        {ARMOR_TYPES.map((a) => (
                          <option key={a} value={a}>
                            {a}
                          </option>
                        ))}
                      </select>
                    </Field>
                    <Field label="AC base" hint="Shields add this on top of your AC">
                      <input
                        type="number"
                        value={item.acBase ?? 10}
                        disabled={draft === null}
                        onChange={(e) => patch(i, { acBase: num(e.target.value) })}
                      />
                    </Field>
                    <Field label="+DEX">
                      <input
                        type="checkbox"
                        checked={item.acDexMod ?? false}
                        disabled={draft === null}
                        onChange={(e) => patch(i, { acDexMod: e.target.checked })}
                      />
                    </Field>
                    <Field label="PA">
                      <input
                        type="number"
                        value={item.pa ?? 0}
                        disabled={draft === null}
                        onChange={(e) => patch(i, { pa: num(e.target.value) })}
                      />
                    </Field>
                    <Field label="MA">
                      <input
                        type="number"
                        value={item.ma ?? 0}
                        disabled={draft === null}
                        onChange={(e) => patch(i, { ma: num(e.target.value) })}
                      />
                    </Field>
                    <Field label="PA/lvl">
                      <input
                        type="number"
                        value={item.paScaling ?? 0}
                        disabled={draft === null}
                        onChange={(e) => patch(i, { paScaling: num(e.target.value) })}
                      />
                    </Field>
                    <Field label="MA/lvl">
                      <input
                        type="number"
                        value={item.maScaling ?? 0}
                        disabled={draft === null}
                        onChange={(e) => patch(i, { maScaling: num(e.target.value) })}
                      />
                    </Field>
                  </>
                )}

                <Field label="Properties" hint="Comma-separated: two-handed, light, finesse…">
                  <input
                    placeholder="two-handed"
                    value={item.properties ?? ''}
                    disabled={draft === null}
                    onChange={(e) => patch(i, { properties: e.target.value })}
                  />
                </Field>
                <Field label="Proficient" hint="Off = the usual non-proficiency penalties apply">
                  <input
                    type="checkbox"
                    checked={item.proficient ?? true}
                    disabled={draft === null}
                    onChange={(e) => patch(i, { proficient: e.target.checked })}
                  />
                </Field>
              </div>
            </div>
          ))}

          {draft !== null && (
            <div className="inv-add">
              <button
                className="btn btn--ghost"
                onClick={() => setDraft([...(draft ?? []), blank('WEAPON')])}
              >
                + Weapon
              </button>
              <button
                className="btn btn--ghost"
                onClick={() => setDraft([...(draft ?? []), blank('ARMOR')])}
              >
                + Armor
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
}

function Field({
  label,
  hint,
  children,
}: {
  label: string;
  hint?: string;
  children: React.ReactNode;
}) {
  return (
    <label className="inv-custom-field" title={hint}>
      <span className="override-label">{label}</span>
      {children}
    </label>
  );
}
