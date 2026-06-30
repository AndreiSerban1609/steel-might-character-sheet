import { create } from 'zustand';
import type {
  AbilityScore,
  BioPatch,
  BioSnapshot,
  CombatSnapshot,
  DeckTemplate,
  InventoryItemInput,
  InventorySnapshot,
  PlayerDeckConfig,
  PlayerDeckView,
  RosterEntry,
  SkillCheckResult,
} from '../platform/types';
import {
  createCharacter as apiCreateCharacter,
  fetchBio,
  fetchCombatSnapshot,
  fetchInventory,
  fetchPlayerDeck,
  fetchRoomDeck,
  fetchRoster,
  findCharacter,
  skillCheck,
  updateBio,
  updateIdentity,
  updateInventory,
  updatePlayerDeck,
  updateProficiencies,
  updateRoomDeck,
  updateStats,
  updateVitals,
  type CreateCharacterBody,
  type IdentityPatch,
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
        set({ selectedPlayerId: found.playerId, snapshot: found.snapshot, view: 'sheet', loading: false, inventory: null, bio: null });
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
      set({ selectedPlayerId: created.playerId, snapshot: created.snapshot, view: 'sheet', saving: false, inventory: null, bio: null });
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
    set({ selectedPlayerId: playerId, view: 'sheet', snapshot: null, loading: true, error: null, drawResult: null, playerDeck: null, inventory: null, bio: null });
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
}));
