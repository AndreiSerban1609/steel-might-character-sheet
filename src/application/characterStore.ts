import { create } from 'zustand';
import type {
  AbilitiesSnapshot,
  AbilityScore,
  ActionResponse,
  BioPatch,
  BioSnapshot,
  CombatSnapshot,
  CustomAbilityView,
  DamageTypeId,
  DeckTemplate,
  EncounterView,
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
  fetchAbilities,
  updateAbilities,
  updateCustomAbilities,
  useAbility,
  useCustomAbility,
  createCharacter as apiCreateCharacter,
  encounterNextTurn,
  endEncounter as apiEndEncounter,
  equipItem,
  fetchBio,
  fetchCombatSnapshot,
  fetchEncounter,
  fetchInventory,
  fetchPlayerDeck,
  fetchRoomDeck,
  fetchRoster,
  fetchSpellbook,
  findCharacter,
  gainResource,
  levelUp,
  setEncounterInitiative,
  startEncounter as apiStartEncounter,
  prepareSpells,
  purchaseItem,
  removeEffect,
  rest,
  revive,
  sellItem,
  sendDamage,
  sendHeal,
  skillCheck,
  skillCheckAccept,
  skillCheckRedraw,
  spendResource,
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
  weaponAttack,
  type ApplyEffectBody,
  type CastBody,
  type CreateCharacterBody,
  type IdentityPatch,
  type LevelUpChoices,
  type ReviveBody,
  type VitalsPatch,
} from '../platform/http';
import { subscribeConnectivity } from '../platform/http';
import type { SheetSlice, Viewport } from '../platform/metadataGateway';

type View = 'entry' | 'create' | 'roster' | 'sheet' | 'deck';
type Role = 'player' | 'gm';

