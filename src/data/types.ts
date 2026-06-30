// ── Shared Enums ──

export type PhysicalDamageType = 'slashing' | 'piercing' | 'crushing';
export type MagicalDamageType =
  | 'shadow' | 'light' | 'fire' | 'ice' | 'lightning'
  | 'poison' | 'thunder' | 'psychic' | 'spectral' | 'pure' | 'force';
export type DamageType = PhysicalDamageType | MagicalDamageType | 'true';

export type AbilityScore = 'str' | 'dex' | 'const' | 'int' | 'wis' | 'will' | 'cha';
export type CasterType = 'major' | 'minor' | 'none';
export type SpellComponent = 'V' | 'S' | 'W';
export type ArmorType = 'light' | 'medium' | 'heavy' | 'shield';
export type CasterWeaponType = 'spellbook' | 'orb' | 'wand' | 'staff';

// ── Damage / Healing Formulas ──

export interface DiceFormula {
  modMultiplier: number;
  flat: number;
  dice: string; // e.g. "1d8", "2d6"
}

// ── Effects ──

export interface CorrodeApplication {
  order: number;
  effect: string;
  value?: number;
  duration: string;
}

export interface NegativeEffect {
  id: string;
  name: string;
  stackBased: boolean;
  multiInstance: boolean;
  applicationBased?: boolean;
  maxApplications?: number;
  applications?: CorrodeApplication[];
  composedOf?: string[];
  damageType?: DamageType;
  timing?: 'startOfTurn' | 'endOfTurn';
  standUpBaseCost?: number;
  additionalCostPerStack?: number;
  description: string;
}

export interface PositiveEffect {
  id: string;
  name: string;
  hasValue: boolean;
  timing?: 'startOfTurn' | 'endOfTurn';
  description: string;
}

export interface ConditionTerm {
  threshold: number;
  comparison: 'below';
}

export interface EffectsData {
  negative: NegativeEffect[];
  positive: PositiveEffect[];
  conditionTerms: Record<string, ConditionTerm>;
}

// ── Weapons ──

export interface WeaponProperty {
  id: string;
  description: string;
  hasRange?: boolean;
  hasApCost?: boolean;
  defaultRange?: number;
}

export interface Weapon {
  id: string;
  name: string;
  damage: DiceFormula;
  damageType: DamageType;
  scaling: number;
  stat: AbilityScore[];
  properties: string[];
  propertyDetails?: Record<string, string | number>;
  inventorySpace: number;
  apCost: number;
  proficientClasses: string[];
  priceTier: string;
}

// ── Armor ──

export interface ArmorAC {
  base: number;
  dexMod: boolean;
  dexMultiplier?: number;
  isBonus?: boolean;
}

export interface Armor {
  id: string;
  name: string;
  type: ArmorType;
  ac: ArmorAC;
  pa: number;
  ma: number;
  paScaling: number;
  maScaling: number;
  acBonusLevels: number[];
  acBonusPerLevel: number;
  properties: string[];
  proficientClasses: string[];
  priceTier: string;
}

// ── Pricing ──

export interface PricingTier {
  description: string;
  prices: number[]; // 20 entries, in coppers
}

export interface UpgradeKitRules {
  description: string;
  costTier: string;
  costDivisor: number;
  checkDC: { base: number; perLevel: number };
  inventorySpace: number;
}

export interface PricingData {
  tiers: Record<string, PricingTier>;
  currency: { copperPerSilver: number; copperPerGold: number };
  upgradeKit: UpgradeKitRules;
  blacksmithUpgrade: { description: string; surchargePercent: number };
  silveringMultiplier: number;
  sellbackRatio: number;
}

// ── Skills ──

export interface Skill {
  id: string;
  name: string;
  ability: AbilityScore;
}

// ── Consumables ──

export interface HealingPotionSize {
  id: string;
  name: string;
  healPerLevel: number;
  apCost: number;
  inventorySpace: number;
}

export interface MagicShopItem {
  id: string;
  name: string;
  price: number;
  inventorySpace: number;
  slot?: string;
  apCost?: number;
  usesPerLongRest?: number;
  charges?: number | null;
  description: string;
}

export interface Scroll {
  id: string;
  name: string;
  spellLevel: number;
  casterType: CasterType;
  minCharLevel?: number;
  price: number;
}

export interface GeneralShopItem {
  id: string;
  name: string;
  price: number;
  inventorySpace: number;
  description?: string;
}

export interface ConsumablesData {
  healingPotions: {
    sizes: HealingPotionSize[];
    pricesBySize: Record<string, number[]>;
  };
  magicShopItems: MagicShopItem[];
  scrolls: Scroll[];
  generalShop: GeneralShopItem[];
}

// ── Spellcasting System ──

export interface SpellcastingData {
  spellLevelAccess: Record<string, number[]>;
  spellsKnownProgression: Record<string, { description: string; perLevel: number[] }>;
  intModifierSpells: { description: string; minorRestrictions: string; majorRestrictions: string };
  spellComponents: SpellComponent[];
  concentrationSaveDC: { description: string };
  proficiencyProgression: number[];
  proficiencyMilestones: number[];
}

// ── Character Creation ──

