import * as fs from 'fs';
import * as path from 'path';
import { fileURLToPath } from 'url';

// ESM-safe __dirname (tsx runs this as an ES module)
const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url));
const DATA_DIR = path.resolve(SCRIPT_DIR, '..', 'src', 'data');

let errors: string[] = [];
let warnings: string[] = [];

function error(msg: string) { errors.push(msg); }
function warn(msg: string) { warnings.push(msg); }

function loadJson(filename: string): any {
  const filepath = path.join(DATA_DIR, filename);
  try {
    return JSON.parse(fs.readFileSync(filepath, 'utf-8'));
  } catch (e: any) {
    error(`Failed to parse ${filename}: ${e.message}`);
    return null;
  }
}

// ── Load all data files ──

const classes: any[] | null = loadJson('classes.json');
const classAbilities: Record<string, any> | null = loadJson('class-abilities.json');
const specializations: Record<string, any[]> | null = loadJson('specializations.json');
const talents: any[] | null = loadJson('talents.json');
const races: any | null = loadJson('races.json');
const weapons: any[] | null = loadJson('weapons.json');
const armor: any[] | null = loadJson('armor.json');
const pricing: any | null = loadJson('pricing.json');
const effects: any | null = loadJson('effects.json');
const skills: any[] | null = loadJson('skills.json');
const itemProperties: any[] | null = loadJson('item-properties.json');
const consumables: any | null = loadJson('consumables.json');
const spellcasting: any | null = loadJson('spellcasting.json');
const characterCreation: any | null = loadJson('character-creation.json');
const casterWeapons: any[] | null = loadJson('caster-weapons.json');
const mounts: any[] | null = loadJson('mounts.json');
const damageTypes: any | null = loadJson('damage-types.json');

const spellFiles = [
  'spells-musician.json', 'spells-disciple.json', 'spells-wildborn.json',
  'spells-corruptor.json', 'spells-wizard.json', 'spells-battlemage.json',
  'spells-archer.json', 'spells-rogue.json', 'spells-warrior.json',
  'spells-wraith-hunter.json'
];
const allSpells: any[] = [];
for (const sf of spellFiles) {
  const spells = loadJson(sf);
  if (spells) allSpells.push(...spells);
}

// ── Helpers ──

function checkNoDuplicateIds(items: any[], label: string) {
  const seen = new Set<string>();
  for (const item of items) {
    if (!item.id) { error(`${label}: entry missing 'id' field — name: ${item.name || 'unknown'}`); continue; }
    if (seen.has(item.id)) error(`${label}: duplicate id "${item.id}"`);
    seen.add(item.id);
  }
}

function checkArrayLength(arr: any[], expected: number, label: string) {
  if (arr.length !== expected) error(`${label}: expected ${expected} entries, got ${arr.length}`);
}

// ── Collect known IDs ──

const classIds = new Set(classes?.map(c => c.id) ?? []);
const allSubclassIds = new Set<string>();
if (classes) {
  for (const c of classes) {
    for (const sc of c.classes) allSubclassIds.add(sc);
  }
}
const talentIds = new Set(talents?.map(t => t.id) ?? []);
const weaponIds = new Set(weapons?.map(w => w.id) ?? []);
const armorIds = new Set(armor?.map(a => a.id) ?? []);
const propertyIds = new Set(itemProperties?.map(p => p.id) ?? []);
const effectIds = new Set([
  ...(effects?.negative?.map((e: any) => e.id) ?? []),
  ...(effects?.positive?.map((e: any) => e.id) ?? [])
]);
const skillIds = new Set(skills?.map(s => s.id) ?? []);
const pricingTierIds = new Set(pricing ? Object.keys(pricing.tiers) : []);

const validAbilityScores = new Set(['str', 'dex', 'const', 'int', 'wis', 'will', 'cha']);
const validDamageTypes = new Set<string>();
if (damageTypes) {
  for (const category of Object.values(damageTypes) as string[][]) {
    for (const dt of category) validDamageTypes.add(dt);
  }
}
const validCasterTypes = new Set(['major', 'minor', 'none']);
const validArmorTypes = new Set(['light', 'medium', 'heavy', 'shield']);

// ── 1. Classes ──

