import { getObrIdentity, isObrAvailable, obrReady } from '../platform/obrClient';
import { fetchCombatSnapshot } from '../platform/http';
import {
  INACTIVE_ENCOUNTER,
  readAllViewports,
  readEncounter,
  readTableDraw,
  subscribeEncounter,
  subscribeTableDraw,
  subscribeViewports,
  sweepStaleSlices,
  writeEncounter,
  writeTableDraw,
  writeViewport,
  type SheetSlice,
} from '../platform/metadataGateway';
import type { EncounterView, TableDraw } from '../platform/types';
import { sliceFor, useCharacterStore } from './characterStore';

/**
 * OBR-native identity (no email available from the SDK): the backend id scheme is
 * slug(room)-email, so we present the OBR room id as the room and a synthetic
 * `{playerId}@obr` as the email. Deterministic, and satisfies the server's
 * address validation without backend changes.
 */
function syntheticEmail(obrPlayerId: string): string {
  return `${obrPlayerId}@obr`;
}

/**
 * Entry point for OBR mode, called once at startup. Outside OBR this is a no-op
 * and the manual entry form remains the (dev) path in. Inside OBR it skips the
 * form entirely: players land on their sheet (or creation), GMs on the roster.
 */
export async function bootstrapObr(): Promise<void> {
  if (!isObrAvailable()) return;
  useCharacterStore.setState({ obrMode: true });
  await obrReady();
  const identity = await getObrIdentity();
  startBroadcastMirror();
  startViewportConsumer();
  startEncounterSync();
  startDrawSync();
  useCharacterStore.setState({
    roomName: identity.roomId,
    email: syntheticEmail(identity.playerId),
  });
  const { enterAsGm, enterAsPlayer } = useCharacterStore.getState();
  if (identity.role === 'GM') {
    await enterAsGm();
    // Housekeeping the original Deck of Fates never did: drop mirrored slices for
    // characters that no longer exist in this room before they crowd the 16 kB cap.
    const roster = useCharacterStore.getState().roster;
    if (roster.length > 0) void sweepStaleSlices(roster.map((r) => r.playerId));
  } else {
    await enterAsPlayer();
  }
}

/**
 * Mirror the active viewport to room metadata whenever it changes. Writes go
 * under the *viewed* character's id, so a GM resolving damage on a player's
 * sheet broadcasts to that player's slice (ARCHITECTURE.md §"DM applies damage").
 */
function startBroadcastMirror(): void {
  useCharacterStore.subscribe((state, previous) => {
    const playerId = state.selectedPlayerId;
    if (!playerId) return;
    const viewport = state.activeViewport;
    const slice = sliceFor(state, viewport);
    if (slice == null) return;
    const unchanged =
      playerId === previous.selectedPlayerId &&
      viewport === previous.activeViewport &&
      slice === sliceFor(previous, viewport);
    if (unchanged) return;
    // writeViewport never rejects — over-budget writes degrade inside the gateway
    void writeViewport(playerId, viewport, slice);
  });
}

/**
 * Story 3.1 — consume the room's mirrored slices into `partyViewports` so the
 * GM roster shows live vitals without polling the server. Seeds from the current
 * metadata, then follows changes; identical payloads are skipped to avoid
 * re-render churn (every metadata write re-delivers all keys).
 */
function startViewportConsumer(): void {
  let lastJson = '';
  const adopt = (slices: Record<string, SheetSlice>): void => {
    const json = JSON.stringify(slices);
    if (json === lastJson) return;
    lastJson = json;
    useCharacterStore.setState({ partyViewports: slices });
    maybeRefreshViewedSheet(slices);
  };
  void readAllViewports().then(adopt);
  subscribeViewports(adopt);
}

/**
 * Targeting support: when ANOTHER client acts on the character this client is
 * viewing (player-to-player damage/heal/effects), the actor mirrors the target's
 * fresh combat snapshot. Spot the mismatch against our local snapshot and
 * refetch from the server (the authority) — our own mirror writes echo back
 * byte-identical and no-op here.
 */
