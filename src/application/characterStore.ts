import { create } from 'zustand';
import type {
  AbilityScore,
  ActionResponse,
  BioPatch,
  BioSnapshot,
  CombatSnapshot,
  DamageTypeId,
  DeckTemplate,
  InventoryItemInput,
  InventorySnapshot,
  PlayerDeckConfig,
  PlayerDeckView,
  ResolutionResult,
  RosterEntry,
  SkillCheckResult,
  SpellbookSnapshot,
} from '../platform/types';
import {
  applyEffect,
  castScroll,
  castSpell,
  combatStart,
  createCharacter as apiCreateCharacter,
  equipItem,
  fetchBio,
  fetchCombatSnapshot,
  fetchInventory,
  fetchPlayerDeck,
  fetchRoomDeck,
  fetchRoster,
  fetchSpellbook,
  findCharacter,
  levelUp,
  prepareSpells,
  purchaseItem,
  removeEffect,
  rest,
  revive,
  sellItem,
  sendDamage,
  sendHeal,
  skillCheck,
  turnEnd,
  turnStart,
  unequipItem,
  updateBio,
  updateIdentity,
  updateInventory,
  updatePlayerDeck,
  updateProficiencies,
  updateRoomDeck,
  updateStats,
  updateVitals,
  upgradeItem,
  useConsumable,
  type ApplyEffectBody,
  type CastBody,
  type CreateCharacterBody,
  type IdentityPatch,
  type LevelUpChoices,
  type ReviveBody,
  type VitalsPatch,
} from '../platform/http';

type View = 'entry' | 'create' | 'roster' | 'sheet' | 'deck';
type Role = 'player' | 'gm';