if (classes) {
  console.log(`\n── Classes: ${classes.length} entries ──`);
  checkNoDuplicateIds(classes, 'classes.json');
  for (const c of classes) {
    for (const st of c.savingThrowProficiencies ?? []) {
      if (!validAbilityScores.has(st)) error(`classes.json [${c.id}]: invalid saving throw ability "${st}"`);
    }
    for (const ap of c.armorProficiencies ?? []) {
      if (ap !== 'all' && !armorIds.has(ap)) warn(`classes.json [${c.id}]: armor proficiency "${ap}" not in armor.json`);
    }
    if (!c.classes || c.classes.length === 0) error(`classes.json [${c.id}]: no classes defined`);
  }
}

// ── 2. Class Abilities ──

if (classAbilities) {
  const caKeys = Object.keys(classAbilities);
  console.log(`\n── Class Abilities: ${caKeys.length} subclasses ──`);

  for (const [subId, entry] of Object.entries(classAbilities) as [string, any][]) {
    if (!allSubclassIds.has(subId)) error(`class-abilities.json: key "${subId}" not found in any class's subclasses array`);
    if (!classIds.has(entry.pathId)) error(`class-abilities.json [${subId}]: pathId "${entry.pathId}" not in classes.json`);
    if (!validCasterTypes.has(entry.casterType)) error(`class-abilities.json [${subId}]: invalid casterType "${entry.casterType}"`);
    if (entry.spellStat && !validAbilityScores.has(entry.spellStat)) error(`class-abilities.json [${subId}]: invalid spellStat "${entry.spellStat}"`);
    if (typeof entry.hpPerLevel !== 'number' || entry.hpPerLevel <= 0) error(`class-abilities.json [${subId}]: invalid hpPerLevel`);

    if (entry.manaIncreases && entry.manaIncreases.length !== 4) {
      error(`class-abilities.json [${subId}]: manaIncreases should have 4 entries (got ${entry.manaIncreases.length})`);
    }

    if (!entry.abilities || entry.abilities.length === 0) error(`class-abilities.json [${subId}]: no abilities defined`);
    for (const ab of entry.abilities ?? []) {
      if (typeof ab.level !== 'number' || ab.level < 1 || ab.level > 20) {
        error(`class-abilities.json [${subId}]: ability "${ab.name}" has invalid level ${ab.level}`);
      }
    }
  }

  for (const subId of allSubclassIds) {
    if (!classAbilities[subId]) error(`class-abilities.json: missing entry for subclass "${subId}"`);
  }
}

// ── 3. Specializations ──

if (specializations) {
  const specKeys = Object.keys(specializations);
  console.log(`\n── Specializations: ${specKeys.length} subclasses ──`);

  let totalSpecs = 0;
  for (const [subId, specs] of Object.entries(specializations) as [string, any[]][]) {
    if (!allSubclassIds.has(subId)) error(`specializations.json: key "${subId}" not found in any class's subclasses array`);
    totalSpecs += specs.length;

    for (const spec of specs) {
      if (!spec.name) error(`specializations.json [${subId}]: specialization missing name`);
      if (!spec.active?.name) error(`specializations.json [${subId}/${spec.name}]: missing active feat`);
      if (!spec.passive?.name) error(`specializations.json [${subId}/${spec.name}]: missing passive feat`);
      if (!spec.modification?.name) error(`specializations.json [${subId}/${spec.name}]: missing modification feat`);

      if (spec.startingTalent) {
        if (spec.startingTalent === 'unknown') {
          warn(`specializations.json [${subId}/${spec.name}]: startingTalent is "unknown" (missing from PDF)`);
        } else if (!talentIds.has(spec.startingTalent)) {
          error(`specializations.json [${subId}/${spec.name}]: startingTalent "${spec.startingTalent}" not in talents.json`);
        }
      }
    }
  }
  console.log(`   Total specializations: ${totalSpecs}`);

  for (const subId of allSubclassIds) {
    if (!specializations[subId]) error(`specializations.json: missing entry for subclass "${subId}"`);
  }
}

// ── 4. Talents ──

if (talents) {
  console.log(`\n── Talents: ${talents.length} entries ──`);
  checkNoDuplicateIds(talents, 'talents.json');
  for (const t of talents) {
    if (!t.name) error(`talents.json [${t.id}]: missing name`);
    if (!t.description) error(`talents.json [${t.id}]: missing description`);
  }
}

// ── 5. Races ──

