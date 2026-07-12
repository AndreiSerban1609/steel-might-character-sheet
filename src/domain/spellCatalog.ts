// Spell picker/display data for the Spellbook panel. Pure display logic —
// no React/SDK/fetch. Sources: the ten spells-*.json files, class-abilities.json
// (casterType/spellStat) and spellcasting.json (level access, INT-bonus rules).

import spellsArcher from '../data/spells-archer.json';
import spellsBattlemage from '../data/spells-battlemage.json';
import spellsCorruptor from '../data/spells-corruptor.json';
import spellsDisciple from '../data/spells-disciple.json';
import spellsMusician from '../data/spells-musician.json';
import spellsRogue from '../data/spells-rogue.json';
import spellsWarrior from '../data/spells-warrior.json';
import spellsWildborn from '../data/spells-wildborn.json';
import spellsWizard from '../data/spells-wizard.json';
import spellsWraithHunter from '../data/spells-wraith-hunter.json';
import classAbilitiesRaw from '../data/class-abilities.json';
import spellcastingRaw from '../data/spellcasting.json';

// The data is looser than types.ts admits: costs can be strings ("10%",
// "reaction", "1 or 2") and dice can be "2d10" or {count, sides}.
export interface SpellDiceFormula {
  modMultiplier: number;
  flat: number;
  dice: string | { count: number; sides: number } | null;
}

export interface SpellScaling {
  manaCostIncrease: number | null;
  damageIncrease?: SpellDiceFormula | null;
  healingIncrease?: SpellDiceFormula | null;
}

export interface SpellEntry {
  id: string;
  name: string;
  classId: string;
  level: number;
  apCost: number | string;
  manaCost: number | string;
  range: string;
  components: string[];
  duration: string | null;
  concentration: boolean;
  channeling: boolean;
  damageType: string | null;
  attackType: string | null;
  saveStat: string | null;
  damage?: SpellDiceFormula | null;
  healing?: SpellDiceFormula | null;
  effects?: string[] | null;
  scaling?: SpellScaling | null;
  description: string;
}

const ALL_SPELLS: SpellEntry[] = [
  spellsArcher, spellsBattlemage, spellsCorruptor, spellsDisciple, spellsMusician,
  spellsRogue, spellsWarrior, spellsWildborn, spellsWizard, spellsWraithHunter,
].flat() as unknown as SpellEntry[];

const spellsById = new Map(ALL_SPELLS.map((s) => [s.id, s]));

const spellsByClass = new Map<string, SpellEntry[]>();
for (const spell of ALL_SPELLS) {
  const list = spellsByClass.get(spell.classId) ?? [];
  list.push(spell);
  spellsByClass.set(spell.classId, list);
}
for (const list of spellsByClass.values()) {
  list.sort((a, b) => a.level - b.level || a.name.localeCompare(b.name));
}

export function spellById(id: string): SpellEntry | undefined {
  return spellsById.get(id);
}

export function spellName(id: string): string {
  return spellsById.get(id)?.name ?? id;
}

/** All of a class's spells, sorted by level then name. */
export function spellsForClass(classId: string): SpellEntry[] {
  return spellsByClass.get(classId) ?? [];
}

/** Spells a scroll of the given level + caster tier can hold (Shops p.17). */
export function spellsForScroll(spellLevel: number, tier: 'minor' | 'major'): SpellEntry[] {
  return ALL_SPELLS
    .filter((s) => s.level === spellLevel && casterTypeOf(s.classId) === tier)
    .sort((a, b) => a.name.localeCompare(b.name));
}

// ── Caster identity & level access ──

export type CasterType = 'major' | 'minor' | 'none';

interface ClassAbilitiesEntry {
  casterType?: CasterType;
  spellStat?: string | null;
}

const classAbilities = classAbilitiesRaw as unknown as Record<string, ClassAbilitiesEntry>;

const spellcasting = spellcastingRaw as unknown as {
  spellLevelAccess: Record<'minor' | 'major', number[]>;
};

export function casterTypeOf(classId: string): CasterType {
  return classAbilities[classId]?.casterType ?? 'none';
}

/** Highest castable spell level (spellcasting.json → spellLevelAccess). */
export function maxSpellLevel(classId: string, characterLevel: number): number {
  const type = casterTypeOf(classId);
  if (type === 'none') return 0;
  const access = spellcasting.spellLevelAccess[type];
  const idx = Math.min(Math.max(characterLevel, 1), access.length) - 1;
  return access[idx];
}

// ── Display formatting ──

export function formatCost(cost: number | string, unit: string): string {
  return typeof cost === 'number' ? `${cost} ${unit}` : String(cost);
}

export function formatDice(formula: SpellDiceFormula | null | undefined): string {
  if (!formula) return '';
  const parts: string[] = [];
  if (formula.dice) {
    parts.push(
      typeof formula.dice === 'string' ? formula.dice : `${formula.dice.count}d${formula.dice.sides}`,
    );
  }
  if (formula.flat) parts.push(String(formula.flat));
  if (formula.modMultiplier) {
    parts.push(formula.modMultiplier === 1 ? 'mod' : `${formula.modMultiplier}×mod`);
  }
  return parts.join(' + ');
}
