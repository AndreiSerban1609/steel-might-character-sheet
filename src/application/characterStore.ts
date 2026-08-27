import { create } from 'zustand';
import type {
  AbilitiesSnapshot,
  AbilityScore,
  ActionResponse,
  BioPatch,
  BioSnapshot,
  CombatSnapshot,
  CustomAbilityView,
  CustomItemView,
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
  TableDraw,
  MonsterTemplateRequest,
  MonsterTemplateView,
  MonsterView,
  CombatantView,
} from '../platform/types';
import { isMonsterId } from '../platform/types';
import {
  applyEffect,
  castScroll,
  castSpell,
  clearMonsters,
  combatantApplyEffect,
  combatantDamage,
  combatantHeal,
  combatantRemoveEffect,
  combatantTurnEnd,
  createMonsterTemplate,
  deleteMonster,
  deleteMonsterTemplate,
  fetchMonsterTemplates,
  fetchMonsters,
  importMonsterTemplates,
  spawnMonsters,
  templateFromCharacter,
  updateMonsterTemplate,
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
  fetchCustomItems,
  updateCustomItems,
  updateStatOverrides,
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
import { subscribeConnectivity, verifyServer } from '../platform/http';
import {
  clearSheetMetadata,
  writeViewport,
  type SheetSlice,
  type Viewport,
} from '../platform/metadataGateway';

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
  /** True when the entry gate found no real backend at the API base (fresh browser,
   *  stale tunnel URL) — the entry screen opens the Server connection field on it. */
  serverBad: boolean;
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
  /** GM toggle: the next checks are drawn without mirroring to the table. */
  hiddenCheck: boolean;
  /** Whether the CURRENT in-flight check was started hidden (mid-check toggle
   *  flips must not leak an already-hidden draw). */
  drawWasHidden: boolean;
  /** The table's in-progress draws by player id, adopted from room metadata. */
  tableDraws: Record<string, TableDraw>;
  roomDeck: DeckTemplate | null;
  playerDeck: PlayerDeckView | null;
  inventory: InventorySnapshot | null;
  bio: BioSnapshot | null;
  spellbook: SpellbookSnapshot | null;
  acting: boolean;
  lastResolution: ResolutionResult | null;
  /** Name of the party member the last resolution acted on (null = the viewed character). */
  lastResolutionTarget: string | null;
  encounter: EncounterView | null;
  abilities: AbilitiesSnapshot | null;
  /** Monsters in the room's fight (Epic 2) — every client sees them: targets for all, board for the GM. */
  monsters: MonsterView[];
  /** The GM's room library of monster stat blocks. */
  monsterTemplates: MonsterTemplateView[];

  loadMonsters: () => Promise<void>;
  loadMonsterTemplates: () => Promise<void>;
  saveMonsterTemplate: (body: MonsterTemplateRequest, id?: number) => Promise<void>;
  deleteMonsterTemplate: (id: number) => Promise<void>;
  importMonsterTemplates: (body: MonsterTemplateRequest[]) => Promise<void>;
  /** Story 2.5: mirror a character into a Death-fight template. */
  cloneCharacterAsTemplate: (playerId: string) => Promise<void>;
  spawnMonsters: (templateId: number, count: number) => Promise<void>;
  removeMonster: (id: number) => Promise<void>;
  clearMonsters: () => Promise<void>;
  /** GM: end the current monster's turn (players only ever end their own). */
  endMonsterTurn: (combatantId: string) => Promise<void>;
  doMonsterRemoveEffect: (combatantId: string, effectId: string) => Promise<void>;

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
  saveStatOverrides: (overrides: Record<string, number>) => Promise<void>;
  /** This character's homebrew weapons/armor (demo feedback #19). */
  customItems: CustomItemView[];
  loadCustomItems: () => Promise<void>;
  saveCustomItems: (items: CustomItemView[]) => Promise<void>;
  saveIdentity: (patch: IdentityPatch) => Promise<void>;
  saveProficiencies: (skillIds: string[]) => Promise<void>;
  drawSkill: (skillId: string, advantage?: 'advantage' | 'disadvantage') => Promise<void>;
  redrawSkill: () => Promise<void>;
  clearDraw: () => void;
  setHiddenCheck: (hidden: boolean) => void;
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
  doCastScroll: (body: { itemId: string; tier?: number; spellId?: string; applyEffectsToSelf?: boolean; targetPlayerId?: string; targetCombatantId?: string }) => Promise<void>;
  doLevelUp: (choices: LevelUpChoices) => Promise<void>;
  /** targetId (Story 2.3): the roll meets the target's AC and a hit lands on them. */
  doWeaponAttack: (itemId?: string, targetId?: string) => Promise<void>;
  doDamage: (value: number, damageType: DamageTypeId, tags?: string[], attackerMight?: number, attackerCombatantId?: string) => Promise<void>;
  doHeal: (value: number) => Promise<void>;
  /** Quiet room-roster refresh — feeds the target pickers without touching loading flags. */
  refreshParty: () => Promise<void>;
  doTargetedDamage: (targetId: string, value: number, damageType: DamageTypeId, tags?: string[], attackerMight?: number, attackerCombatantId?: string) => Promise<void>;
  doTargetedHeal: (targetId: string, value: number) => Promise<void>;
  doTargetedApplyEffect: (targetId: string, body: ApplyEffectBody) => Promise<void>;
  doTurnStart: () => Promise<void>;
  doTurnEnd: () => Promise<void>;
  doSpendResource: (resource: string, amount: number) => Promise<void>;
  doGainResource: (resource: string, amount: number) => Promise<void>;
  loadAbilities: () => Promise<void>;
  saveAbilities: (abilityIds: string[]) => Promise<void>;
  saveCustomAbilities: (abilities: CustomAbilityView[]) => Promise<void>;
  /** targetId (Story 2.3): the ability's structured target effect lands on them. */
  doUseAbility: (abilityId: string, targetId?: string) => Promise<void>;
  doUseCustomAbility: (name: string) => Promise<void>;
  adoptEncounter: (view: EncounterView) => void;
  /** Flush all sheet keys from OBR room metadata and re-mirror this client's slice. */
  resetTableMirror: () => Promise<number>;
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
  serverBad: false,
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
  hiddenCheck: false,
  drawWasHidden: false,
  tableDraws: {},
  roomDeck: null,
  playerDeck: null,
  inventory: null,
  bio: null,
  spellbook: null,
  acting: false,
  lastResolution: null,
  lastResolutionTarget: null,
  encounter: null,
  abilities: null,
  monsters: [],
  monsterTemplates: [],

  loadMonsters: async () => {
    const room = get().roomName;
    if (!room.trim()) return;
    try {
      set({ monsters: await fetchMonsters(room) });
    } catch {
      /* the fight list is an enhancement — keep whatever we had */
    }
  },

  loadMonsterTemplates: async () => {
    const room = get().roomName;
    if (!room.trim()) return;
    try {
      set({ monsterTemplates: await fetchMonsterTemplates(room), error: null });
    } catch (e) {
      set({ error: msg(e) });
    }
  },

  saveMonsterTemplate: async (body, id) => {
    const room = get().roomName;
    set({ saving: true, error: null });
    try {
      if (id != null) await updateMonsterTemplate(room, id, body);
      else await createMonsterTemplate(room, body);
      set({ monsterTemplates: await fetchMonsterTemplates(room), saving: false });
    } catch (e) {
      set({ error: msg(e), saving: false });
    }
  },

  deleteMonsterTemplate: async (id) => {
    const room = get().roomName;
    set({ saving: true, error: null });
    try {
      await deleteMonsterTemplate(room, id);
      set({ monsterTemplates: await fetchMonsterTemplates(room), saving: false });
    } catch (e) {
      set({ error: msg(e), saving: false });
    }
  },

  importMonsterTemplates: async (body) => {
    const room = get().roomName;
    set({ saving: true, error: null });
    try {
      await importMonsterTemplates(room, body);
      set({ monsterTemplates: await fetchMonsterTemplates(room), saving: false });
    } catch (e) {
      set({ error: msg(e), saving: false });
    }
  },

  cloneCharacterAsTemplate: async (playerId) => {
    const room = get().roomName;
    set({ saving: true, error: null });
    try {
      await templateFromCharacter(room, playerId);
      set({ monsterTemplates: await fetchMonsterTemplates(room), saving: false });
    } catch (e) {
      set({ error: msg(e), saving: false });
    }
  },

  spawnMonsters: async (templateId, count) => {
    const room = get().roomName;
    set({ acting: true, error: null });
    try {
      await spawnMonsters(room, templateId, count);
      set({ monsters: await fetchMonsters(room), acting: false });
      // Reinforcements join a running order server-side — pull the fresh view.
      if (get().encounter?.active) await get().loadEncounter();
    } catch (e) {
      set({ error: msg(e), acting: false });
    }
  },

  removeMonster: async (id) => {
    const room = get().roomName;
    set({ acting: true, error: null });
    try {
      await deleteMonster(room, id);
      set({ monsters: await fetchMonsters(room), acting: false });
      if (get().encounter?.active) await get().loadEncounter();
    } catch (e) {
      set({ error: msg(e), acting: false });
    }
  },

  clearMonsters: async () => {
    const room = get().roomName;
    set({ acting: true, error: null });
    try {
      await clearMonsters(room);
      set({ monsters: [], acting: false });
      if (get().encounter?.active) await get().loadEncounter();
    } catch (e) {
      set({ error: msg(e), acting: false });
    }
  },

  endMonsterTurn: async (combatantId) => {
    const room = get().roomName;
    set({ acting: true, error: null });
    try {
      const response = await combatantTurnEnd(room, combatantId);
      adoptMonsterResponse(set, get, combatantId, response);
      set({ acting: false });
      await get().loadEncounter();
      // The next combatant's ticks may have changed the viewed sheet or another monster.
      await get().loadMonsters();
      const id = get().selectedPlayerId;
      if (id && get().encounter?.currentPlayerId === id) {
        void fetchCombatSnapshot(id).then((snap) => set({ snapshot: snap }), () => undefined);
      }
    } catch (e) {
      set({ error: msg(e), acting: false });
    }
  },

  doMonsterRemoveEffect: async (combatantId, effectId) => {
    const room = get().roomName;
    set({ acting: true, error: null });
    try {
      adoptMonsterResponse(set, get, combatantId, await combatantRemoveEffect(room, combatantId, effectId));
      set({ acting: false });
    } catch (e) {
      set({ error: msg(e), acting: false });
    }
  },

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
    if (!(await passesServerGate(set))) return;
    try {
      const found = await findCharacter(roomName, email);
      if (found) {
        set({ selectedPlayerId: found.playerId, snapshot: found.snapshot, view: 'sheet', loading: false, inventory: null, bio: null, spellbook: null, abilities: null });
        void get().refreshParty();
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
    // Stay on the entry screen until the roster actually loads — jumping to the
    // roster on a dead server strands the GM with no Server connection field.
    set({ role: 'gm', loading: true, error: null });
    if (!(await passesServerGate(set))) return;
    try {
      set({ roster: await fetchRoster(get().roomName), view: 'roster', loading: false });
    } catch (e) {
      set({ error: msg(e), loading: false });
    }
  },

  createCharacter: async (body) => {
    const { roomName, email } = get();
    set({ saving: true, error: null });
    try {
      const created = await apiCreateCharacter({ ...body, roomName, email });
      set({ selectedPlayerId: created.playerId, snapshot: created.snapshot, view: 'sheet', saving: false, inventory: null, bio: null, spellbook: null, abilities: null });
      void get().refreshParty();
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
    set({ selectedPlayerId: playerId, view: 'sheet', snapshot: null, loading: true, error: null, drawResult: null, playerDeck: null, inventory: null, bio: null, spellbook: null, abilities: null, customItems: [] });
    try {
      set({ snapshot: await fetchCombatSnapshot(playerId), loading: false });
      // Homebrew gear names are needed on the Combat tab too, not just Inventory —
      // without this an equipped custom weapon renders as its raw id.
      void get().loadCustomItems();
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

  customItems: [],

  loadCustomItems: async () => {
    const id = get().selectedPlayerId;
    if (!id) return;
    try {
      set({ customItems: await fetchCustomItems(id) });
    } catch {
      // An older backend has no such endpoint — homebrew gear is simply unavailable,
      // which must not take the inventory tab down with it.
      set({ customItems: [] });
    }
  },

  saveCustomItems: async (items) => {
    const id = get().selectedPlayerId;
    if (!id) return;
    set({ saving: true, error: null });
    try {
      set({ customItems: await updateCustomItems(id, items), saving: false });
      await get().loadInventory();
    } catch (e) {
      set({ error: msg(e), saving: false });
    }
  },

  saveStatOverrides: async (overrides) => {
    const id = get().selectedPlayerId;
    if (!id) return;
    set({ saving: true, error: null });
    try {
      set({ snapshot: await updateStatOverrides(id, overrides), saving: false });
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
    // Hidden is settled when the check STARTS — a mid-check toggle flip must
    // not retroactively broadcast (or hide) the in-flight draw.
    set({ drawing: true, error: null, drawWasHidden: get().hiddenCheck });
    try {
      set({ drawResult: await skillCheck(id, skillId, advantage), drawing: false });
    } catch (e) {
      set({ error: msg(e), drawing: false });
    }
  },

  setHiddenCheck: (hidden) => set({ hiddenCheck: hidden }),

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

  doCast: async (body) => {
    await runCombatAction(set, get, (id) => castSpell(id, body));
    afterTargetedCast(set, get, body.targetCombatantId ?? body.targetPlayerId);
  },

  doPrepareSpells: async (spellIds) => {
    await runCombatAction(set, get, (id) => prepareSpells(id, spellIds));
    // the prepared list lives in the spellbook snapshot, not the combat one
    if (!get().error) await get().loadSpellbook();
  },

  doPurchase: (body) => runInventoryAction(set, get, (id) => purchaseItem(id, body)),
  doSell: (body) => runInventoryAction(set, get, (id) => sellItem(id, body)),
  doUpgrade: (body) => runInventoryAction(set, get, (id) => upgradeItem(id, body)),

  // equip/unequip/use return a combat snapshot (AC/HP/AP change) — the item
  // flags live in the inventory snapshot, so refresh that too.
  // The follow-up loads reset `error`, which would wipe the failure message of
  // the action itself — skip them when it errored (nothing changed server-side).
  doEquip: async (body) => {
    await runCombatAction(set, get, (id) => equipItem(id, body));
    if (!get().error) await get().loadInventory();
  },
  doUnequip: async (body) => {
    await runCombatAction(set, get, (id) => unequipItem(id, body));
    if (!get().error) await get().loadInventory();
  },
  doUseConsumable: async (body) => {
    await runCombatAction(set, get, (id) => useConsumable(id, body));
    if (!get().error) await get().loadInventory();
  },
  doCastScroll: async (body) => {
    await runCombatAction(set, get, (id) => castScroll(id, body));
    if (get().error) return;
    afterTargetedCast(set, get, body.targetCombatantId ?? body.targetPlayerId);
    await get().loadInventory();
  },
  doLevelUp: async (choices) => {
    await runCombatAction(set, get, (id) => levelUp(id, choices));
    // known spells changed → the spellbook (if loaded) is stale
    if (!get().error && get().spellbook) await get().loadSpellbook();
  },

  doWeaponAttack: async (itemId, targetId) => {
    await runCombatAction(set, get, (id) => weaponAttack(id, itemId, targetId));
    // The hit landed on the target server-side — refresh their board row / mirrored sheet.
    afterTargetedCast(set, get, targetId);
  },
  doDamage: (value, damageType, tags, attackerMight, attackerCombatantId) =>
    runCombatAction(set, get, (id) => sendDamage(id, value, damageType, tags, attackerMight, attackerCombatantId)),
  doHeal: (value) => runCombatAction(set, get, (id) => sendHeal(id, value)),

  refreshParty: async () => {
    const room = get().roomName;
    if (!room.trim()) return;
    void get().loadMonsters(); // monsters are targets too (ruling E5)
    try {
      set({ roster: await fetchRoster(room) });
    } catch {
      /* the party list is an enhancement — keep whatever we had */
    }
  },

  doTargetedDamage: (targetId, value, damageType, tags, attackerMight, attackerCombatantId) =>
    runTargetedCombatAction(
      set, get, targetId,
      (id) => sendDamage(id, value, damageType, tags, attackerMight, attackerCombatantId),
      (room, id) => combatantDamage(room, id, value, damageType, tags, attackerMight, attackerCombatantId),
    ),
  doTargetedHeal: (targetId, value) =>
    runTargetedCombatAction(
      set, get, targetId,
      (id) => sendHeal(id, value),
      (room, id) => combatantHeal(room, id, value),
    ),
  doTargetedApplyEffect: (targetId, body) =>
    runTargetedCombatAction(
      set, get, targetId,
      (id) => applyEffect(id, body),
      (room, id) => combatantApplyEffect(room, id, body),
    ),
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

  doUseAbility: async (abilityId, targetId) => {
    await runCombatAction(set, get, (id) => useAbility(id, abilityId, targetId));
    // A use changes the per-rest/per-turn budgets the panel displays.
    if (!get().error) await get().loadAbilities();
    afterTargetedCast(set, get, targetId);
  },

  doUseCustomAbility: (name) => runCombatAction(set, get, (id) => useCustomAbility(id, name)),

  adoptEncounter: (view) => {
    const prev = get().encounter;
    set({ encounter: view });
    // A turn changed hands (or combat opened/closed): monster ticks may have landed —
    // refresh the fight list so board rows and target pickers stay honest.
    if (view.active !== prev?.active || view.currentPlayerId !== prev?.currentPlayerId) {
      void get().loadMonsters();
    }
    // Turns auto-start server-side — when the order just reached the viewed character,
    // their sheet already ticked (DoTs, AP recovery, per-turn budgets): pull it fresh.
    const id = get().selectedPlayerId;
    const becameMyTurn =
      view.active && !!id && view.currentPlayerId === id && prev?.currentPlayerId !== id;
    if (becameMyTurn && !get().acting) {
      void fetchCombatSnapshot(id).then(
        (snap) => {
          // Re-check at RESOLVE time: an action fired meanwhile has a fresher
          // snapshot in its response — a stale fetch must not clobber it.
          const s = get();
          if (!s.acting && s.selectedPlayerId === id) set({ snapshot: snap });
        },
        () => undefined /* best-effort refresh */,
      );
      void get().loadAbilities();
    }
  },

  resetTableMirror: async () => {
    // GM escape hatch: flush every sheet key from room metadata, then immediately
    // re-mirror what this client knows. A live turn order survives the flush;
    // other players' slices rebuild on their next action.
    const state = get();
    const removed = await clearSheetMetadata(state.encounter?.active === true);
    if (state.selectedPlayerId) {
      const slice = sliceFor(state, state.activeViewport);
      if (slice != null) await writeViewport(state.selectedPlayerId, state.activeViewport, slice);
    }
    return removed;
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
  clearResolution: () => set({ lastResolution: null, lastResolutionTarget: null }),
}));

// The platform layer reports connectivity transitions; the flag drives the offline banner.
subscribeConnectivity((offline) => useCharacterStore.setState({ serverOffline: offline }));

/** Shared shape of every combat action: post, then adopt the returned resolution + snapshot. */
/** Which slice of state a viewport mirrors — shared by the OBR broadcast and the reset. */
export function sliceFor(state: CharacterState, viewport: Viewport): unknown {
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
 * Entry gate: nobody proceeds to find/create/roster against a URL that doesn't
 * answer like the game server. Without it, the static-host fallback's 404 on
 * /find reads as "character doesn't exist" and strands new players on the
 * create screen, whose final POST can only ever 405.
 */
async function passesServerGate(
  set: (partial: Partial<CharacterState>) => void,
): Promise<boolean> {
  try {
    await verifyServer();
    set({ serverBad: false });
    return true;
  } catch (e) {
    set({ serverBad: true, error: msg(e), loading: false });
    return false;
  }
}

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
    set({ snapshot: response.snapshot, lastResolution: response.resolution, lastResolutionTarget: null, acting: false });
  } catch (e) {
    set({ error: msg(e), acting: false });
  }
}

/**
 * Post-cast handling for a party-targeted cast (spell or scroll): the response
 * snapshot is the CASTER's (they paid the costs), so fetch the target's fresh
 * state and mirror it so their client picks the applied effects up (no-op
 * outside OBR), and label the resolution with the target's name.
 */
function afterTargetedCast(
  set: (partial: Partial<CharacterState>) => void,
  get: () => CharacterState,
  targetId: string | undefined,
): void {
  if (!targetId || get().error || targetId === get().selectedPlayerId) return;
  if (isMonsterId(targetId)) {
    // The monster took the spell's effects server-side — refresh its board row.
    const name = get().monsters.find((m) => m.combatantId === targetId)?.name;
    set({ lastResolutionTarget: name ?? targetId });
    void get().loadMonsters();
    return;
  }
  const target = get().roster.find((r) => r.playerId === targetId);
  set({ lastResolutionTarget: target?.name ?? targetId });
  void fetchCombatSnapshot(targetId).then(
    (snap) => writeViewport(targetId, 'combat', snap),
    () => undefined /* mirror is best-effort */,
  );
}

/**
 * Run a combat action against a PARTY MEMBER who is not the viewed character:
 * the response snapshot belongs to the target, so the local sheet must not
 * adopt it — show the resolution, and mirror the target's fresh state to their
 * metadata slice so their client picks it up (no-op outside OBR).
 */
async function runTargetedCombatAction(
  set: (partial: Partial<CharacterState>) => void,
  get: () => CharacterState,
  targetId: string,
  action: (playerId: string) => Promise<ActionResponse<CombatSnapshot>>,
  monsterAction?: (room: string, combatantId: string) => Promise<ActionResponse<CombatantView>>,
): Promise<void> {
  if (targetId === get().selectedPlayerId) return runCombatAction(set, get, action);
  set({ acting: true, error: null });
  try {
    if (isMonsterId(targetId)) {
      // Monsters answer on the combatant routes (Story 2.3); their snapshot is a MonsterView
      // that replaces the board row — no OBR sheet slice exists for a monster.
      if (!monsterAction) throw new Error('This action cannot target a monster.');
      adoptMonsterResponse(set, get, targetId, await monsterAction(get().roomName, targetId));
      set({ acting: false });
      return;
    }
    const response = await action(targetId);
    const target = get().roster.find((r) => r.playerId === targetId);
    set({
      lastResolution: response.resolution,
      lastResolutionTarget: target?.name ?? targetId,
      acting: false,
    });
    void writeViewport(targetId, 'combat', response.snapshot);
  } catch (e) {
    set({ error: msg(e), acting: false });
  }
}

/** A combatant-route response for a monster: show the log, refresh that board row. */
function adoptMonsterResponse(
  set: (partial: Partial<CharacterState>) => void,
  get: () => CharacterState,
  combatantId: string,
  response: ActionResponse<CombatantView>,
): void {
  const monster = response.snapshot.monster;
  const monsters = monster
    ? get().monsters.some((m) => m.combatantId === combatantId)
      ? get().monsters.map((m) => (m.combatantId === combatantId ? monster : m))
      : [...get().monsters, monster]
    : get().monsters;
  set({
    lastResolution: response.resolution,
    lastResolutionTarget: response.snapshot.name ?? combatantId,
    monsters,
  });
  // Monster vitals ride inside the encounter view — keep the tracker's chip current.
  if (get().encounter?.active) void get().loadEncounter();
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
