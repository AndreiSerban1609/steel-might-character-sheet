import OBR from '@owlbear-rodeo/sdk';

/**
 * OBR room metadata is a broadcast mirror, never the authority (ARCHITECTURE.md §2).
 * The 16 kB cap applies to the room's metadata as a whole, so each player mirrors
 * only the viewport they are currently looking at; switching tabs swaps the slice.
 */
export type Viewport = 'combat' | 'bio' | 'inventory' | 'spellbook';

/** One player's mirrored slice: which viewport they broadcast and its snapshot. */
export interface SheetSlice {
  viewport: Viewport;
  data: unknown;
}

const KEY_PREFIX = 'com.deckoffates.sheets';

/** Room-level pseudo-player segment — holds shared state like the encounter. */
const ROOM_SEGMENT = 'room';
const ENCOUNTER_KEY = `${KEY_PREFIX}/${ROOM_SEGMENT}/encounter`;
/** Pre-2026-08 single-draw key — still read so mid-deploy clients stay visible. */
const LEGACY_DRAW_KEY = `${KEY_PREFIX}/${ROOM_SEGMENT}/draw`;
const DRAW_KEY_PREFIX = `${KEY_PREFIX}/${ROOM_SEGMENT}/draw/`;

// Budget for a single viewport slice. The room-wide cap is 16 kB shared with the
// Deck of Fates keys, so anything near this size is already a design problem.
const MAX_SLICE_CHARS = 8_000;

// Warn when the room's total metadata nears the OBR cap — the original Deck of
// Fates died exactly this way (history accumulated until writes failed and the
// room needed a manual flush). The mirror must degrade, never break.
const ROOM_BUDGET_WARN_CHARS = 12_000;

/** What consumers receive when no encounter is mirrored (matches the server's inactive view). */
export const INACTIVE_ENCOUNTER = Object.freeze({
  active: false,
  round: 0,
  currentPlayerId: null,
  turnStarted: false,
  entries: [] as unknown[],
});

/**
 * All metadata writes go through here: the mirror is best-effort by design
 * (the server is the authority), so a full room must log and degrade — the
 * original Deck of Fates hard-failed instead, which is the bug class this
 * module exists to avoid.
 */
async function safeSetMetadata(update: Record<string, unknown>, context: string): Promise<void> {
  try {
    await OBR.room.setMetadata(update);
  } catch (e) {
    console.warn(`[sheets] room metadata write failed (${context}) — likely over the 16 kB cap; ` +
      'the table keeps playing off the server, but consider "Reset table sync" on the GM roster', e);
  }
}

function viewportKey(playerId: string, viewport: Viewport): string {
  return `${KEY_PREFIX}/${playerId}/${viewport}`;
}

/** Last key written per player, so a viewport swap clears the stale slice. */
const lastKeyByPlayer = new Map<string, string>();

/**
 * Mirror a snapshot into room metadata under `com.deckoffates.sheets/{playerId}/{viewport}`,
 * deleting the player's previously mirrored viewport if it was a different one.
 * No-op outside OBR. Oversized slices are dropped with a warning rather than
 * risking the room-wide metadata cap.
 */
export async function writeViewport(
  playerId: string,
  viewport: Viewport,
  data: unknown,
): Promise<void> {
  if (!OBR.isAvailable) return;
  const size = JSON.stringify(data).length;
  if (size > MAX_SLICE_CHARS) {
    console.warn(`[sheets] ${viewport} viewport for ${playerId} is ${size} chars — not mirrored`);
    return;
  }
  const key = viewportKey(playerId, viewport);
  const update: Record<string, unknown> = { [key]: data };
  const previous = lastKeyByPlayer.get(playerId);
  if (previous && previous !== key) update[previous] = undefined; // undefined deletes the key
  lastKeyByPlayer.set(playerId, key);
  await safeSetMetadata(update, `viewport ${viewport} for ${playerId}`);
}

/** Read a mirrored viewport (e.g. Deck of Fates pulling ability modifiers). */
export async function readViewport<T>(playerId: string, viewport: Viewport): Promise<T | null> {
  if (!OBR.isAvailable) return null;
  const metadata = await OBR.room.getMetadata();
  return (metadata[viewportKey(playerId, viewport)] as T | undefined) ?? null;
}

