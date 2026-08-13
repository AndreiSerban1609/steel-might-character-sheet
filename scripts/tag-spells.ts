/**
 * Spell tagging pass (demo feedback #21, taxonomy ruled 2026-08-13).
 *
 * Writes a `tags` array onto every spell in src/data/spells-*.json. Most tags fall out of
 * structured fields the data already carries (damage, healing, damageType, effects,
 * duration); the rest come from description keywords. Deliberately NOT an AI pass over 817
 * descriptions — the structured signals are exact, and a keyword pass over the remainder is
 * reviewable, which one-shot generated tags are not.
 *
 * Idempotent: re-running replaces the tags rather than appending, so it can be re-run after
 * hand corrections to see what the rules would have said. Hand-corrected spells listed in
 * MANUAL_TAGS below always win.
 *
 *   npm run tag-spells          # write tags
 *   npm run tag-spells -- --dry # report only, touch nothing
 */
import * as fs from 'fs';
import * as path from 'path';
import { fileURLToPath } from 'url';

const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url));
const DATA_DIR = path.resolve(SCRIPT_DIR, '..', 'src', 'data');
const DRY_RUN = process.argv.includes('--dry');

const ELEMENTS = [
  'pure', 'spectral', 'light', 'shadow', 'fire', 'ice', 'lightning',
  'poison', 'thunder', 'psychic', 'force',
] as const;

/** Physical/true damage types are real, but they aren't part of the 11 element tags. */
const NON_ELEMENT_DAMAGE = new Set(['slashing', 'piercing', 'crushing', 'true']);

/**
 * Spells the rules get wrong, corrected by hand. Key = spell id, value = the full tag list.
 * Add to this rather than editing the JSON, so a re-run doesn't silently undo the fix.
 */
const MANUAL_TAGS: Record<string, string[]> = {};

// Keyword sets. Matched case-insensitively against name + description.
const AOE = [
  'each creature', 'all creatures', 'every creature', 'each enemy', 'all enemies',
  'each ally', 'all allies', 'each target', 'all targets', 'creatures within',
  'enemies within', 'allies within', 'targets within', 'creatures in', 'in a cone',
  'in a line', 'radius', 'sphere', 'cone of', 'line of', 'area of effect', 'aoe',
  'up to \\d+ (?:creatures|targets|enemies|allies)', 'multiple targets',
];
const CONTROL = [
  'stunned', 'stun', 'rooted', 'root', 'paralyz', 'restrain', 'slowed', 'slow the',
  'frozen', 'freeze', 'prone', 'silenced', 'silence', 'charm', 'frighten', 'feared',
  'blinded', 'blind', 'incapacitat', 'cannot move', "can't move", 'cannot act',
  "can't act", 'loses their turn', 'skips their turn', 'pushed', 'pulled', 'knock',
  'grappl', 'immobil', 'banish', 'sleep', 'unconscious', 'taunt', 'provoke',
];
const BUFF = [
  'gains advantage', 'has advantage', 'gain advantage', 'bonus to', 'increases by',
  'increased by', 'gains \\+', 'gain \\+', 'additional damage', 'extra damage',
  'empower', 'bless', 'inspire', 'haste', 'strengthen', 'enhance', 'improves',
];
const DEBUFF = [
  'disadvantage', 'reduced by', 'reduces by', 'decreas', 'weaken', 'vulnerab',
  'curse', 'penalty', 'lowers', 'loses \\d', 'drain', 'wither', 'expose',
];
const DEFENSE = [
  'temporary hp', 'temporary hit points', 'temp hp', 'shield', 'barrier', 'ward',
  'resistance', 'resistant', 'immune', 'immunity', 'absorb', 'damage taken is reduced',
  'reduce the damage', 'reduces the damage', 'physical armor', 'magic armor',
  'armor class', 'ac increases', 'protect', 'guard', 'deflect', 'parry', 'block',
  // S&M writes the armor stats as bare initialisms far more often than in words.
  '\\bPA\\b', '\\bMA\\b', 'redirect the attack', 'becoming the new target',
];
const MOVEMENT = [
  'teleport', 'blink', 'dash', 'you may move', 'can move', 'move up to', 'movement speed',
  'fly', 'flying', 'leap', 'jump', 'step', 'shift', 'swap places', 'reposition',
  'phase through', 'burrow', 'climb speed', 'swim speed',
  'transport', 'switch places', 'move your',
];
// NOT a bare "create a" — S&M writes zones, walls and auras as "you create a 15 ft radius
// …", which is an area effect, not a summon. Only conjuring something that ACTS is a summon.
const SUMMON = [
  'summon', 'conjure', 'call forth', 'familiar', 'minion', 'servant', 'construct',
  'animate', 'spirit appears', 'raise a', 'raise the dead', 'under your control',
  'obeys your', 'acts on your turn', 'creature appears', 'beast appears',
];
const RITUAL = ['ritual', 'ceremony', 'takes \\d+ (?:minutes|hours)', 'over the course of'];
const HEAL_WORDS = [
  'heal', 'restore.{0,25}hp', 'restore.{0,25}hit points', 'restore.{0,25}health',
  'regain.{0,30}(?:hp|hit points|damage)', 'revive', 'resurrect', 'bring.{0,15}back to life',
];

function matches(text: string, patterns: string[]): boolean {
  return patterns.some((p) => new RegExp(p, 'i').test(text));
}

interface Spell {
  id: string;
  name: string;
  description: string;
  damage?: unknown;
  healing?: unknown;
  damageType?: string | null;
  damageTypes?: string[];
  effects?: string[];
  duration?: string | null;
  channeling?: boolean;
  saveStat?: string | null;
  tags?: string[];
  [k: string]: unknown;
}

