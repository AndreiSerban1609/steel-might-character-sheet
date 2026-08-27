// Class-ability catalog for the Abilities panel. Pure display logic — no React/SDK/fetch.
// Sources: the six abilities-*.json files (Epic 1). The server loads the same files and is
// the authority; this module only joins ids to display data.

import type { CombatSnapshot } from '../platform/types';
import abilitiesArcher from '../data/abilities-archer.json';
import abilitiesMonk from '../data/abilities-monk.json';
import abilitiesRogue from '../data/abilities-rogue.json';
import abilitiesWarrior from '../data/abilities-warrior.json';
import abilitiesWildborn from '../data/abilities-wildborn.json';
import abilitiesWraithHunter from '../data/abilities-wraith-hunter.json';

export interface AbilityCostEntry {
  resource: string;
  amount?: number | null;
  amountDice?: { count: number; sides: number } | null;
}

export interface AbilityEntry {
  id: string;
  name: string;
  classId: string;
  minLevel: number;
  group: string | null;
  kind: 'active' | 'reaction' | 'attack-enhancer' | 'passive';
  apCost: number | string | null;
  costs: AbilityCostEntry[];
  resolution: 'auto' | 'manual';
  usesPerRest?: { stat?: string; min?: number; amount?: number } | null;
  usesPerTurn?: number | null;
  /** Structured effect the ability puts on a target — lands on a named combatant (Story 2.3). */
  targetEffect?: { effectId: string } | null;
  description: string;
}

const ALL_ABILITIES: AbilityEntry[] = [
  abilitiesArcher, abilitiesMonk, abilitiesRogue,
  abilitiesWarrior, abilitiesWildborn, abilitiesWraithHunter,
].flatMap((doc) => (doc as unknown as { abilities: AbilityEntry[] }).abilities);

const byId = new Map(ALL_ABILITIES.map((a) => [a.id, a]));

const byClass = new Map<string, AbilityEntry[]>();
for (const ability of ALL_ABILITIES) {
  const list = byClass.get(ability.classId) ?? [];
  list.push(ability);
  byClass.set(ability.classId, list);
}
for (const list of byClass.values()) {
  list.sort((a, b) => a.minLevel - b.minLevel || a.name.localeCompare(b.name));
}

export function abilityById(id: string): AbilityEntry | undefined {
  return byId.get(id);
}

export function abilitiesForClass(classId: string | null | undefined): AbilityEntry[] {
  return classId ? (byClass.get(classId) ?? []) : [];
}

export function classHasAbilities(classId: string | null | undefined): boolean {
  return abilitiesForClass(classId).length > 0;
}

/** "2 AP · 15 energy · 1 perseverance" — a compact cost summary for list rows. */
export function formatAbilityCost(a: AbilityEntry): string {
  return abilityCostParts(a, null).map((p) => p.label).join(' · ') || 'free';
}

/** One cost component with what the character currently has of that resource. */
export interface CostPart {
  label: string;
  /** Current amount of the backing resource, or null when the sheet doesn't track it. */
  available: number | null;
  /** The flat part of the cost exceeds what's available (dice parts can't be pre-judged). */
  short: boolean;
}

/**
 * Cost components joined with live availability from the combat snapshot: AP, mana, the
 * class resource (energy/focus/…) and sub-pools (perseverance/fury/…). All current values
 * come from the server; this only pairs them with the static cost for display.
 */
export function abilityCostParts(a: AbilityEntry, snapshot: CombatSnapshot | null): CostPart[] {
  const parts: CostPart[] = [];
  if (typeof a.apCost === 'number' && a.apCost > 0) {
    const have = snapshot ? snapshot.ap.current : null;
    parts.push({ label: `${a.apCost} AP`, available: have, short: have !== null && a.apCost > have });
  }
  if (typeof a.apCost === 'string') {
    parts.push({ label: `${a.apCost} AP`, available: null, short: false });
  }
  for (const cost of a.costs ?? []) {
    const dice = cost.amountDice ? `${cost.amountDice.count}d${cost.amountDice.sides}` : '';
    const amount = cost.amount ?? 0;
    const value = dice ? (amount ? `${amount}+${dice}` : dice) : `${amount}`;
    const have = availableOf(cost.resource, snapshot);
    parts.push({
      label: `${value} ${cost.resource}`,
      available: have,
      short: have !== null && amount > have,
    });
  }
  return parts;
}

function availableOf(resource: string, snapshot: CombatSnapshot | null): number | null {
  if (!snapshot) return null;
  if (resource === 'mana') return snapshot.mana.current;
  if (snapshot.resource && snapshot.resource.type === resource) return snapshot.resource.current;
  const pool = snapshot.pools.find((p) => p.id === resource);
  return pool ? pool.current : null;
}

/** "offensive-maneuver-minor" → "Offensive Maneuver Minor". */
export function formatGroup(group: string): string {
  return group.split('-').map((w) => w.charAt(0).toUpperCase() + w.slice(1)).join(' ');
}

export const KIND_LABELS: Record<AbilityEntry['kind'], string> = {
  active: 'Actives',
  reaction: 'Reactions',
  'attack-enhancer': 'Attack Enhancers',
  passive: 'Passives',
};
