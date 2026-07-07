// Flattens the bundled item data into a single display catalog for the inventory
// picker, itemId → name resolution, and DISPLAY prices (mirrors ShopService math —
// the server stays the authority on every actual transaction). Pure display data —
// no React/SDK/fetch. Items without an explicit inventorySpace default to 1 slot,
// same as the server.

import weaponsRaw from '../data/weapons.json';
import armorRaw from '../data/armor.json';
import casterRaw from '../data/caster-weapons.json';
import consumablesRaw from '../data/consumables.json';
import pricingRaw from '../data/pricing.json';

export type ItemKind = 'weapon' | 'armor' | 'caster' | 'potion' | 'magic' | 'scroll' | 'general';

export interface CatalogItem {
  id: string;
  name: string;
  kind: ItemKind;
  space: number;
  /** pricing.json tier key (weapons/armor/caster weapons) */
  priceTier?: string;
  /** the item's own level when the id encodes it (caster weapons) */
  itemLevel?: number;
  /** explicit price (magic/scroll/general) — one generic gold currency */
  price?: number;
  /** usesPerLongRest/charges capacity (magic items) */
  charges?: number;
  /** scrolls: the spell level written on the scroll */
  spellLevel?: number;
  /** scrolls: which caster tier's spells it holds ('minor' | 'major') */
  casterType?: string;
  /** scrolls: minimum character level to read it */
  minCharLevel?: number;
}

interface RawItem {
  id?: string;
  name?: string;
  inventorySpace?: number;
  priceTier?: string;
  itemLevel?: number;
  price?: number;
  usesPerLongRest?: number;
  charges?: number;
  spellLevel?: number;
  casterType?: string;
  minCharLevel?: number;
}

function spaceOf(it: RawItem): number {
  return typeof it.inventorySpace === 'number' ? it.inventorySpace : 1;
}

function fromArray(arr: unknown, kind: ItemKind): CatalogItem[] {
  if (!Array.isArray(arr)) return [];
  return (arr as RawItem[])
    .filter((it): it is Required<Pick<RawItem, 'id' | 'name'>> & RawItem => !!it.id && !!it.name)
    .map((it) => ({
      id: it.id,
      name: it.name,
      kind,
      space: spaceOf(it),
      priceTier: it.priceTier,
      itemLevel: it.itemLevel,
      price: it.price,
      charges: it.usesPerLongRest ?? it.charges,
      spellLevel: it.spellLevel,
      casterType: it.casterType,
      minCharLevel: it.minCharLevel,
    }));
}

const consumables = consumablesRaw as {
  healingPotions?: { sizes?: RawItem[]; pricesBySize?: Record<string, number[]> };
  magicShopItems?: RawItem[];
  scrolls?: RawItem[];
  generalShop?: RawItem[];
};

const pricing = pricingRaw as unknown as {
  tiers: Record<string, { prices: number[] }>;
  silveringMultiplier: number;
  sellbackRatio: number;
};

export const ITEM_CATALOG: CatalogItem[] = [
  ...fromArray(weaponsRaw, 'weapon'),
  ...fromArray(armorRaw, 'armor'),
  ...fromArray(casterRaw, 'caster'),
  ...fromArray(consumables.healingPotions?.sizes, 'potion'),
  ...fromArray(consumables.magicShopItems, 'magic'),
  ...fromArray(consumables.scrolls, 'scroll'),
  ...fromArray(consumables.generalShop, 'general'),
].sort((a, b) => a.name.localeCompare(b.name));

// ── Display pricing (mirror of ShopService.priceOf) ──

/** Tier pricing depends on a buyer-chosen level (weapons/armor/potions). */
export function usesTierPricing(kind: ItemKind): boolean {
  return kind === 'weapon' || kind === 'armor' || kind === 'potion';
}

export function isEquippable(kind: ItemKind): boolean {
  return kind === 'weapon' || kind === 'armor' || kind === 'caster';
}

export function isUsable(kind: ItemKind): boolean {
  return kind === 'potion' || kind === 'magic' || kind === 'general';
}

export function canUpgrade(kind: ItemKind): boolean {
  return kind === 'weapon' || kind === 'armor';
}

/** Display price in generic gold; null when the item/level has no price. */
export function displayPrice(item: CatalogItem, tier: number, silvered = false): number | null {
  let base: number | null = null;
  if (item.kind === 'weapon' || item.kind === 'armor') {
    base = item.priceTier ? (pricing.tiers[item.priceTier]?.prices[tier - 1] ?? null) : null;
  } else if (item.kind === 'caster') {
    base = item.priceTier
      ? (pricing.tiers[item.priceTier]?.prices[(item.itemLevel ?? 1) - 1] ?? null)
      : null;
  } else if (item.kind === 'potion') {
    base = consumables.healingPotions?.pricesBySize?.[item.id]?.[tier - 1] ?? null;
  } else {
    base = item.price ?? null;
  }
  if (base == null) return null;
  return silvered ? base * pricing.silveringMultiplier : base;
}

/** Half the current price, floored — what the shop pays back. */
export function displaySellback(item: CatalogItem, tier: number, silvered = false): number | null {
  const price = displayPrice(item, Math.max(1, tier), silvered);
  return price == null ? null : Math.floor(price * pricing.sellbackRatio);
}

const byId = new Map(ITEM_CATALOG.map((i) => [i.id, i]));

export function itemById(id: string): CatalogItem | undefined {
  return byId.get(id);
}

export function itemName(id: string): string {
  return byId.get(id)?.name ?? id;
}

export const KIND_LABEL: Record<ItemKind, string> = {
  weapon: 'Weapon',
  armor: 'Armor',
  caster: 'Caster',
  potion: 'Potion',
  magic: 'Magic Item',
  scroll: 'Scroll',
  general: 'General',
};