if (races) {
  const raceList: any[] = races.races ?? races;
  console.log(`\n── Races: ${raceList.length} entries ──`);
  checkNoDuplicateIds(raceList, 'races.json');

  let complete = 0, placeholder = 0;
  for (const r of raceList) {
    if (r.active && r.passive && r.skillAbility) complete++;
    else placeholder++;
  }
  console.log(`   Complete: ${complete}, Placeholder: ${placeholder}`);
}

// ── 6. Weapons ──

if (weapons) {
  console.log(`\n── Weapons: ${weapons.length} entries ──`);
  checkNoDuplicateIds(weapons, 'weapons.json');

  for (const w of weapons) {
    if (w.priceTier && !pricingTierIds.has(w.priceTier)) {
      error(`weapons.json [${w.id}]: priceTier "${w.priceTier}" not in pricing.json`);
    }
    for (const prop of w.properties ?? []) {
      if (!propertyIds.has(prop)) error(`weapons.json [${w.id}]: property "${prop}" not in item-properties.json`);
    }
    for (const cls of w.proficientClasses ?? []) {
      if (!allSubclassIds.has(cls) && !classIds.has(cls)) {
        warn(`weapons.json [${w.id}]: proficientClass "${cls}" not a known class or subclass ID`);
      }
    }
    for (const s of w.stat ?? []) {
      if (!validAbilityScores.has(s)) error(`weapons.json [${w.id}]: invalid stat "${s}"`);
    }
    if (w.damageType && !validDamageTypes.has(w.damageType)) {
      error(`weapons.json [${w.id}]: invalid damageType "${w.damageType}"`);
    }
  }
}

// ── 7. Armor ──

if (armor) {
  console.log(`\n── Armor: ${armor.length} entries ──`);
  checkNoDuplicateIds(armor, 'armor.json');

  for (const a of armor) {
    if (a.priceTier && !pricingTierIds.has(a.priceTier)) {
      error(`armor.json [${a.id}]: priceTier "${a.priceTier}" not in pricing.json`);
    }
    if (a.type && !validArmorTypes.has(a.type)) {
      error(`armor.json [${a.id}]: invalid armor type "${a.type}"`);
    }
    for (const cls of a.proficientClasses ?? []) {
      if (cls !== 'all' && !allSubclassIds.has(cls) && !classIds.has(cls)) {
        warn(`armor.json [${a.id}]: proficientClass "${cls}" not a known class or subclass ID`);
      }
    }
  }
}

// ── 8. Pricing ──

if (pricing) {
  console.log(`\n── Pricing: ${Object.keys(pricing.tiers).length} tiers ──`);
  for (const [tid, tier] of Object.entries(pricing.tiers) as [string, any][]) {
    checkArrayLength(tier.prices, 20, `pricing.json tier "${tid}"`);
  }
}

// ── 9. Effects ──

if (effects) {
  const neg = effects.negative ?? [];
  const pos = effects.positive ?? [];
  console.log(`\n── Effects: ${neg.length} negative, ${pos.length} positive ──`);
  checkNoDuplicateIds(neg, 'effects.json (negative)');
  checkNoDuplicateIds(pos, 'effects.json (positive)');

  for (const e of neg) {
    if (e.composedOf) {
      for (const ref of e.composedOf) {
        if (!effectIds.has(ref)) error(`effects.json [${e.id}]: composedOf references unknown effect "${ref}"`);
      }
    }
    if (e.damageType && !validDamageTypes.has(e.damageType)) {
      error(`effects.json [${e.id}]: invalid damageType "${e.damageType}"`);
    }
  }
}

// ── 10. Skills ──

if (skills) {
  console.log(`\n── Skills: ${skills.length} entries ──`);
  checkNoDuplicateIds(skills, 'skills.json');
  for (const s of skills) {
    if (!validAbilityScores.has(s.ability)) error(`skills.json [${s.id}]: invalid ability "${s.ability}"`);
  }
}

// ── 11. Spellcasting ──

if (spellcasting) {
  console.log(`\n── Spellcasting ──`);
  for (const [key, arr] of Object.entries(spellcasting.spellLevelAccess ?? {}) as [string, any][]) {
    checkArrayLength(arr, 20, `spellcasting.json spellLevelAccess["${key}"]`);
  }
  if (spellcasting.proficiencyProgression) {
    checkArrayLength(spellcasting.proficiencyProgression, 20, 'spellcasting.json proficiencyProgression');
  }
}

// ── 12. Consumables ──

