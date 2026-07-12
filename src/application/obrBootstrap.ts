import { getObrIdentity, isObrAvailable, obrReady } from '../platform/obrClient';
import { writeViewport, type Viewport } from '../platform/metadataGateway';
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
