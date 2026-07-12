// Damage-type and effect pickers for the combat panel. Pure display data —
// no React/SDK/fetch. Sources: damage-types.json (categories) and effects.json.

import damageTypesRaw from '../data/damage-types.json';
import effectsRaw from '../data/effects.json';
import type { DamageTypeId } from '../platform/types';

export interface DamageTypeOption {
  id: DamageTypeId;
  label: string;
  category: 'physical' | 'magical' | 'true';
}

const damageTypes = damageTypesRaw as Record<'physical' | 'magical' | 'true', string[]>;

function cap(s: string): string {
  return s.charAt(0).toUpperCase() + s.slice(1);
}

export const DAMAGE_TYPE_OPTIONS: DamageTypeOption[] = (
  ['physical', 'magical', 'true'] as const
).flatMap((category) =>
  (damageTypes[category] ?? []).map((t) => ({
    id: t.toUpperCase() as DamageTypeId,
    label: cap(t),
    category,
  })),
);

export interface EffectOption {
  id: string;
  name: string;
  polarity: 'negative' | 'positive';
  stackBased: boolean;
  hasValue: boolean;
}

interface RawEffect {
  id: string;
  name: string;
  stackBased?: boolean;
  hasValue?: boolean;
}

const effects = effectsRaw as unknown as { negative: RawEffect[]; positive: RawEffect[] };

export const EFFECT_OPTIONS: EffectOption[] = (['negative', 'positive'] as const)
  .flatMap((polarity) =>
    (effects[polarity] ?? []).map((e) => ({
      id: e.id,
      name: e.name,
      polarity,
      stackBased: !!e.stackBased,
      hasValue: !!e.hasValue,
    })),
  )
  .sort((a, b) => a.name.localeCompare(b.name));

const effectNameById = new Map(EFFECT_OPTIONS.map((e) => [e.id, e.name]));

export function effectName(id: string): string {
  return effectNameById.get(id) ?? id;
}

export function effectOption(id: string): EffectOption | undefined {
  return EFFECT_OPTIONS.find((e) => e.id === id);
}
