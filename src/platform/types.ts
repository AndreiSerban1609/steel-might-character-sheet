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

export interface InventoryItemView {
  itemId: string;
  quantity: number;
  upgradeTier: number;
  equipped: boolean;
  space: number;
}

export interface InventorySnapshot {
  items: InventoryItemView[];
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
  savingThrowProficiencies: AbilityScore[];
  proficiencies: string[];
  activeEffects: EffectView[];
  equippedWeapon: string | null;
  equippedArmor: string | null;
  conditions: string[];
}