/** Effect polarity, so a spell that applies an effect is tagged by what the effect IS. */
function loadEffectPolarity(): Map<string, 'negative' | 'positive'> {
  const raw = JSON.parse(fs.readFileSync(path.join(DATA_DIR, 'effects.json'), 'utf-8'));
  const map = new Map<string, 'negative' | 'positive'>();
  for (const e of raw.negative ?? []) map.set(e.id, 'negative');
  for (const e of raw.positive ?? []) map.set(e.id, 'positive');
  return map;
}

const polarity = loadEffectPolarity();

function tagsFor(spell: Spell): string[] {
  if (MANUAL_TAGS[spell.id]) return [...MANUAL_TAGS[spell.id]];

  const tags = new Set<string>();
  const text = `${spell.name} ${spell.description ?? ''}`;

  // ── Structured signals: exact, no guessing ──
  if (spell.damage) tags.add('damage');
  if (spell.healing) tags.add('healing');

  const damageTypes = [spell.damageType, ...(spell.damageTypes ?? [])].filter(Boolean) as string[];
  for (const dt of damageTypes) {
    if (ELEMENTS.includes(dt as (typeof ELEMENTS)[number])) tags.add(dt);
    // A spell with a damage type deals damage even if the formula lives in the text.
    if (!NON_ELEMENT_DAMAGE.has(dt) || dt === 'true') tags.add('damage');
    else tags.add('damage');
  }

  for (const effectId of spell.effects ?? []) {
    const pol = polarity.get(effectId);
    if (pol === 'negative') tags.add('debuff');
    if (pol === 'positive') tags.add('buff');
  }

  // ── Keyword signals over name + description ──
  if (matches(text, AOE)) tags.add('aoe');
  if (matches(text, CONTROL)) tags.add('control');
  if (matches(text, BUFF)) tags.add('buff');
  if (matches(text, DEBUFF)) tags.add('debuff');
  if (matches(text, DEFENSE)) tags.add('defense');
  if (matches(text, MOVEMENT)) tags.add('movement');
  if (matches(text, SUMMON)) tags.add('summon');
  if (matches(text, RITUAL)) tags.add('ritual');
  if (!tags.has('healing') && matches(text, HEAL_WORDS)) tags.add('healing');

  // A save that isn't attached to damage is almost always a control/debuff effect.
  if (spell.saveStat && !tags.has('damage') && !tags.has('control')) tags.add('debuff');

  // Long non-concentration durations read as ritual-ish preparation, not combat casting.
  if (typeof spell.duration === 'string' && /\b(hour|hours|day|days)\b/i.test(spell.duration)) {
    tags.add('ritual');
  }

  // Fallback: something that neither harms, heals, nor alters anyone is utility.
  if (tags.size === 0) tags.add('utility');

  // Stable order: roles first (declaration order), then elements.
  const ROLE_ORDER = ['damage', 'healing', 'buff', 'debuff', 'control', 'defense',
    'movement', 'summon', 'ritual', 'utility', 'aoe'];
  return [
    ...ROLE_ORDER.filter((t) => tags.has(t)),
    ...ELEMENTS.filter((t) => tags.has(t)),
  ];
}

// ── Run ──

const files = fs.readdirSync(DATA_DIR).filter((f) => /^spells-.*\.json$/.test(f)).sort();
const counts = new Map<string, number>();
const untagged: string[] = [];
const utilityOnly: string[] = [];
let total = 0;
let changed = 0;

for (const file of files) {
  const filepath = path.join(DATA_DIR, file);
  const spells: Spell[] = JSON.parse(fs.readFileSync(filepath, 'utf-8'));
  let fileChanged = false;

  for (const spell of spells) {
    const tags = tagsFor(spell);
    total++;
    for (const t of tags) counts.set(t, (counts.get(t) ?? 0) + 1);
    if (tags.length === 1 && tags[0] === 'utility') utilityOnly.push(`${file}: ${spell.id}`);
    if (!spell.description) untagged.push(`${file}: ${spell.id} (no description)`);

    if (JSON.stringify(spell.tags) !== JSON.stringify(tags)) {
      spell.tags = tags;
      fileChanged = true;
      changed++;
    } else {
      spell.tags = tags;
    }
  }

  if (fileChanged && !DRY_RUN) {
    fs.writeFileSync(filepath, JSON.stringify(spells, null, 2) + '\n', 'utf-8');
  }
}

console.log(`\n${DRY_RUN ? '[dry run] ' : ''}Tagged ${total} spells across ${files.length} files (${changed} changed)\n`);
console.log('Tag distribution:');
for (const [tag, n] of [...counts.entries()].sort((a, b) => b[1] - a[1])) {
  console.log(`  ${tag.padEnd(12)} ${String(n).padStart(4)}  ${'█'.repeat(Math.round(n / 12))}`);
}

if (utilityOnly.length > 0) {
  console.log(`\n⚠ ${utilityOnly.length} spells fell through to "utility" alone — these are the`);
  console.log('  ones worth a human read, since the rules found no signal at all:');
  for (const s of utilityOnly.slice(0, 40)) console.log(`    ${s}`);
  if (utilityOnly.length > 40) console.log(`    … and ${utilityOnly.length - 40} more`);
}
if (untagged.length > 0) {
  console.log(`\n⚠ ${untagged.length} spells have no description to read.`);
  for (const s of untagged.slice(0, 20)) console.log(`    ${s}`);
}
console.log('');