export interface CharacterCreationData {
  statArray: number[];
  bonusPoints: number;
  maxBonusPerStat: number;
  statIncreaseLevels: number[];
  abilities: AbilityScore[];
  modifierFormula: string;
  hpFormula: string;
  defaultAP: { starting: number; recovery: number; maximum: number };
  standardWeaponAttackAP: number;
  movementCost: { apPerMove: number; movementPerAP: string };
  defaultSkillProficiencies: number;
  baseInitiative: string;
  stackThreshold: { player: string };
}

// ── Races ──

export interface RaceAbility {
  name: string;
  description: string;
  apCost?: number;
  duration?: string;
  consumed?: boolean;
  cooldown?: string;
}

export interface RaceSkillAbility {
  type: 'deckModification' | 'proficiency' | 'bonus';
  name: string;
  description: string;
}

export interface Race {
  id: string;
  name: string;
  replaces?: string;
  description: string;
  /** DEPRECATED (N16/N17, round 4): does NOT constrain creation — the +5 bonus may go to any stats (max 2 each). Kept only as an optional UI hint. */
  levelOneStats?: AbilityScore[];
  movementSpeed: number | null;
  senses: Record<string, number> | null;
  sizeRange: Record<string, { heightCm: number[]; weightKg: number[] }> | null;
  alignmentTendency: string | null;
  active: RaceAbility | null;
  passive: RaceAbility | null;
  skillAbility: RaceSkillAbility | null;
}

export interface RacesData {
  _note: string;
  races: Race[];
}

// ── Paths (top-level archetypes; e.g. Musician, Disciple) ──

export interface PathDef {
  id: string;
  name: string;
  description: string;
  classes: string[];
  roles: string[];
  armorProficiencies: string[];
  /** NOTE (N16): saving throws are conceptually per-class; values still sit here on the path pending the per-class data migration */
  savingThrowProficiencies: AbilityScore[];
}

// ── Classes / Specializations ──

export interface SpecializationFeat {
  name: string;
  description: string;
  apCost?: number;
  cooldown?: string;
  modifies?: string;
}

export interface SpecializationTalent {
  id: string;
  name: string;
  description: string;
}

export interface Specialization {
  id: string;
  name: string;
  active: SpecializationFeat;
  passive: SpecializationFeat;
  modification: SpecializationFeat;
  startingTalent: string;
  additionalTalents: SpecializationTalent[];
}

export interface ClassDef {
  id: string;
  name: string;
  pathId: string;
  description: string;
  specializations: Specialization[];
}

// ── Talents ──

export interface Talent {
  id: string;
  name: string;
  description: string;
}

// ── Spells ──

export interface SpellScaling {
  manaCostIncrease: number;
  damageIncrease?: DiceFormula;
  healingIncrease?: DiceFormula;
  description?: string;
}

export interface Spell {
  id: string;
  name: string;
  classId: string;
  level: number;
  apCost: number;
  manaCost: number;
  range: string;
  components: SpellComponent[];
  duration: string | null;
  concentration: boolean;
  channeling: boolean;
  damageType: DamageType | null;
  attackType: 'rangedSpellAttack' | 'meleeSpellAttack' | 'savingThrow' | null;
  saveStat?: AbilityScore;
  damage?: DiceFormula;
  healing?: DiceFormula;
  effects?: string[];
  scaling?: SpellScaling;
  description: string;
}

// ── Class Abilities ──

export interface ClassAbilityOption {
  id: string;
  description: string;
}

export interface ClassAbility {
  level: number;
  name: string;
  type: 'passive' | 'active' | 'scaling' | 'choice' | 'system';
  apCost?: number;
  values?: Record<string, string | number>;
  options?: (ClassAbilityOption | string)[];
  description: string;
}

export interface ClassAbilitiesEntry {
  pathId: string;
  classId: string;
  name: string;
  hpPerLevel: number;
  casterType: CasterType;
  spellStat: AbilityScore | null;
  resourceType: string | null;
  manaPerLevel?: number;
  manaIncreases?: number[];
  resourcePerLevel?: number[];
  maxEnergy?: number;
  sacredEnergyPerLevel?: number[];
  rageScaling?: { acBonus: number[]; damageBonus: number[] };
  proficiencyChoice?: string;
  fistDamageScaling?: string;
  abilities: ClassAbility[];
}

// ── Caster Weapons ──

export interface CasterWeapon {
  id: string;
  name: string;
  type: CasterWeaponType;
  itemLevel: number;
  spellModifier: number;
  spellDamage: number;
  manaCostReduction: number;
  extraSpellsKnown: number;
  damageType: DamageType | null;
  uniqueEffect: string | null;
  wandAttack?: {
    apCost: number;
    damage: DiceFormula & { scalingPerLevel: number };
    manaRestored: string;
    autoHit: boolean;
  };
  staffAccuracy?: {
    spellAttackBonus: number;
    spellSaveDCBonus: number;
  };
  priceTier: string;
}

// ── Mounts ──

export interface Mount {
  id: string;
  name: string;
  price: number;
  carryCapacity: number;
  movementSpeed: number;
  requirements: {
    race: string;
    animalHandling: number;
    proficiency: number;
  };
  abilities: string[];
  stats: Partial<Record<AbilityScore, number>>;
}
