import { getObrIdentity, isObrAvailable, obrReady } from '../platform/obrClient';
import {
  readAllViewports,
  readEncounter,
  subscribeEncounter,
  subscribeViewports,
  writeEncounter,
  writeViewport,
  type SheetSlice,
  type Viewport,
} from '../platform/metadataGateway';
import type { EncounterView } from '../platform/types';
import { useCharacterStore, type CharacterState } from './characterStore';

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
  useCharacterStore.setState({
    roomName: identity.roomId,
    email: syntheticEmail(identity.playerId),
  });
  const { enterAsGm, enterAsPlayer } = useCharacterStore.getState();
  if (identity.role === 'GM') await enterAsGm();
  else await enterAsPlayer();
}

function sliceFor(state: CharacterState, viewport: Viewport): unknown {
  switch (viewport) {
    case 'combat':
      return state.snapshot;
    case 'bio':
      return state.bio;
    case 'inventory':
      return state.inventory;
    case 'spellbook':
      return state.spellbook;
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
    void writeViewport(playerId, viewport, slice).catch((e) => {
      console.warn('[sheets] failed to mirror viewport to OBR metadata', e);
    });
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
  };
  void readAllViewports().then(adopt);
  subscribeViewports(adopt);
}

/**
 * Story 3.2 — keep the room's encounter state live over metadata. Any client
 * whose store adopts a fresh server EncounterView (start/end/skip/turn actions)
 * broadcasts it; everyone else adopts it from metadata instead of polling.
 * The JSON guard breaks the write→change→write echo loop.
 */
function startEncounterSync(): void {
  let lastJson = '';
  const consume = (view: unknown): void => {
    const json = JSON.stringify(view);
    if (json === lastJson) return;
    lastJson = json;
    // adoptEncounter also refreshes the sheet when the turn just became ours
    useCharacterStore.getState().adoptEncounter(view as EncounterView);
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
    void writeEncounter(state.encounter).catch((e) => {
      console.warn('[sheets] failed to mirror encounter to OBR metadata', e);
    });
  });
}
