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
}

export interface SkillCheckResult {
  skillId: string;
  ability: string;
  card: Card;
  d10: number;
  effectiveModifier: number | null;
  total: number | null;
  critical: boolean;
  proficient: boolean;
}

export interface DeckCard {
  name: string;
  modifier: number;
  description: string;
}

export interface DeckTemplate {
  neutralCards: DeckCard[];
  statCount: number;
  encounterCards: DeckCard[];
}

export interface PlayerDeckConfig {
  statAdjust: number;
  extraCards: DeckCard[];
}

export interface PlayerDeckView {
  room: DeckTemplate;
  config: PlayerDeckConfig;
  deckSize: number;
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

/** The cast's d20 spell-attack roll (attack-type spells only). */
export interface AttackRollView {
  roll: number;
  bonus: number;
  total: number;
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
  ap: ApView;
  mana: ManaView;
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
  equippedArmor: string | null;
  conditions: string[];
  /** Q30 (M5-B): equipped gear without proficiency + the consequences (DM display data). */
  proficiencyPenalties: { itemId: string; penalty: string }[];
  /** M6-C progression state — drives the level-up UI's choice pools. */
  talents: string[];
  specFeats: string[];
}