function sheetSlicesFrom(metadata: Record<string, unknown>): Record<string, SheetSlice> {
  const slices: Record<string, SheetSlice> = {};
  for (const [key, value] of Object.entries(metadata)) {
    if (!key.startsWith(`${KEY_PREFIX}/`) || value === undefined) continue;
    const [, playerId, viewport] = key.split('/');
    if (!playerId || playerId === ROOM_SEGMENT || !viewport) continue;
    slices[playerId] = { viewport: viewport as Viewport, data: value };
  }
  return slices;
}

/** Snapshot of every mirrored sheet slice in the room, keyed by player id. */
export async function readAllViewports(): Promise<Record<string, SheetSlice>> {
  if (!OBR.isAvailable) return {};
  return sheetSlicesFrom(await OBR.room.getMetadata());
}

/**
 * Subscribe to the room's sheet viewports. The handler receives the complete
 * slice record on every metadata change. Returns an unsubscribe function.
 */
export function subscribeViewports(
  handler: (slices: Record<string, SheetSlice>) => void,
): () => void {
  if (!OBR.isAvailable) return () => {};
  return OBR.room.onMetadataChange((metadata) => handler(sheetSlicesFrom(metadata)));
}

/**
 * Mirror the room's encounter state (turn order) under a room-level key so
 * clients get pushed updates instead of polling the server (Story 3.2).
 * An ended/inactive encounter DELETES the key — room metadata is a viewport,
 * not a ledger; nothing may accumulate in it after its moment has passed.
 */
export async function writeEncounter(view: unknown): Promise<void> {
  if (!OBR.isAvailable) return;
  const inactive = view == null || (view as { active?: boolean }).active === false;
  if (inactive) {
    await safeSetMetadata({ [ENCOUNTER_KEY]: undefined }, 'encounter cleanup');
    return;
  }
  const size = JSON.stringify(view).length;
  if (size > MAX_SLICE_CHARS) {
    console.warn(`[sheets] encounter view is ${size} chars — not mirrored`);
    return;
  }
  await safeSetMetadata({ [ENCOUNTER_KEY]: view }, 'encounter');
}

/**
 * Mirror one character's in-progress skill-check draw under its own key
 * (`room/draw/{playerId}`), so concurrent draws never overwrite each other.
 * Passing null deletes that character's key: the check was accepted or
 * dismissed, and the mirror never keeps anything past its moment.
 */
export async function writeTableDraw(playerId: string, draw: unknown | null): Promise<void> {
  if (!OBR.isAvailable) return;
  const key = DRAW_KEY_PREFIX + playerId;
  if (draw == null) {
    await safeSetMetadata({ [key]: undefined }, 'draw cleanup');
    return;
  }
  const size = JSON.stringify(draw).length;
  if (size > MAX_SLICE_CHARS) {
    console.warn(`[sheets] table draw is ${size} chars — not mirrored`);
    return;
  }
  await safeSetMetadata({ [key]: draw }, 'table draw');
}

function tableDrawsFrom(metadata: Record<string, unknown>): Record<string, unknown> {
  const draws: Record<string, unknown> = {};
  // A not-yet-updated client may still mirror the old single key; adopt it
  // under its payload's playerId so mixed-version tables keep seeing draws.
  const legacy = metadata[LEGACY_DRAW_KEY] as { playerId?: string } | undefined;
  if (legacy?.playerId) draws[legacy.playerId] = legacy;
  for (const [key, value] of Object.entries(metadata)) {
    if (!key.startsWith(DRAW_KEY_PREFIX) || value == null) continue;
    const playerId = key.slice(DRAW_KEY_PREFIX.length);
    if (playerId) draws[playerId] = value;
  }
  return draws;
}

/** All mirrored in-progress draws, keyed by the drawing character's player id. */
export async function readTableDraws(): Promise<Record<string, unknown>> {
  if (!OBR.isAvailable) return {};
  return tableDrawsFrom(await OBR.room.getMetadata());
}