function maybeRefreshViewedSheet(slices: Record<string, SheetSlice>): void {
  const state = useCharacterStore.getState();
  const id = state.selectedPlayerId;
  if (!id || state.view !== 'sheet' || state.acting || state.snapshot == null) return;
  const slice = slices[id];
  if (!slice || slice.viewport !== 'combat' || slice.data == null) return;
  if (JSON.stringify(slice.data) === JSON.stringify(state.snapshot)) return;
  void fetchCombatSnapshot(id).then(
    (snap) => {
      // Re-check at resolve time — an action fired meanwhile has fresher state.
      const s = useCharacterStore.getState();
      if (!s.acting && s.selectedPlayerId === id) useCharacterStore.setState({ snapshot: snap });
    },
    () => undefined /* best-effort refresh */,
  );
}

/**
 * Story 3.2 — keep the room's encounter state live over metadata. Any client
 * whose store adopts a fresh server EncounterView (start/end/skip/turn actions)
 * broadcasts it; everyone else adopts it from metadata instead of polling.
 * The JSON guard breaks the write→change→write echo loop.
 */
/**
 * Live table draws (the "everyone sees the card" DoF behaviour): the drawing
 * client mirrors each draw/redraw under the room-level draw key; every client
 * adopts it into `tableDraw`. The drawer's own client renders the full banner
 * off `drawResult`, so the toast only shows other people's draws.
 */
function startDrawSync(): void {
  let lastJson = '';
  const consume = (draw: unknown | null): void => {
    const json = JSON.stringify(draw ?? null);
    if (json === lastJson) return;
    lastJson = json;
    useCharacterStore.setState({ tableDraw: (draw as TableDraw | null) ?? null });
  };
  void readTableDraw().then((draw) => {
    if (draw !== null) consume(draw);
  });
  subscribeTableDraw(consume);
  // Ownership is tracked locally, not via the mirrored key — the metadata echo
  // of our own write may not have landed yet when a fast draw→dismiss happens.
  let wroteDrawFor: string | null = null;
  useCharacterStore.subscribe((state, previous) => {
    if (state.drawResult === previous.drawResult) return;
    if (state.drawResult == null) {
      // Check ended (accept/dismiss/character switch). Clear the table key only
      // if the mirrored draw is still ours — a concurrent drawer may have
      // already overwritten it, and their toast must survive our cleanup.
      if (wroteDrawFor && (state.tableDraw == null || state.tableDraw.playerId === wroteDrawFor)) {
        void writeTableDraw(null);
      }
      wroteDrawFor = null;
      return;
    }
    const id = state.selectedPlayerId;
    if (!id) return;
    wroteDrawFor = id;
    const payload: TableDraw = {
      playerId: id,
      playerName: state.snapshot?.name ?? 'Someone',
      pathId: state.snapshot?.pathId ?? null,
      result: state.drawResult,
      at: Date.now(),
    };
    void writeTableDraw(payload);
  });
}

function startEncounterSync(): void {
  let lastJson = '';
  // Only treat an ABSENT key as "combat ended" after we've actually seen the key —
  // otherwise a metadata change in a never-mirrored room would clobber an active
  // encounter this client just loaded from the server (the authority).
  let sawMirroredKey = false;
  const consume = (view: unknown | null): void => {
    if (view === null && !sawMirroredKey) return;
    if (view !== null) sawMirroredKey = true;
    const effective = view ?? INACTIVE_ENCOUNTER;
    const json = JSON.stringify(effective);
    if (json === lastJson) return;
    lastJson = json;
    // adoptEncounter also refreshes the sheet when the turn just became ours
    useCharacterStore.getState().adoptEncounter(effective as EncounterView);
  };
  void readEncounter().then((view) => {
    if (view !== null) consume(view);
  });
  subscribeEncounter(consume);
  useCharacterStore.subscribe((state, previous) => {
    if (!state.encounter || state.encounter === previous.encounter) return;
    const json = JSON.stringify(state.encounter);
    if (json === lastJson) return;
    lastJson = json;
    // writeEncounter never rejects — over-budget writes degrade inside the gateway
    void writeEncounter(state.encounter);
  });
}
