// Pure display helpers for ability scores — no React, no SDK, no fetch.

import type { AbilityScore } from '../platform/types';

export const ABILITY_ORDER: AbilityScore[] = ['STR', 'DEX', 'CON', 'INT', 'WIS', 'WILL', 'CHA'];

export const ABILITY_LABELS: Record<AbilityScore, string> = {
  STR: 'Strength',
  DEX: 'Dexterity',
  CON: 'Constitution',
  INT: 'Intelligence',
  WIS: 'Wisdom',
  WILL: 'Will',
  CHA: 'Charisma',
};

/** Modifier with an explicit sign, e.g. +2 / -1 / +0. */
export function formatModifier(mod: number): string {
  return mod >= 0 ? `+${mod}` : `${mod}`;
}

/** "musician" -> "Musician". For display of path/class ids until names are wired. */
export function titleCase(id: string): string {
  return id
    .split(/[-_]/)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');
}

/** camelCase ids -> spaced words: "severelyInjured" -> "severely injured". */
export function camelToWords(id: string): string {
  return id.replace(/([a-z0-9])([A-Z])/g, '$1 $2').toLowerCase();
}
