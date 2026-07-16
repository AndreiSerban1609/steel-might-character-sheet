import type {
  AbilitiesSnapshot,
  AbilityScore,
  ActionResponse,
  BioPatch,
  BioSnapshot,
  CharacterCreatedResponse,
  CombatSnapshot,
  DamageTypeId,
  DeckTemplate,
  EncounterView,
  InventoryItemInput,
  InventorySnapshot,
  PlayerDeckConfig,
  PlayerDeckView,
  RosterEntry,
  SkillCheckAccepted,
  SkillCheckResult,
  SpellbookSnapshot,
} from './types';

// API base resolution, in priority order:
//   1. ?api=<url> query param (persisted to localStorage) — handy in the OBR popover URL
//   2. localStorage (set once via the Entry screen's connection field)
//   3. VITE_API_BASE build-time env
//   4. relative `/api` (dev proxy / same-origin)
// A pasted root URL (e.g. a Cloudflare Tunnel URL) gets `/api` appended automatically.
const API_BASE_KEY = 'sm_api_base';

function normalizeBase(value: string): string {
  const b = value.trim().replace(/\/+$/, '');
  if (!b) return '/api';
  if (/^https?:\/\//i.test(b)) return b.endsWith('/api') ? b : `${b}/api`;
  return b;
}

function resolveApiBase(): string {
  if (typeof window !== 'undefined') {
    try {
      const fromQuery = new URLSearchParams(window.location.search).get('api');
      if (fromQuery) {
        const normalized = normalizeBase(fromQuery);
        window.localStorage.setItem(API_BASE_KEY, normalized);
        return normalized;
      }
      const stored = window.localStorage.getItem(API_BASE_KEY);
      if (stored) return normalizeBase(stored);
    } catch {
      /* storage blocked (e.g. partitioned iframe) — fall through */
    }
  }
  return import.meta.env.VITE_API_BASE ?? '/api';
}

let API_BASE = resolveApiBase();

/** Current effective API base (e.g. for display in a settings field). */
export function getApiBase(): string {
  return API_BASE;
}

/** Set the backend URL at runtime (persisted). Pass '' to reset to the default. */
export function setApiBase(value: string): void {
  const trimmed = value.trim();
  if (typeof window !== 'undefined') {
    try {
      if (trimmed) window.localStorage.setItem(API_BASE_KEY, normalizeBase(trimmed));
      else window.localStorage.removeItem(API_BASE_KEY);
    } catch {
      /* ignore */
    }
  }
  API_BASE = trimmed ? normalizeBase(trimmed) : (import.meta.env.VITE_API_BASE ?? '/api');
}

// ── Connectivity (Story 3.3) ──
// A fetch that rejects (network error, server down, tunnel gone) flips the app
// into offline mode; while offline, a slow probe pings /health until the server
// answers again. Gateway errors (502–504) count as offline too — a tunnel can be
// up while the backend behind it is not.

let serverOffline = false;
let probeTimer: number | null = null;
const offlineListeners = new Set<(offline: boolean) => void>();

const PROBE_INTERVAL_MS = 10_000;

/** Subscribe to offline/online transitions. Returns an unsubscribe function. */
export function subscribeConnectivity(listener: (offline: boolean) => void): () => void {
  offlineListeners.add(listener);
  return () => offlineListeners.delete(listener);
}

function setOffline(offline: boolean): void {
  if (offline === serverOffline) return;
  serverOffline = offline;
  for (const listener of offlineListeners) listener(offline);
  if (typeof window === 'undefined') return;
  if (offline && probeTimer === null) {
    probeTimer = window.setInterval(() => void probeHealth(), PROBE_INTERVAL_MS);
  } else if (!offline && probeTimer !== null) {
    window.clearInterval(probeTimer);
    probeTimer = null;
  }
}

async function probeHealth(): Promise<void> {
  try {
    const res = await fetch(`${API_BASE}/health`);
    // any real answer from the backend (even a 404 from an older build) proves
    // reachability; 5xx may be a gateway speaking for a dead server
    if (res.status < 500) setOffline(false);
  } catch {
    /* still down */
  }
}

/** fetch + connectivity bookkeeping — every HTTP call goes through here. */
async function trackedFetch(input: string, init?: RequestInit): Promise<Response> {
  let res: Response;
  try {
    res = await fetch(input, init);
  } catch {
    setOffline(true);
    throw new Error('Server unreachable — changes are not being saved.');
  }
  if (res.status < 502 || res.status > 504) setOffline(false);
  return res;
}

export interface VitalsPatch {
  currentHp?: number;
  tempHp?: number;
  currentAp?: number;
  currentMana?: number;
}

export interface IdentityPatch {
  name?: string;
  level?: number;
}

export interface CreateCharacterBody {
  roomName: string;
  email: string;
  name: string;
  raceId: string;
  pathId: string;
  classId: string;
  specializationId?: string;
  level: number;
  /** exactly a permutation of the standard array (M6-A) */
  stats: Record<AbilityScore, number>;
  /** +5 across any stats, max 2 each (N17) */
  bonusAllocation: Partial<Record<AbilityScore, number>>;
  /** exactly 3 distinct skill ids */
  skillProficiencies: string[];
  /** casters: exactly 1 level-1 spell; others: empty */
  knownSpells: string[];
}

async function errText(res: Response, path: string): Promise<string> {
  // Spring's error body carries a "message" field (server.error.include-message: always).
  try {
    const data = await res.json();
    if (data && typeof data.message === 'string' && data.message) return data.message;
  } catch {
    /* not JSON */
  }
  return `${res.status} ${res.statusText} (${path})`;
}

async function getJson<T>(path: string): Promise<T> {
  const res = await trackedFetch(`${API_BASE}${path}`);
  if (!res.ok) throw new Error(await errText(res, path));
  return (await res.json()) as T;
}

async function sendJson<T>(method: 'POST' | 'PUT', path: string, body: unknown): Promise<T> {
  const res = await trackedFetch(`${API_BASE}${path}`, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(await errText(res, path));
  return (await res.json()) as T;
}

export function fetchRoster(room?: string): Promise<RosterEntry[]> {
  const q = room && room.trim() ? `?room=${encodeURIComponent(room.trim())}` : '';
  return getJson<RosterEntry[]>(`/characters/roster${q}`);
}

export function fetchCombatSnapshot(playerId: string): Promise<CombatSnapshot> {
  return getJson<CombatSnapshot>(`/characters/${encodeURIComponent(playerId)}/combat`);
}

export async function findCharacter(room: string, email: string): Promise<CharacterCreatedResponse | null> {
  const res = await trackedFetch(
    `${API_BASE}/characters/find?room=${encodeURIComponent(room)}&email=${encodeURIComponent(email)}`,
  );
  if (res.status === 404) return null;
  if (!res.ok) throw new Error(await errText(res, '/characters/find'));
  return (await res.json()) as CharacterCreatedResponse;
}

export function createCharacter(body: CreateCharacterBody): Promise<CharacterCreatedResponse> {
  return sendJson<CharacterCreatedResponse>('POST', '/characters', body);
}

export function updateStats(
  playerId: string,
  stats: Record<AbilityScore, number>,
): Promise<CombatSnapshot> {
  return sendJson<CombatSnapshot>('PUT', `/characters/${encodeURIComponent(playerId)}/stats`, { stats });
}

export function updateVitals(playerId: string, patch: VitalsPatch): Promise<CombatSnapshot> {
  return sendJson<CombatSnapshot>('PUT', `/characters/${encodeURIComponent(playerId)}/vitals`, patch);
}

export function updateIdentity(playerId: string, patch: IdentityPatch): Promise<CombatSnapshot> {
  return sendJson<CombatSnapshot>('PUT', `/characters/${encodeURIComponent(playerId)}/identity`, patch);
}

export function updateProficiencies(playerId: string, skillIds: string[]): Promise<CombatSnapshot> {
  return sendJson<CombatSnapshot>('PUT', `/characters/${encodeURIComponent(playerId)}/proficiencies`, {
    skillIds,
  });
}

export function skillCheck(
  playerId: string,
  skillId: string,
  advantage?: 'advantage' | 'disadvantage',
): Promise<SkillCheckResult> {
  return sendJson<SkillCheckResult>('POST', `/characters/${encodeURIComponent(playerId)}/skill-check`, {
    skillId,
    advantage: advantage ?? null,
  });
}

/** Proficiency gamble: forfeit the current card, draw the next; the d10 stays. */
export function skillCheckRedraw(playerId: string): Promise<SkillCheckResult> {
  return sendJson<SkillCheckResult>(
    'POST',
    `/characters/${encodeURIComponent(playerId)}/skill-check/redraw`,
    {},
  );
}

/** Accept the final card — applies consume/burn removal and closes the check. */
export function skillCheckAccept(playerId: string): Promise<SkillCheckAccepted> {
  return sendJson<SkillCheckAccepted>(
    'POST',
    `/characters/${encodeURIComponent(playerId)}/skill-check/accept`,
    {},
  );
}

// ── Class abilities (Epic 1) ──

export function fetchAbilities(playerId: string): Promise<AbilitiesSnapshot> {
  return getJson<AbilitiesSnapshot>(`/characters/${encodeURIComponent(playerId)}/abilities`);
}

/** Free-form picker: replaces the choice-group picks (class + level validated server-side). */
export function updateAbilities(playerId: string, abilityIds: string[]): Promise<AbilitiesSnapshot> {
  return sendJson<AbilitiesSnapshot>('PUT', `/characters/${encodeURIComponent(playerId)}/abilities`, {
    abilityIds,
  });
}

// ── Combat actions (all resolve through the server's rule pipelines) ──

type CombatAction = ActionResponse<CombatSnapshot>;

function combatAction(playerId: string, action: string, body: unknown): Promise<CombatAction> {
  return sendJson<CombatAction>(
    'POST',
    `/characters/${encodeURIComponent(playerId)}/actions/${action}`,
    body,
  );
}

export function sendDamage(
  playerId: string,
  value: number,
  damageType: DamageTypeId,
  tags?: string[],
  attackerMight?: number,
): Promise<CombatAction> {
  // attackerMight (N2): feeds the concentration-break WILL save (DC 5 + might);
  // omitted → the server emits a resolve-manually step instead of rolling.
  return combatAction(playerId, 'damage', { value, damageType, tags, attackerMight });
}

export function sendHeal(playerId: string, value: number): Promise<CombatAction> {
  return combatAction(playerId, 'heal', { value });
}

export function weaponAttack(playerId: string, itemId?: string): Promise<CombatAction> {
  return combatAction(playerId, 'weapon-attack', itemId ? { itemId } : {});
}

/** Use a class ability: validate → spend costs → resolve (auto) or print the rule (manual). */
export function useAbility(playerId: string, abilityId: string): Promise<CombatAction> {
  return combatAction(playerId, 'use-ability', { abilityId });
}

/** Validated spend (M0-D): resource is 'ap' | 'mana' | the class resource type. 400 when insufficient. */
export function spendResource(playerId: string, resource: string, amount: number): Promise<CombatAction> {
  return combatAction(playerId, 'spend-resource', { resource, amount });
}

/** Capped gain (M0-D): resource is 'ap' | 'mana' | the class resource type. */
export function gainResource(playerId: string, resource: string, amount: number): Promise<CombatAction> {
  return combatAction(playerId, 'gain-resource', { resource, amount });
}

export function turnStart(playerId: string): Promise<CombatAction> {
  return combatAction(playerId, 'turn-start', {});
}

export function turnEnd(playerId: string): Promise<CombatAction> {
  return combatAction(playerId, 'turn-end', {});
}

export interface ApplyEffectBody {
  effectId: string;
  stacks?: number;
  value?: number;
  duration?: number;
  source?: string;
  duringOwnTurn?: boolean;
}

export function applyEffect(playerId: string, body: ApplyEffectBody): Promise<CombatAction> {
  return combatAction(playerId, 'apply-effect', body);
}

export interface ReviveBody {
  hpRestored?: number;
  deathStackGained?: boolean;
  criticalFail?: boolean;
}

export function revive(playerId: string, body: ReviveBody): Promise<CombatAction> {
  return combatAction(playerId, 'revive', body);
}

export function combatStart(playerId: string): Promise<CombatAction> {
  return combatAction(playerId, 'combat-start', {});
}

/** Single tiered rest (Q20): tier 25 | 50 | 75 | 100. */
export function rest(playerId: string, tier: number): Promise<CombatAction> {
  return combatAction(playerId, 'rest', { tier });
}

export async function removeEffect(playerId: string, effectId: string): Promise<CombatAction> {
  const res = await trackedFetch(
    `${API_BASE}/characters/${encodeURIComponent(playerId)}/actions/remove-effect?effectId=${encodeURIComponent(effectId)}`,
    { method: 'POST' },
  );
  if (!res.ok) throw new Error(await errText(res, 'remove-effect'));
  return (await res.json()) as CombatAction;
}

// ── Spellcasting (M4) ──

export function fetchSpellbook(playerId: string): Promise<SpellbookSnapshot> {
  return getJson<SpellbookSnapshot>(`/characters/${encodeURIComponent(playerId)}/spells`);
}

export interface CastBody {
  spellId: string;
  castAtLevel?: number;
  applyEffectsToSelf?: boolean;
  targetPlayerId?: string;
  componentsAvailable?: string[];
}

export function castSpell(playerId: string, body: CastBody): Promise<CombatAction> {
  return combatAction(playerId, 'cast', body);
}

export function prepareSpells(playerId: string, spellIds: string[]): Promise<CombatAction> {
  return combatAction(playerId, 'prepare-spells', { spellIds });
}

export function fetchInventory(playerId: string): Promise<InventorySnapshot> {
  return getJson<InventorySnapshot>(`/characters/${encodeURIComponent(playerId)}/inventory`);
}

// ── Shop / equipment / progression actions (M5, M6) ──

type InventoryAction = ActionResponse<InventorySnapshot>;

function inventoryAction(playerId: string, action: string, body: unknown): Promise<InventoryAction> {
  return sendJson<InventoryAction>(
    'POST',
    `/characters/${encodeURIComponent(playerId)}/actions/${action}`,
    body,
  );
}

export function purchaseItem(
  playerId: string,
  body: { itemId: string; quantity?: number; tier?: number; silvered?: boolean; spellId?: string },
): Promise<InventoryAction> {
  return inventoryAction(playerId, 'purchase', body);
}

export function sellItem(
  playerId: string,
  body: { itemId: string; quantity?: number; tier?: number; spellId?: string },
): Promise<InventoryAction> {
  return inventoryAction(playerId, 'sell', body);
}

export function upgradeItem(
  playerId: string,
  body: { itemId: string; tier?: number; mode: 'kit' | 'blacksmith' },
): Promise<InventoryAction> {
  return inventoryAction(playerId, 'upgrade', body);
}

export function equipItem(
  playerId: string,
  body: { itemId: string; tier?: number },
): Promise<CombatAction> {
  return combatAction(playerId, 'equip', body);
}

export function unequipItem(
  playerId: string,
  body: { itemId: string; tier?: number },
): Promise<CombatAction> {
  return combatAction(playerId, 'unequip', body);
}

export function useConsumable(
  playerId: string,
  body: { itemId: string; tier?: number },
): Promise<CombatAction> {
  return combatAction(playerId, 'use-consumable', body);
}

/** The cast spell is the one written on the scroll; spellId only disambiguates
 *  when several scrolls of the same kind carry different spells. */
export function castScroll(
  playerId: string,
  body: { itemId: string; tier?: number; spellId?: string; applyEffectsToSelf?: boolean },
): Promise<CombatAction> {
  return combatAction(playerId, 'cast-scroll', body);
}

export interface LevelUpChoices {
  statIncreases?: Partial<Record<AbilityScore, number>>;
  newSpells?: string[];
  talentId?: string;
  featId?: string;
}

export function levelUp(playerId: string, choices: LevelUpChoices): Promise<CombatAction> {
  return combatAction(playerId, 'level-up', { choices });
}

export function updateInventory(
  playerId: string,
  body: { items: InventoryItemInput[]; gold: number },
): Promise<InventorySnapshot> {
  return sendJson<InventorySnapshot>('PUT', `/characters/${encodeURIComponent(playerId)}/inventory`, body);
}

export function fetchBio(playerId: string): Promise<BioSnapshot> {
  return getJson<BioSnapshot>(`/characters/${encodeURIComponent(playerId)}/bio`);
}

export function updateBio(playerId: string, patch: BioPatch): Promise<BioSnapshot> {
  return sendJson<BioSnapshot>('PUT', `/characters/${encodeURIComponent(playerId)}/bio`, patch);
}

export function fetchRoomDeck(room: string): Promise<DeckTemplate> {
  return getJson<DeckTemplate>(`/rooms/${encodeURIComponent(room)}/deck`);
}

// ── Initiative & turn order ──

export function fetchEncounter(room: string): Promise<EncounterView> {
  return getJson<EncounterView>(`/rooms/${encodeURIComponent(room)}/encounter`);
}

/** Rolls d20 + DEX mod + initiative bonus per participant; omit playerIds for the whole room.
 *  surprisedPlayerIds opens the encounter on a surprise round (round 0) that skips them. */
export function startEncounter(
  room: string,
  playerIds?: string[],
  surprisedPlayerIds?: string[],
): Promise<EncounterView> {
  return sendJson<EncounterView>('POST', `/rooms/${encodeURIComponent(room)}/encounter/start`, {
    playerIds: playerIds ?? null,
    surprisedPlayerIds: surprisedPlayerIds ?? null,
  });
}

export function endEncounter(room: string): Promise<EncounterView> {
  return sendJson<EncounterView>('POST', `/rooms/${encodeURIComponent(room)}/encounter/end`, {});
}

/** DM override: skip the current turn (AFK player). */
export function encounterNextTurn(room: string): Promise<EncounterView> {
  return sendJson<EncounterView>('POST', `/rooms/${encodeURIComponent(room)}/encounter/next`, {});
}

/** DM override: change a participant's initiative mid-combat. */
export function setEncounterInitiative(
  room: string,
  playerId: string,
  initiative: number,
): Promise<EncounterView> {
  return sendJson<EncounterView>('PUT', `/rooms/${encodeURIComponent(room)}/encounter/initiative`, {
    playerId,
    initiative,
  });
}

export function updateRoomDeck(room: string, template: DeckTemplate): Promise<DeckTemplate> {
  return sendJson<DeckTemplate>('PUT', `/rooms/${encodeURIComponent(room)}/deck`, template);
}

export function fetchPlayerDeck(playerId: string): Promise<PlayerDeckView> {
  return getJson<PlayerDeckView>(`/characters/${encodeURIComponent(playerId)}/deck`);
}

export function updatePlayerDeck(playerId: string, config: PlayerDeckConfig): Promise<PlayerDeckView> {
  return sendJson<PlayerDeckView>('PUT', `/characters/${encodeURIComponent(playerId)}/deck`, config);
}
