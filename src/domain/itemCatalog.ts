// Flattens the bundled item data into a single display catalog for the inventory
// picker and itemId → name resolution. Pure display data — no React/SDK/fetch.
// Mirrors the server's item index (GameDataProvider.buildItemIndex); items without
// an explicit inventorySpace default to 1 slot, same as the server.

import weaponsRaw from '../data/weapons.json';
import armorRaw from '../data/armor.json';
import casterRaw from '../data/caster-weapons.json';
import consumablesRaw from '../data/consumables.json';

export type ItemKind = 'weapon' | 'armor' | 'caster' | 'potion' | 'magic' | 'scroll' | 'general';

export interface CatalogItem {
  id: string;
  name: string;
  kind: ItemKind;
  space: number;
}

interface RawItem {
  id?: string;
  name?: string;
  inventorySpace?: number;
}

function spaceOf(it: RawItem): number {
  return typeof it.inventorySpace === 'number' ? it.inventorySpace : 1;
}

function fromArray(arr: unknown, kind: ItemKind): CatalogItem[] {
  if (!Array.isArray(arr)) return [];
  return (arr as RawItem[])
    .filter((it): it is Required<Pick<RawItem, 'id' | 'name'>> & RawItem => !!it.id && !!it.name)
    .map((it) => ({ id: it.id, name: it.name, kind, space: spaceOf(it) }));
}

const consumables = consumablesRaw as {
  healingPotions?: { sizes?: RawItem[] };
  magicShopItems?: RawItem[];
  scrolls?: RawItem[];
  generalShop?: RawItem[];
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
