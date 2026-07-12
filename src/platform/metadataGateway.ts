import OBR from '@owlbear-rodeo/sdk';

/**
 * OBR room metadata is a broadcast mirror, never the authority (ARCHITECTURE.md §2).
 * The 16 kB cap applies to the room's metadata as a whole, so each player mirrors
 * only the viewport they are currently looking at; switching tabs swaps the slice.
 */
export type Viewport = 'combat' | 'bio' | 'inventory' | 'spellbook';

const KEY_PREFIX = 'com.deckoffates.sheets';

// Budget for a single viewport slice. The room-wide cap is 16 kB shared with the
// Deck of Fates keys, so anything near this size is already a design problem.
const MAX_SLICE_CHARS = 8_000;

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
  await OBR.room.setMetadata(update);
}

/** Read a mirrored viewport (e.g. Deck of Fates pulling ability modifiers). */
export async function readViewport<T>(playerId: string, viewport: Viewport): Promise<T | null> {
  if (!OBR.isAvailable) return null;
  const metadata = await OBR.room.getMetadata();
  return (metadata[viewportKey(playerId, viewport)] as T | undefined) ?? null;
}

/**
 * Subscribe to every sheet viewport in the room. The handler fires once per
 * mirrored slice on each metadata change. Returns an unsubscribe function.
 */
export function subscribeViewports(
  handler: (playerId: string, viewport: Viewport, data: unknown) => void,
): () => void {
  if (!OBR.isAvailable) return () => {};
  return OBR.room.onMetadataChange((metadata) => {
    for (const [key, value] of Object.entries(metadata)) {
      if (!key.startsWith(`${KEY_PREFIX}/`) || value === undefined) continue;
      const [, playerId, viewport] = key.split('/');
      if (playerId && viewport) handler(playerId, viewport as Viewport, value);
    }
  });
}