function msg(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

export interface CharacterState {
  view: View;
  role: Role | null;
  /** True when running inside Owlbear Rodeo — identity comes from the SDK, not the entry form. */
  obrMode: boolean;
  /** Which snapshot slice is mirrored to OBR metadata (follows the active tab). */
  activeViewport: Viewport;
  /** Every sheet slice mirrored to the room, keyed by player id (Story 3.1). */
  partyViewports: Record<string, SheetSlice>;
  /** True after a network-level fetch failure; cleared on the next success (Story 3.3). */
  serverOffline: boolean;
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
  encounter: EncounterView | null;
  abilities: AbilitiesSnapshot | null;

  setRoom: (room: string) => void;
  setEmail: (email: string) => void;
  setActiveViewport: (viewport: Viewport) => void;
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
  drawSkill: (skillId: string, advantage?: 'advantage' | 'disadvantage') => Promise<void>;
  redrawSkill: () => Promise<void>;
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
  doWeaponAttack: (itemId?: string) => Promise<void>;
  doDamage: (value: number, damageType: DamageTypeId, tags?: string[], attackerMight?: number) => Promise<void>;
  doHeal: (value: number) => Promise<void>;
  doTurnStart: () => Promise<void>;
  doTurnEnd: () => Promise<void>;
  doSpendResource: (resource: string, amount: number) => Promise<void>;
  doGainResource: (resource: string, amount: number) => Promise<void>;
  loadAbilities: () => Promise<void>;
  saveAbilities: (abilityIds: string[]) => Promise<void>;
  saveCustomAbilities: (abilities: CustomAbilityView[]) => Promise<void>;
  doUseAbility: (abilityId: string) => Promise<void>;
  doUseCustomAbility: (name: string) => Promise<void>;
  adoptEncounter: (view: EncounterView) => void;
  loadEncounter: () => Promise<void>;
  startEncounter: (surprisedPlayerIds?: string[]) => Promise<void>;
  endEncounter: () => Promise<void>;
  skipTurn: () => Promise<void>;
  overrideInitiative: (playerId: string, initiative: number) => Promise<void>;
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
  obrMode: false,
  activeViewport: 'combat',
  partyViewports: {},
  serverOffline: false,
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
  encounter: null,
  abilities: null,

  setRoom: (room) => set({ roomName: room }),
  setEmail: (email) => set({ email }),
  setActiveViewport: (viewport) => set({ activeViewport: viewport }),

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
        set({ selectedPlayerId: found.playerId, snapshot: found.snapshot, view: 'sheet', loading: false, inventory: null, bio: null, spellbook: null, abilities: null });
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
      set({ selectedPlayerId: created.playerId, snapshot: created.snapshot, view: 'sheet', saving: false, inventory: null, bio: null, spellbook: null, abilities: null });
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
    set({ selectedPlayerId: playerId, view: 'sheet', snapshot: null, loading: true, error: null, drawResult: null, playerDeck: null, inventory: null, bio: null, spellbook: null, abilities: null });
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

  drawSkill: async (skillId, advantage) => {
    const id = get().selectedPlayerId;
    if (!id) return;
    set({ drawing: true, error: null });
    try {
      set({ drawResult: await skillCheck(id, skillId, advantage), drawing: false });
    } catch (e) {
      set({ error: msg(e), drawing: false });
    }
  },

  redrawSkill: async () => {
    const id = get().selectedPlayerId;
    if (!id) return;
    set({ drawing: true, error: null });
    try {
      set({ drawResult: await skillCheckRedraw(id), drawing: false });
    } catch (e) {
      set({ error: msg(e), drawing: false });
    }
  },

  // Dismissing the banner ACCEPTS the result (DoF semantics): the server applies the
  // final card's consume/burn removal and closes the check.
  clearDraw: () => {
    const { selectedPlayerId, drawResult } = get();
    set({ drawResult: null });
    if (!selectedPlayerId || !drawResult) return;
    void skillCheckAccept(selectedPlayerId)
      .then((accepted) => {
        // a consumed/burned card changes the deck — refresh the Deck tab if loaded
        if (accepted.cardRemoved && get().playerDeck) void get().loadPlayerDeck();
      })
      .catch(() => {
        /* accept is best-effort; a lost session just means nothing to remove */
      });
  },

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

  doWeaponAttack: (itemId) => runCombatAction(set, get, (id) => weaponAttack(id, itemId)),
  doDamage: (value, damageType, tags, attackerMight) =>
    runCombatAction(set, get, (id) => sendDamage(id, value, damageType, tags, attackerMight)),
  doHeal: (value) => runCombatAction(set, get, (id) => sendHeal(id, value)),
  // turn actions move the room's turn order — refresh it alongside the snapshot
  doTurnStart: async () => {
    await runCombatAction(set, get, (id) => turnStart(id));
    if (get().encounter?.active) await get().loadEncounter();
  },
  doTurnEnd: async () => {
    await runCombatAction(set, get, (id) => turnEnd(id));
    if (get().encounter?.active) await get().loadEncounter();
  },
  doSpendResource: (resource, amount) =>
    runCombatAction(set, get, (id) => spendResource(id, resource, amount)),
  doGainResource: (resource, amount) =>
    runCombatAction(set, get, (id) => gainResource(id, resource, amount)),

  loadAbilities: async () => {
    const id = get().selectedPlayerId;
    if (!id) return;
    set({ error: null });
    try {
      set({ abilities: await fetchAbilities(id) });
    } catch (e) {
      set({ error: msg(e) });
    }
  },

  saveAbilities: async (abilityIds) => {
    const id = get().selectedPlayerId;
    if (!id) return;
    set({ saving: true, error: null });
    try {
      set({ abilities: await updateAbilities(id, abilityIds), saving: false });
    } catch (e) {
      set({ error: msg(e), saving: false });
    }
  },

  saveCustomAbilities: async (abilities) => {
    const id = get().selectedPlayerId;
    if (!id) return;
    set({ saving: true, error: null });
    try {
      set({ abilities: await updateCustomAbilities(id, abilities), saving: false });
    } catch (e) {
      set({ error: msg(e), saving: false });
    }
  },

  doUseAbility: async (abilityId) => {
    await runCombatAction(set, get, (id) => useAbility(id, abilityId));
    // A use changes the per-rest/per-turn budgets the panel displays.
    if (!get().error) await get().loadAbilities();
  },

  doUseCustomAbility: (name) => runCombatAction(set, get, (id) => useCustomAbility(id, name)),

  adoptEncounter: (view) => {
    const prev = get().encounter;
    set({ encounter: view });
    // Turns auto-start server-side — when the order just reached the viewed character,
    // their sheet already ticked (DoTs, AP recovery, per-turn budgets): pull it fresh.
    const id = get().selectedPlayerId;
    const becameMyTurn =
      view.active && !!id && view.currentPlayerId === id && prev?.currentPlayerId !== id;
    if (becameMyTurn && !get().acting) {
      void fetchCombatSnapshot(id).then(
        (snap) => set({ snapshot: snap }),
        () => undefined /* best-effort refresh */,
      );
      void get().loadAbilities();
    }
  },

  loadEncounter: async () => {
    const room = get().roomName;
    if (!room.trim()) return;
    try {
      get().adoptEncounter(await fetchEncounter(room));
    } catch {
      /* polling is best-effort — keep the last known state */
    }
  },

  startEncounter: async (surprisedPlayerIds) => {
    const room = get().roomName;
    if (!room.trim()) return;
    set({ acting: true, error: null });
    try {
      const view = await apiStartEncounter(room, undefined, surprisedPlayerIds);
      set({ acting: false });
      get().adoptEncounter(view);
    } catch (e) {
      set({ error: msg(e), acting: false });
    }
  },

  endEncounter: async () => {
    const room = get().roomName;
    if (!room.trim()) return;
    set({ acting: true, error: null });
    try {
      set({ encounter: await apiEndEncounter(room), acting: false });
    } catch (e) {
      set({ error: msg(e), acting: false });
    }
  },

  skipTurn: async () => {
    const room = get().roomName;
    if (!room.trim()) return;
    set({ acting: true, error: null });
    try {
      const view = await encounterNextTurn(room);
      set({ acting: false });
      get().adoptEncounter(view);
    } catch (e) {
      set({ error: msg(e), acting: false });
    }
  },

  overrideInitiative: async (playerId, initiative) => {
    const room = get().roomName;
    if (!room.trim()) return;
    set({ error: null });
    try {
      set({ encounter: await setEncounterInitiative(room, playerId, initiative) });
    } catch (e) {
      set({ error: msg(e) });
    }
  },
  doApplyEffect: (body) => runCombatAction(set, get, (id) => applyEffect(id, body)),
  doRemoveEffect: (effectId) => runCombatAction(set, get, (id) => removeEffect(id, effectId)),
  doRevive: (body) => runCombatAction(set, get, (id) => revive(id, body)),
  doCombatStart: () => runCombatAction(set, get, (id) => combatStart(id)),
  doRest: (tier) => runCombatAction(set, get, (id) => rest(id, tier)),
  clearResolution: () => set({ lastResolution: null }),
}));

// The platform layer reports connectivity transitions; the flag drives the offline banner.
subscribeConnectivity((offline) => useCharacterStore.setState({ serverOffline: offline }));

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
