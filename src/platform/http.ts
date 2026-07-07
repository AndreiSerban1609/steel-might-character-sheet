import type {
  AbilityScore,
  ActionResponse,
  BioPatch,
  BioSnapshot,
  CharacterCreatedResponse,
  CombatSnapshot,
  DamageTypeId,
  DeckTemplate,
  InventoryItemInput,
  InventorySnapshot,
  PlayerDeckConfig,
  PlayerDeckView,
  RosterEntry,
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
  const res = await fetch(`${API_BASE}${path}`);
  if (!res.ok) throw new Error(await errText(res, path));
  return (await res.json()) as T;
}

async function sendJson<T>(method: 'POST' | 'PUT', path: string, body: unknown): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
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
  const res = await fetch(
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

export function skillCheck(playerId: string, skillId: string): Promise<SkillCheckResult> {
  return sendJson<SkillCheckResult>('POST', `/characters/${encodeURIComponent(playerId)}/skill-check`, {
    skillId,
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
  const res = await fetch(
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

export function updateRoomDeck(room: string, template: DeckTemplate): Promise<DeckTemplate> {
  return sendJson<DeckTemplate>('PUT', `/rooms/${encodeURIComponent(room)}/deck`, template);
}

export function fetchPlayerDeck(playerId: string): Promise<PlayerDeckView> {
  return getJson<PlayerDeckView>(`/characters/${encodeURIComponent(playerId)}/deck`);
}

export function updatePlayerDeck(playerId: string, config: PlayerDeckConfig): Promise<PlayerDeckView> {
  return sendJson<PlayerDeckView>('PUT', `/characters/${encodeURIComponent(playerId)}/deck`, config);
}
