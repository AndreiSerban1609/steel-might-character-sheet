// Mirrors the server's CombatSnapshot DTO (com.steelmight.charactersheet.dto.CombatSnapshot).
// Jackson serializes the AbilityScore enum (and enum-keyed maps) as uppercase names.

export type AbilityScore = 'STR' | 'DEX' | 'CON' | 'INT' | 'WIS' | 'WILL' | 'CHA';

export interface HpView {
  current: number;
  max: number;
  temp: number;
}

export interface ApView {
  current: number;
  recovery: number;
  max: number;
}

/** max === null → unbounded (builder resources like focus). */
export interface ResourceView {
  type: string;
  current: number;
  max: number | null;
}

/** Sub-resource pool row; current can be negative (fury disaster rule). */
export interface PoolView {
  id: string;
  name: string;
  current: number;
  max: number | null;
}

export interface ManaView {
  current: number;
  max: number;
}

export interface EffectView {
  id: string;
  name: string;
  stacks: number;
  value: number | null;
  rounds: number | null;
  /** false → accumulating dormant stacks; the effect is doing nothing yet. */
  active: boolean;
  /** Stacks needed to fire; null for effects the threshold system doesn't gate. */
  threshold: number | null;
}

/** Compact GM-roster row (server: dto.RosterEntry). */
export interface RosterEntry {
  playerId: string;
  roomName: string;
  email: string;
  name: string;
  level: number;
  pathId: string;
  classId: string;
  currentHp: number;
  maxHp: number;
  ac: number;
}

export interface CharacterCreatedResponse {
  playerId: string;
  snapshot: CombatSnapshot;
}

export type CardType = 'STEEL_CRITICAL' | 'MIGHT_CRITICAL' | 'NEUTRAL' | 'ENCOUNTER' | 'STAT' | 'CLASS';

export interface Card {
  type: CardType;
  name: string;
  modifier: number | null;
  description: string;
  /** CLASS cards: skill restriction — auto-passes on any other check. */
  checkType?: string | null;
  /** CLASS cards: passed on draw, this bonus accumulates for the whole check. */
  redrawModifier?: number | null;
  /** CLASS cards: position in the player's extraCards (drives consume/burn on accept). */
  classCardIndex?: number | null;
  /** CLASS cards: "consume" | "burn" — applied when this card is accepted as final. */
  removal?: 'consume' | 'burn' | null;
}

/** A card auto-passed during a draw. */
export interface PassedCard {
  card: Card;
  reason: 'wrong-check' | 'redraw-bonus';
}

export interface RedrawBonus {
  name: string;
  modifier: number;
}

export interface SkillCheckResult {
  skillId: string;
  ability: string;
  card: Card;
  d10: number;
  /** Both dice when rolled with advantage/disadvantage ([d10] on a normal draw). */
  d10Rolls: number[];
  /** Chosen before the draw; d10 = the higher (advantage) / lower (disadvantage) roll. */
  advantage: 'advantage' | 'disadvantage' | null;
  effectiveModifier: number | null;
  total: number | null;
  critical: boolean;
  proficient: boolean;
  /** DoF gamble: proficiency-bonus-many redraws per check; the d10 stays fixed. */
  redrawsUsed: number;
  redrawsRemaining: number;
  /** Cards auto-passed on THIS draw/redraw (wrong-check skips, redraw-bonus cards). */
  passedCards: PassedCard[];
  /** Bonuses accumulated across the whole check; their sum is included in total. */
  redrawBonuses: RedrawBonus[];
  bonusTotal: number;
}

/** Outcome of accepting a check: whether the final card was consumed/burned. */
export interface SkillCheckAccepted {
  cardRemoved: boolean;
  removal: 'consume' | 'burn' | null;
}

/**
 * A skill-check draw mirrored to the whole table (room-level metadata key).
 * The drawing client shows its full banner; everyone else renders this as a toast.
 */
export interface TableDraw {
  playerId: string;
  playerName: string;
  /** Path id of the drawer — themes CLASS card art on other clients. */
  pathId: string | null;
  result: SkillCheckResult;
  /** Write timestamp — keys the toast's local dismiss so a new draw re-shows it. */
  at: number;
}

/** Per-ability budget status — the null side means "no limit of that kind". */
export interface AbilityUseView {
  abilityId: string;
  perRestRemaining: number | null;
  perRestMax: number | null;
  perTurnRemaining: number | null;
  perTurnMax: number | null;
}

/** A player-written free-text ability, pending official rulings (2026-07-20). */
export interface CustomAbilityView {
  name: string;
  text: string;
  /** Optional flat AP cost — Use spends it through the validated path; null = table call. */
  apCost?: number | null;
}