if (consumables) {
  console.log(`\n── Consumables ──`);
  if (consumables.healingPotions) {
    checkNoDuplicateIds(consumables.healingPotions.sizes ?? [], 'consumables.json healingPotions.sizes');
    for (const [size, prices] of Object.entries(consumables.healingPotions.pricesBySize ?? {}) as [string, any][]) {
      checkArrayLength(prices, 20, `consumables.json healingPotions.pricesBySize["${size}"]`);
    }
  }
  if (consumables.magicShopItems) checkNoDuplicateIds(consumables.magicShopItems, 'consumables.json magicShopItems');
  if (consumables.scrolls) checkNoDuplicateIds(consumables.scrolls, 'consumables.json scrolls');
  if (consumables.generalShop) checkNoDuplicateIds(consumables.generalShop, 'consumables.json generalShop');
}

// ── 13. Caster Weapons ──

if (casterWeapons) {
  console.log(`\n── Caster Weapons: ${casterWeapons.length} entries ──`);
  checkNoDuplicateIds(casterWeapons, 'caster-weapons.json');
  for (const cw of casterWeapons) {
    if (cw.priceTier && !pricingTierIds.has(cw.priceTier)) {
      error(`caster-weapons.json [${cw.id}]: priceTier "${cw.priceTier}" not in pricing.json`);
    }
    if (cw.damageType && !validDamageTypes.has(cw.damageType)) {
      error(`caster-weapons.json [${cw.id}]: invalid damageType "${cw.damageType}"`);
    }
  }
}

// ── 14. Mounts ──

if (mounts) {
  console.log(`\n── Mounts: ${mounts.length} entries ──`);
  checkNoDuplicateIds(mounts, 'mounts.json');
}

// ── 15. Spells ──

console.log(`\n── Spells: ${allSpells.length} total across ${spellFiles.length} files ──`);
checkNoDuplicateIds(allSpells, 'spells (all files)');

const spellsByClass: Record<string, number> = {};
for (const spell of allSpells) {
  spellsByClass[spell.classId] = (spellsByClass[spell.classId] ?? 0) + 1;

  if (!allSubclassIds.has(spell.classId)) {
    error(`spell [${spell.id}]: classId "${spell.classId}" not a known subclass ID`);
  }
  if (spell.damageType && !validDamageTypes.has(spell.damageType)) {
    if (spell.damageTypes) {
      for (const dt of spell.damageTypes) {
        if (!validDamageTypes.has(dt)) error(`spell [${spell.id}]: invalid damageType "${dt}" in multi-type`);
      }
    } else {
      error(`spell [${spell.id}]: invalid damageType "${spell.damageType}"`);
    }
  }
  if (spell.saveStat && !validAbilityScores.has(spell.saveStat)) {
    error(`spell [${spell.id}]: invalid saveStat "${spell.saveStat}"`);
  }
  if (typeof spell.level !== 'number' || spell.level < 0 || spell.level > 9) {
    error(`spell [${spell.id}]: invalid level ${spell.level}`);
  }
  if (spell.effects) {
    for (const eff of spell.effects) {
      if (!effectIds.has(eff)) warn(`spell [${spell.id}]: references unknown effect "${eff}"`);
    }
  }
  if (typeof spell.manaCost === 'string' && !spell.manaCost.includes('%')) {
    warn(`spell [${spell.id}]: manaCost is a non-percentage string "${spell.manaCost}"`);
  }
}

console.log('   Spells by subclass:');
for (const [cls, count] of Object.entries(spellsByClass).sort((a, b) => b[1] - a[1])) {
  console.log(`     ${cls}: ${count}`);
}

// ── 16. Cross-file: all subclass IDs in class-abilities also in specializations ──

if (classAbilities && specializations) {
  const caKeys = new Set(Object.keys(classAbilities));
  const specKeys = new Set(Object.keys(specializations));
  for (const k of caKeys) {
    if (!specKeys.has(k)) warn(`subclass "${k}" has class-abilities but no specializations entry`);
  }
  for (const k of specKeys) {
    if (!caKeys.has(k)) warn(`subclass "${k}" has specializations but no class-abilities entry`);
  }
}

// ── 17. Character Creation ──

