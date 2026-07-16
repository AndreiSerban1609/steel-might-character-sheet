/**
 * Display helpers for the party's mirrored OBR viewports (Story 3.1).
 * The mirrored data comes from other clients over room metadata, so it is
 * shape-checked rather than trusted — an older build (or a non-combat viewport)
 * simply yields null and the roster falls back to the server snapshot.
 */

export interface LiveVitals {
  currentHp: number;
  maxHp: number;
  ac: number;
}

/** Extract live vitals from a mirrored slice when it is a combat viewport. */
export function liveVitalsFromSlice(
  slice: { viewport: string; data: unknown } | undefined,
): LiveVitals | null {
  if (!slice || slice.viewport !== 'combat') return null;
  const data = slice.data as { hp?: { current?: unknown; max?: unknown }; ac?: unknown } | null;
  const currentHp = data?.hp?.current;
  const maxHp = data?.hp?.max;
  const ac = data?.ac;
  if (typeof currentHp !== 'number' || typeof maxHp !== 'number' || typeof ac !== 'number') {
    return null;
  }
  return { currentHp, maxHp, ac };
}