/** Known class abilities: group-null entries are class-granted; picked = the editable choices. */
export interface AbilitiesSnapshot {
  classId: string;
  known: string[];
  picked: string[];
  /** Only abilities that declare per-rest/per-turn limits appear here (server-computed). */
  uses: AbilityUseView[];
  /** Free-text abilities the player wrote — the table adjudicates costs and outcomes. */
  custom: CustomAbilityView[];
}

export interface DeckCard {
  name: string;
  modifier: number;
  description: string;
  /** CLASS-card extras only: restrict to one skill check (auto-passes elsewhere). */
  checkType?: string | null;
  /** CLASS-card extras only: pass + accumulate this bonus instead of resolving. */
  redrawModifier?: number | null;
  /** CLASS-card extras only: "consume" (out until rest) | "burn" (gone forever). */
  removal?: 'consume' | 'burn' | null;
  /** True while a consume card is spent (restored by any rest). */
  consumed?: boolean | null;
}

export interface DeckTemplate {
  neutralCards: DeckCard[];
  statCount: number;
  encounterCards: DeckCard[];
}

export interface PlayerDeckConfig {
  statAdjust: number;
  extraCards: DeckCard[];
  /** Room Encounter cards this player opted out of, by card name (lowercased server-side). */
  disabledEncounters: string[];
}

export interface PlayerDeckView {
  room: DeckTemplate;
  config: PlayerDeckConfig;
  deckSize: number;
  /** The full effective deck, build order (draws shuffle; consumed/burned excluded). */
  cards: Card[];
}

export interface EncounterEntryView {
  playerId: string;
  name: string;
  initiative: number;
  status: 'ALIVE' | 'DOWNED' | 'DEAD' | null;
  /** Ambushed: auto-skipped during the surprise round (round 0). */
  surprised: boolean;
}

/**
 * A player/GM-defined weapon or armor (server: dto.CustomItemView). `id` is server-assigned
 * and stable across edits; send null to create. Weapon fields are ignored for ARMOR and
 * vice versa, so one flat shape covers both.
 */
export interface CustomItemView {
  id: string | null;
  name: string;
  kind: 'WEAPON' | 'ARMOR';
  inventorySpace: number | null;
  properties: string | null;
  proficient: boolean | null;
  damageDice: string | null;
  damageFlat: number | null;
  damageType: string | null;
  damageScaling: number | null;
  weaponStat: string | null;
  apCost: number | null;
  armorType: string | null;
  acBase: number | null;
  acDexMod: boolean | null;
  pa: number | null;
  ma: number | null;
  paScaling: number | null;
  maScaling: number | null;
}

/** One line of the room's activity log (newest first): who did what, when. */
export interface AuditView {
  time: string;
  playerId: string;
  characterName: string;
  action: string;
  summary: string;
}

/** A room's turn order. active=false → no encounter running. round 0 = surprise round. */
export interface EncounterView {
  active: boolean;
  round: number;
  currentPlayerId: string | null;
  turnStarted: boolean;
  entries: EncounterEntryView[];
}

/** One pipeline rule's contribution to an action's resolution (server: engine.ResolutionStep). */
export interface ResolutionStep {
  rule: string;
  note: string;
  valueBefore: number;
  valueAfter: number;
}

/** Dice breakdown inside a cast payload (M4-B/D). */
export interface RollBreakdown {
  rolls: number[];
  flat: number;
  modifier: number;
  weaponDamage?: number;
  /** present (= 2) when a critical hit doubled the damage */
  critMultiplier?: number;
  total: number;
}

/** A d20 attack roll (spell casts and weapon attacks). */
export interface AttackRollView {
  roll?: number;
  /** both dice when rolled with advantage/disadvantage */
  rolls?: number[];
  advantage?: boolean;
  disadvantage?: boolean;
  /** stacked disadvantage (Guide 4.3) — no roll happens */
  autoMiss?: boolean;
  bonus?: number;
  total?: number;
  critical?: boolean;
  criticalFailure?: boolean;
}

/** An effect the spell applies on hit, with its converted duration. */
export interface EffectOnHitView {
  id: string;
  name: string;
  rounds?: number;
  durationType?: string;
}

/** Action-specific extras (cast: saveDC/attackRoll/damage/…; level-up: newAbilities). */
export interface ResolutionPayload {
  saveDC?: number;
  attackBonus?: number;
  attackRoll?: AttackRollView;
  damageType?: string;
  damage?: RollBreakdown;
  healing?: RollBreakdown;
  concentrationDropped?: boolean;
  effectsOnHit?: EffectOnHitView[];
  effectsAppliedTo?: string;
  newLevel?: number;
  newAbilities?: string[];
  /** weapon attacks */
  weapon?: { id: string; name: string };
  silvered?: boolean;
  properties?: string[];
}

export interface ResolutionResult {
  steps: ResolutionStep[];
  effectsTriggered: string[];
  payload?: ResolutionPayload;
}