function msg(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

interface CharacterState {
  view: View;
  role: Role | null;
  roomName: string;
  email: string;
  roster: RosterEntry[];
  selectedPlayerId: string | null;
  snapshot: CombatSnapshot | null;
  loading: boolean;
  saving: boolean;
  error: string | null;
  drawing: boolean;
  drawResult: SkillCheckResult | null;
  roomDeck: DeckTemplate | null;
  playerDeck: PlayerDeckView | null;
  inventory: InventorySnapshot | null;
  bio: BioSnapshot | null;
  spellbook: SpellbookSnapshot | null;
  acting: boolean;
  lastResolution: ResolutionResult | null;

  setRoom: (room: string) => void;
  setEmail: (email: string) => void;
  enterAsPlayer: () => Promise<void>;
  enterAsGm: () => Promise<void>;
  createCharacter: (body: Omit<CreateCharacterBody, 'roomName' | 'email'>) => Promise<void>;
  loadRoster: () => Promise<void>;
  selectPlayer: (playerId: string) => Promise<void>;
  back: () => void;
  saveStats: (stats: Record<AbilityScore, number>) => Promise<void>;
  saveVitals: (patch: VitalsPatch) => Promise<void>;
  saveIdentity: (patch: IdentityPatch) => Promise<void>;
  saveProficiencies: (skillIds: string[]) => Promise<void>;
  drawSkill: (skillId: string) => Promise<void>;
  clearDraw: () => void;
  openDeckEditor: () => Promise<void>;
  saveRoomDeck: (template: DeckTemplate) => Promise<void>;
  loadPlayerDeck: () => Promise<void>;
  savePlayerDeck: (config: PlayerDeckConfig) => Promise<void>;
  loadInventory: () => Promise<void>;
  saveInventory: (body: { items: InventoryItemInput[]; gold: number }) => Promise<void>;
  loadBio: () => Promise<void>;
  saveBio: (patch: BioPatch) => Promise<void>;
  loadSpellbook: () => Promise<void>;
  doCast: (body: CastBody) => Promise<void>;
  doPrepareSpells: (spellIds: string[]) => Promise<void>;
  doPurchase: (body: { itemId: string; quantity?: number; tier?: number; silvered?: boolean; spellId?: string }) => Promise<void>;
  doSell: (body: { itemId: string; quantity?: number; tier?: number; spellId?: string }) => Promise<void>;
  doUpgrade: (body: { itemId: string; tier?: number; mode: 'kit' | 'blacksmith' }) => Promise<void>;
  doEquip: (body: { itemId: string; tier?: number }) => Promise<void>;
  doUnequip: (body: { itemId: string; tier?: number }) => Promise<void>;
  doUseConsumable: (body: { itemId: string; tier?: number }) => Promise<void>;
  doCastScroll: (body: { itemId: string; tier?: number; spellId?: string; applyEffectsToSelf?: boolean }) => Promise<void>;
  doLevelUp: (choices: LevelUpChoices) => Promise<void>;
  doDamage: (value: number, damageType: DamageTypeId, tags?: string[], attackerMight?: number) => Promise<void>;
  doHeal: (value: number) => Promise<void>;
  doTurnStart: () => Promise<void>;
  doTurnEnd: () => Promise<void>;
  doApplyEffect: (body: ApplyEffectBody) => Promise<void>;
  doRemoveEffect: (effectId: string) => Promise<void>;
  doRevive: (body: ReviveBody) => Promise<void>;
  doCombatStart: () => Promise<void>;
  doRest: (tier: number) => Promise<void>;
  clearResolution: () => void;
}

export const useCharacterStore = create<CharacterState>((set, get) => ({
  view: 'entry',
  role: null,
  roomName: '',
  email: '',
  roster: [],
  selectedPlayerId: null,
  snapshot: null,
  loading: false,
  saving: false,
  error: null,
  drawing: false,
  drawResult: null,
  roomDeck: null,
  playerDeck: null,
  inventory: null,
  bio: null,
  spellbook: null,
  acting: false,
  lastResolution: null,

  setRoom: (room) => set({ roomName: room }),
  setEmail: (email) => set({ email }),

  enterAsPlayer: async () => {
    const { roomName, email } = get();
    if (!roomName.trim() || !email.trim()) {
      set({ error: 'Room and email are required.' });
      return;
    }
    set({ loading: true, error: null, role: 'player' });
    try {
      const found = await findCharacter(roomName, email);
      if (found) {
        set({ selectedPlayerId: found.playerId, snapshot: found.snapshot, view: 'sheet', loading: false, inventory: null, bio: null, spellbook: null });
      } else {
        set({ view: 'create', loading: false });
      }
    } catch (e) {
      set({ error: msg(e), loading: false });
    }
  },

  enterAsGm: async () => {
    if (!get().roomName.trim()) {
      set({ error: 'Room is required.' });
      return;
    }
    set({ role: 'gm', view: 'roster' });
    await get().loadRoster();
  },

  createCharacter: async (body) => {
    const { roomName, email } = get();
    set({ saving: true, error: null });
    try {
      const created = await apiCreateCharacter({ ...body, roomName, email });
      set({ selectedPlayerId: created.playerId, snapshot: created.snapshot, view: 'sheet', saving: false, inventory: null, bio: null, spellbook: null });
    } catch (e) {
      set({ error: msg(e), saving: false });
    }
  },

  loadRoster: async () => {
    set({ loading: true, error: null });
    try {
      set({ roster: await fetchRoster(get().roomName), loading: false });
    } catch (e) {
      set({ error: msg(e), loading: false });
    }
  },

  selectPlayer: async (playerId) => {
    set({ selectedPlayerId: playerId, view: 'sheet', snapshot: null, loading: true, error: null, drawResult: null, playerDeck: null, inventory: null, bio: null, spellbook: null });
    try {
      set({ snapshot: await fetchCombatSnapshot(playerId), loading: false });
    } catch (e) {
      set({ error: msg(e), loading: false });
    }
  },

  back: () => {
    const { view, role } = get();
    if (view === 'deck' || (view === 'sheet' && role === 'gm')) {
      set({ view: 'roster', error: null });
      void get().loadRoster();
    } else {
      set({ view: 'entry', error: null });
    }
  },

  saveStats: async (stats) => {
    const id = get().selectedPlayerId;
    if (!id) return;
    set({ saving: true, error: null });
    try {
      set({ snapshot: await updateStats(id, stats), saving: false });
    } catch (e) {
      set({ error: msg(e), saving: false });
    }
  },

  saveVitals: async (patch) => {
    const id = get().selectedPlayerId;
    if (!id) return;
    set({ saving: true, error: null });
    try {
      set({ snapshot: await updateVitals(id, patch), saving: false });
    } catch (e) {
      set({ error: msg(e), saving: false });
    }
  },

  saveIdentity: async (patch) => {
    const id = get().selectedPlayerId;
    if (!id) return;
    set({ saving: true, error: null });
    try {
      set({ snapshot: await updateIdentity(id, patch), saving: false });
    } catch (e) {
      set({ error: msg(e), saving: false });
    }
  },

  saveProficiencies: async (skillIds) => {
    const id = get().selectedPlayerId;
    if (!id) return;
    set({ saving: true, error: null });
    try {
      set({ snapshot: await updateProficiencies(id, skillIds), saving: false });
    } catch (e) {
      set({ error: msg(e), saving: false });
    }
  },

  drawSkill: async (skillId) => {
    const id = get().selectedPlayerId;
    if (!id) return;
    set({ drawing: true, error: null });
    try {
      set({ drawResult: await skillCheck(id, skillId), drawing: false });
    } catch (e) {
      set({ error: msg(e), drawing: false });
    }
  },

  clearDraw: () => set({ drawResult: null }),

  openDeckEditor: async () => {
    set({ view: 'deck', error: null, roomDeck: null });
    try {
      set({ roomDeck: await fetchRoomDeck(get().roomName) });
    } catch (e) {
      set({ error: msg(e) });
    }
  },

  saveRoomDeck: async (template) => {
    set({ saving: true, error: null });
    try {
      set({ roomDeck: await updateRoomDeck(get().roomName, template), saving: false });
    } catch (e) {
      set({ error: msg(e), saving: false });
    }
  },

  loadPlayerDeck: async () => {
    const id = get().selectedPlayerId;
    if (!id) return;
    set({ error: null });
    try {
      set({ playerDeck: await fetchPlayerDeck(id) });
    } catch (e) {
      set({ error: msg(e) });
    }
  },

  savePlayerDeck: async (config) => {
    const id = get().selectedPlayerId;
    if (!id) return;
    set({ saving: true, error: null });
    try {
      set({ playerDeck: await updatePlayerDeck(id, config), saving: false });
    } catch (e) {
      set({ error: msg(e), saving: false });
    }
  },

  loadInventory: async () => {
    const id = get().selectedPlayerId;
    if (!id) return;
    set({ error: null });
    try {
      set({ inventory: await fetchInventory(id) });
    } catch (e) {
      set({ error: msg(e) });
    }
  },

  saveInventory: async (body) => {
    const id = get().selectedPlayerId;
    if (!id) return;
    set({ saving: true, error: null });
    try {
      set({ inventory: await updateInventory(id, body), saving: false });
    } catch (e) {
      set({ error: msg(e), saving: false });
    }
  },

  loadBio: async () => {
    const id = get().selectedPlayerId;
    if (!id) return;
    set({ error: null });
    try {
      set({ bio: await fetchBio(id) });
    } catch (e) {
      set({ error: msg(e) });
    }
  },

  saveBio: async (patch) => {
    const id = get().selectedPlayerId;
    if (!id) return;
    set({ saving: true, error: null });
    try {
      set({ bio: await updateBio(id, patch), saving: false });
    } catch (e) {
      set({ error: msg(e), saving: false });
    }
  },

  loadSpellbook: async () => {
    const id = get().selectedPlayerId;
    if (!id) return;
    set({ error: null });
    try {
      set({ spellbook: await fetchSpellbook(id) });
    } catch (e) {
      set({ error: msg(e) });
    }
  },

  doCast: (body) => runCombatAction(set, get, (id) => castSpell(id, body)),

  doPrepareSpells: async (spellIds) => {
    await runCombatAction(set, get, (id) => prepareSpells(id, spellIds));
    // the prepared list lives in the spellbook snapshot, not the combat one
    await get().loadSpellbook();
  },

  doPurchase: (body) => runInventoryAction(set, get, (id) => purchaseItem(id, body)),
  doSell: (body) => runInventoryAction(set, get, (id) => sellItem(id, body)),
  doUpgrade: (body) => runInventoryAction(set, get, (id) => upgradeItem(id, body)),

  // equip/unequip/use return a combat snapshot (AC/HP/AP change) — the item
  // flags live in the inventory snapshot, so refresh that too.
  doEquip: async (body) => {
    await runCombatAction(set, get, (id) => equipItem(id, body));
    await get().loadInventory();
  },
  doUnequip: async (body) => {
    await runCombatAction(set, get, (id) => unequipItem(id, body));
    await get().loadInventory();
  },
  doUseConsumable: async (body) => {
    await runCombatAction(set, get, (id) => useConsumable(id, body));
    await get().loadInventory();
  },
  doCastScroll: async (body) => {
    await runCombatAction(set, get, (id) => castScroll(id, body));
    await get().loadInventory();
  },
  doLevelUp: async (choices) => {
    await runCombatAction(set, get, (id) => levelUp(id, choices));
    // known spells changed → the spellbook (if loaded) is stale
    if (get().spellbook) await get().loadSpellbook();
  },

  doDamage: (value, damageType, tags, attackerMight) =>
    runCombatAction(set, get, (id) => sendDamage(id, value, damageType, tags, attackerMight)),
  doHeal: (value) => runCombatAction(set, get, (id) => sendHeal(id, value)),
  doTurnStart: () => runCombatAction(set, get, (id) => turnStart(id)),
  doTurnEnd: () => runCombatAction(set, get, (id) => turnEnd(id)),
  doApplyEffect: (body) => runCombatAction(set, get, (id) => applyEffect(id, body)),
  doRemoveEffect: (effectId) => runCombatAction(set, get, (id) => removeEffect(id, effectId)),
  doRevive: (body) => runCombatAction(set, get, (id) => revive(id, body)),
  doCombatStart: () => runCombatAction(set, get, (id) => combatStart(id)),
  doRest: (tier) => runCombatAction(set, get, (id) => rest(id, tier)),
  clearResolution: () => set({ lastResolution: null }),
}));

/** Shared shape of every combat action: post, then adopt the returned resolution + snapshot. */
async function runCombatAction(
  set: (partial: Partial<CharacterState>) => void,
  get: () => CharacterState,
  action: (playerId: string) => Promise<ActionResponse<CombatSnapshot>>,
): Promise<void> {
  const id = get().selectedPlayerId;
  if (!id) return;
  set({ acting: true, error: null });
  try {
    const response = await action(id);
    set({ snapshot: response.snapshot, lastResolution: response.resolution, acting: false });
  } catch (e) {
    set({ error: msg(e), acting: false });
  }
}

/** Shop actions return the inventory snapshot instead of the combat one. */
async function runInventoryAction(
  set: (partial: Partial<CharacterState>) => void,
  get: () => CharacterState,
  action: (playerId: string) => Promise<ActionResponse<InventorySnapshot>>,
): Promise<void> {
  const id = get().selectedPlayerId;
  if (!id) return;
  set({ acting: true, error: null });
  try {
    const response = await action(id);
    set({ inventory: response.snapshot, lastResolution: response.resolution, acting: false });
  } catch (e) {
    set({ error: msg(e), acting: false });
  }
}