if (characterCreation) {
  console.log(`\n── Character Creation ──`);
  if (characterCreation.statArray) {
    checkArrayLength(characterCreation.statArray, 7, 'character-creation.json statArray (7 ability scores)');
  }
  if (characterCreation.abilities) {
    for (const ab of characterCreation.abilities) {
      if (!validAbilityScores.has(ab)) error(`character-creation.json: invalid ability "${ab}"`);
    }
    checkArrayLength(characterCreation.abilities, 7, 'character-creation.json abilities');
  }
}

// ── 18. Class Abilities (abilities-*.json, Epic 1) ──

const abilityFiles = [
  'abilities-archer.json', 'abilities-monk.json', 'abilities-rogue.json',
  'abilities-warrior.json', 'abilities-wildborn.json', 'abilities-wraith-hunter.json'
];
const validAbilityKinds = new Set(['active', 'reaction', 'attack-enhancer', 'passive']);
const validAbilityResolutions = new Set(['auto', 'manual']);
const validPoolRestores = new Set(['on-rest', 'manual']);

{
  const allAbilityIds = new Set<string>();
  let abilityCount = 0;
  for (const af of abilityFiles) {
    const doc = loadJson(af);
    if (!doc) continue;
    if (!Array.isArray(doc.abilities)) { error(`${af}: missing 'abilities' array`); continue; }

    for (const [cls, defs] of Object.entries(doc.pools ?? {}) as [string, any[]][]) {
      if (!allSubclassIds.has(cls)) error(`${af}: pools key "${cls}" is not a known class`);
      for (const pd of defs) {
        if (!pd.id) error(`${af} pools[${cls}]: pool missing id`);
        if (!validPoolRestores.has(pd.restore)) error(`${af} pools[${cls}/${pd.id}]: invalid restore "${pd.restore}"`);
        const isFormula = pd.maxFormula != null;
        if (!isFormula && typeof pd.initial !== 'number') error(`${af} pools[${cls}/${pd.id}]: numeric pool needs 'initial'`);
        if (typeof pd.max === 'number' && typeof pd.initial === 'number' && pd.initial > pd.max) {
          error(`${af} pools[${cls}/${pd.id}]: initial ${pd.initial} > max ${pd.max}`);
        }
      }
    }

    for (const a of doc.abilities) {
      abilityCount++;
      const where = `${af} [${a.id ?? a.name ?? '?'}]`;
      if (!a.id) { error(`${where}: missing id`); continue; }
      if (allAbilityIds.has(a.id)) error(`${where}: duplicate ability id across files`);
      allAbilityIds.add(a.id);
      if (!allSubclassIds.has(a.classId)) error(`${where}: classId "${a.classId}" is not a known class`);
      if (typeof a.minLevel !== 'number' || a.minLevel < 1 || a.minLevel > 20) error(`${where}: invalid minLevel ${a.minLevel}`);
      if (!validAbilityKinds.has(a.kind)) error(`${where}: invalid kind "${a.kind}"`);
      if (!validAbilityResolutions.has(a.resolution)) error(`${where}: invalid resolution "${a.resolution}"`);
      if (!a.description) error(`${where}: missing description`);
      for (const c of a.costs ?? []) {
        if (!c.resource) error(`${where}: cost missing resource`);
        if (c.amount == null && c.amountDice == null) error(`${where}: cost needs amount or amountDice`);
      }
      if (a.targetEffect && !effectIds.has(a.targetEffect.effectId)) {
        error(`${where}: targetEffect "${a.targetEffect.effectId}" not in effects.json`);
      }
      if (a.selfEffect && !effectIds.has(a.selfEffect.effectId)) {
        error(`${where}: selfEffect "${a.selfEffect.effectId}" not in effects.json`);
      }
    }
  }
  console.log(`\n── Class ability files: ${abilityCount} abilities, ${allAbilityIds.size} unique ids ──`);
}

// ── Summary ──

console.log('\n═══════════════════════════════════');
if (errors.length === 0 && warnings.length === 0) {
  console.log('✓ All validations passed!');
} else {
  if (warnings.length > 0) {
    console.log(`\n⚠ ${warnings.length} WARNINGS:`);
    for (const w of warnings) console.log(`  ⚠ ${w}`);
  }
  if (errors.length > 0) {
    console.log(`\n✗ ${errors.length} ERRORS:`);
    for (const e of errors) console.log(`  ✗ ${e}`);
  }
}
console.log('═══════════════════════════════════\n');

process.exit(errors.length > 0 ? 1 : 0);
