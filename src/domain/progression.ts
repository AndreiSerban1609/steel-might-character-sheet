// What a level-up grants and which choices it demands (M6-B/C schedule).
// Pure display logic mirroring ProgressionService — the server validates
// every actual level-up. No React/SDK/fetch.

import characterCreationRaw from '../data/character-creation.json';
import spellcastingRaw from '../data/spellcasting.json';
import talentsRaw from '../data/talents.json';
import specializationsRaw from '../data/specializations.json';
import { casterTypeOf } from './spellCatalog';

const creation = characterCreationRaw as unknown as {
  statIncreaseLevels: number[];
  bonusPoints: number;
  maxBonusPerStat: number;
};

const spellcasting = spellcastingRaw as unknown as {
  spellsKnownProgression: Record<'minor' | 'major', { cumulative: number[] }>;
};

const talents = talentsRaw as unknown as { id: string; name: string; description: string }[];

interface RawSpec {
  name: string;
  active?: { name: string; description?: string };
  passive?: { name: string; description?: string };
  modification?: { name: string; description?: string };
  additionalTalents?: { name: string; description?: string }[];
}

const specializations = specializationsRaw as unknown as Record<string, RawSpec[]>;

export const TALENT_LEVELS = [3, 7, 11, 15, 19];
export const FEAT_LEVELS = [5, 9, 13];
export const SPEC_TALENT_LEVEL = 17;
export const BONUS_POINTS = creation.bonusPoints;
export const MAX_BONUS_PER_STAT = creation.maxBonusPerStat;

export function slug(name: string): string {
  return name.trim().toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '');
}

export function isStatIncreaseLevel(level: number): boolean {
  return creation.statIncreaseLevels.includes(level);
}

/** New spells owed at this level: the cumulative-array delta; 0 for non-casters. */
export function spellsToLearn(classId: string, newLevel: number): number {
  const type = casterTypeOf(classId);
  if (type === 'none' || newLevel < 2) return 0;
  const cumulative = spellcasting.spellsKnownProgression[type]?.cumulative;
  if (!cumulative || newLevel > cumulative.length) return 0;
  return cumulative[newLevel - 1] - cumulative[newLevel - 2];
}

export interface TalentOption {
  id: string;
  name: string;
  description?: string;
  fromSpec?: boolean;
}

function findSpec(classId: string, specializationId: string | null): RawSpec | undefined {
  if (!specializationId) return undefined;
  return (specializations[classId] ?? []).find((s) => slug(s.name) === specializationId);
}

/** The spec's 2 additional talents as {slug, name} options. */
export function specTalentOptions(classId: string, specializationId: string | null): TalentOption[] {
  const spec = findSpec(classId, specializationId);
  return (spec?.additionalTalents ?? []).map((t) => ({
    id: slug(t.name),
    name: t.name,
    description: t.description,
    fromSpec: true,
  }));
}

/** Eligible pool for a general talent pick (spec talents join from level 5, M6-C). */
export function talentPool(
  classId: string,
  specializationId: string | null,
  newLevel: number,
  owned: string[],
): TalentOption[] {
  const pool: TalentOption[] = talents.map((t) => ({ id: t.id, name: t.name, description: t.description }));
  if (newLevel >= 5) pool.push(...specTalentOptions(classId, specializationId));
  return pool
    .filter((t) => !owned.includes(t.id))
    .sort((a, b) => a.name.localeCompare(b.name));
}

export interface FeatOption {
  slot: 'active' | 'passive' | 'modification';
  name: string;
  description?: string;
}

/** The spec's three feats, minus the already-taken slots. */
export function featOptions(
  classId: string,
  specializationId: string | null,
  taken: string[],
): FeatOption[] {
  const spec = findSpec(classId, specializationId);
  if (!spec) return [];
  const all: FeatOption[] = [];
  if (spec.active) all.push({ slot: 'active', name: spec.active.name, description: spec.active.description });
  if (spec.passive) all.push({ slot: 'passive', name: spec.passive.name, description: spec.passive.description });
  if (spec.modification) {
    all.push({ slot: 'modification', name: spec.modification.name, description: spec.modification.description });
  }
  return all.filter((f) => !taken.includes(f.slot));
}

/** Level 17: which of the 2 spec talents are still missing (0 = nothing to do,
 *  1 = auto-granted by the server, 2 = the player must pick one). */
export function missingSpecTalents(
  classId: string,
  specializationId: string | null,
  owned: string[],
): TalentOption[] {
  return specTalentOptions(classId, specializationId).filter((t) => !owned.includes(t.id));
}