/** Every combat action returns the step-by-step resolution plus the updated snapshot. */
export interface ActionResponse<T> {
  resolution: ResolutionResult;
  snapshot: T;
}

export type DamageTypeId =
  | 'SLASHING'
  | 'PIERCING'
  | 'CRUSHING'
  | 'SHADOW'
  | 'LIGHT'
  | 'FIRE'
  | 'ICE'
  | 'LIGHTNING'
  | 'POISON'
  | 'THUNDER'
  | 'PSYCHIC'
  | 'SPECTRAL'
  | 'PURE'
  | 'FORCE'
  | 'TRUE';

/** Mirrors the server's SpellbookSnapshot DTO. */
export interface SpellbookSnapshot {
  knownSpells: string[];
  preparedSpells: string[];
  currentMana: number;
  maxMana: number;
  concentrating: boolean;
  spellcastingAttribute: AbilityScore | null;
  spellSaveDC: number;
  spellAttackBonus: number;
}

export interface InventoryItemView {
  itemId: string;
  quantity: number;
  upgradeTier: number;
  equipped: boolean;
  silvered: boolean;
  space: number;
  /** remaining uses for charge items; null/absent = not charge-based */
  chargesRemaining?: number | null;
  /** scrolls: the spell written on this scroll ("Scroll of Magic Bolt") */
  spellId?: string | null;
}

export interface InventorySnapshot {
  items: InventoryItemView[];
  /** ONE generic gold currency — all shop prices are in it (no denominations) */
  gold: number;
  carriedSpace: number;
  carryCapacity: number;
}

/** A single item line submitted on a full-replace inventory save. */
export interface InventoryItemInput {
  itemId: string;
  quantity: number;
  upgradeTier: number;
  equipped: boolean;
  /** scrolls: the spell written on the scroll (DM grant path) */
  spellId?: string;
}

export interface AppearanceView {
  age: number | null;
  eyeColor: string | null;
  heightCm: number | null;
  skin: string | null;
  weightKg: number | null;
  hair: string | null;
}

export interface BioSnapshot {
  name: string;
  portraitUrl: string | null;
  symbolUrl: string | null;
  raceId: string | null;
  pathId: string | null;
  classId: string | null;
  specializationId: string | null;
  level: number;
  background: string | null;
  alignment: string | null;
  appearance: AppearanceView | null;
  personalityTraits: string | null;
  ideals: string | null;
  bonds: string | null;
  flaws: string | null;
  backstory: string | null;
  notes: string | null;
  allies: string | null;
  organizations: string | null;
  titles: string | null;
}

/** Partial bio update; omitted fields are left unchanged server-side. */
export interface BioPatch {
  name?: string;
  portraitUrl?: string;
  symbolUrl?: string;
  background?: string;
  alignment?: string;
  appearance?: AppearanceView;
  personalityTraits?: string;
  ideals?: string;
  bonds?: string;
  flaws?: string;
  backstory?: string;
  notes?: string;
  allies?: string;
  organizations?: string;
  titles?: string;
}

export interface CombatSnapshot {
  name: string;
  level: number;
  pathId: string;
  classId: string;
  specializationId: string | null;
  stats: Record<AbilityScore, number>;
  modifiers: Record<AbilityScore, number>;
  hp: HpView;
  ac: number;
  pa: number;
  ma: number;
  /** Remaining typed-shield absorption, mirrored out of the shield effects. 0 = none up. */
  tempShieldPhysical: number;
  tempShieldMagical: number;
  ap: ApView;
  mana: ManaView;
  /** Class resource (chakra/rages/energy/focus/…) — null when the class has none. */
  resource: ResourceView | null;
  /** Sub-resource pools (perseverance/fury/…) — empty for classes without pools. */
  pools: PoolView[];
  speed: number;
  bonusInitiative: number;
  deathStacks: number;
  /** ALIVE | DOWNED | DEAD (M2-D); downedRoundsRemaining/reviveDC present only while DOWNED. */
  status: 'ALIVE' | 'DOWNED' | 'DEAD';
  downedRoundsRemaining?: number | null;
  reviveDC?: number | null;
  pendingDeathFight: boolean;
  downsThisCombat: number;
  savingThrowProficiencies: AbilityScore[];
  proficiencies: string[];
  activeEffects: EffectView[];
  equippedWeapon: string | null;
  /** every equipped weapon (two when dual-wielding) — drives the attack picker */
  equippedWeapons: string[];
  equippedArmor: string | null;
  conditions: string[];
  /** Q30 (M5-B): equipped gear without proficiency + the consequences (DM display data). */
  proficiencyPenalties: { itemId: string; penalty: string }[];
  /** M6-C progression state — drives the level-up UI's choice pools. */
  talents: string[];
  specFeats: string[];
  /** Derived stats the GM pinned to a literal value; keys are OverridableStat keys. */
  statOverrides: Record<string, number>;
}
