import OBR from '@owlbear-rodeo/sdk';

/** Identity handed to us by Owlbear Rodeo once the iframe is ready. */
export interface ObrIdentity {
  /** OBR room id — used as the backend room name in OBR mode. */
  roomId: string;
  /** OBR player id (persistent user id, not the per-session connection id). */
  playerId: string;
  playerName: string;
  role: 'GM' | 'PLAYER';
}

/** True when the app is running inside an Owlbear Rodeo iframe. */
export function isObrAvailable(): boolean {
  return OBR.isAvailable;
}

/** Resolves once the OBR SDK is ready (immediately if it already is). */
export function obrReady(): Promise<void> {
  return new Promise((resolve) => OBR.onReady(resolve));
}

/** Read room + player identity. Call only after {@link obrReady}. */
export async function getObrIdentity(): Promise<ObrIdentity> {
  const [playerName, role] = await Promise.all([OBR.player.getName(), OBR.player.getRole()]);
  return { roomId: OBR.room.id, playerId: OBR.player.id, playerName, role };
}