/** Subscribe to the room's mirrored draws; fires with the full record per change. */
export function subscribeTableDraws(
  handler: (draws: Record<string, unknown>) => void,
): () => void {
  if (!OBR.isAvailable) return () => {};
  return OBR.room.onMetadataChange((metadata) => {
    handler(tableDrawsFrom(metadata));
  });
}

/** Read the mirrored encounter state, if any client has broadcast one. */
export async function readEncounter(): Promise<unknown> {
  if (!OBR.isAvailable) return null;
  const metadata = await OBR.room.getMetadata();
  return metadata[ENCOUNTER_KEY] ?? null;
}

/**
 * Subscribe to the mirrored encounter state. Fires on every metadata change;
 * an absent key is delivered as null (the encounter ended — its key is deleted,
 * not stored as an inactive tombstone).
 */
export function subscribeEncounter(handler: (view: unknown | null) => void): () => void {
  if (!OBR.isAvailable) return () => {};
  return OBR.room.onMetadataChange((metadata) => {
    handler(metadata[ENCOUNTER_KEY] ?? null);
  });
}

/**
 * Delete sheet slices whose player no longer exists in the room's roster —
 * orphans from renamed/deleted characters or long-gone sessions. Without this,
 * keys accumulate across sessions until the 16 kB room cap breaks every write
 * (the original Deck of Fates history bug). Returns the number of keys removed.
 */
export async function sweepStaleSlices(validPlayerIds: string[]): Promise<number> {
  if (!OBR.isAvailable) return 0;
  try {
    const metadata = await OBR.room.getMetadata();
    const valid = new Set(validPlayerIds);
    const doomed: Record<string, undefined> = {};
    for (const key of Object.keys(metadata)) {
      if (!key.startsWith(`${KEY_PREFIX}/`)) continue;
      const [, playerId] = key.split('/');
      if (!playerId || playerId === ROOM_SEGMENT) continue;
      if (!valid.has(playerId)) doomed[key] = undefined;
    }
    const count = Object.keys(doomed).length;
    if (count > 0) {
      await safeSetMetadata(doomed, 'stale-slice sweep');
      console.info(`[sheets] swept ${count} stale sheet key(s) from room metadata`);
    }
    const usage = totalChars(metadata) - charsOf(doomed, metadata);
    if (usage > ROOM_BUDGET_WARN_CHARS) {
      console.warn(`[sheets] room metadata is ~${usage} chars after sweeping — nearing the 16 kB cap. ` +
        'Other extensions (e.g. standalone Deck of Fates keys) may be holding the rest.');
    }
    return count;
  } catch (e) {
    console.warn('[sheets] stale-slice sweep failed', e);
    return 0;
  }
}

/**
 * Flush every key this app owns from room metadata (the GM's one-click fix the
 * original Deck of Fates never had). Slices rebuild on each client's next
 * broadcast; pass keepEncounter to preserve a live turn order through the flush.
 * Returns the number of keys removed.
 */
export async function clearSheetMetadata(keepEncounter = false): Promise<number> {
  if (!OBR.isAvailable) return 0;
  try {
    const metadata = await OBR.room.getMetadata();
    const doomed: Record<string, undefined> = {};
    for (const key of Object.keys(metadata)) {
      if (!key.startsWith(`${KEY_PREFIX}/`)) continue;
      if (keepEncounter && key === ENCOUNTER_KEY) continue;
      doomed[key] = undefined;
    }
    const count = Object.keys(doomed).length;
    if (count > 0) await safeSetMetadata(doomed, 'full flush');
    lastKeyByPlayer.clear(); // next writes re-establish their own keys
    console.info(`[sheets] flushed ${count} sheet key(s) from room metadata`);
    return count;
  } catch (e) {
    console.warn('[sheets] metadata flush failed', e);
    return 0;
  }
}

function totalChars(metadata: Record<string, unknown>): number {
  let sum = 0;
  for (const [key, value] of Object.entries(metadata)) {
    sum += key.length + JSON.stringify(value ?? null).length;
  }
  return sum;
}

function charsOf(doomed: Record<string, undefined>, metadata: Record<string, unknown>): number {
  let sum = 0;
  for (const key of Object.keys(doomed)) {
    sum += key.length + JSON.stringify(metadata[key] ?? null).length;
  }
  return sum;
}
