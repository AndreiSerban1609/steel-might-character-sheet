/**
 * Save-outcome pass (ruled by the Game Owner 2026-08-27).
 *
 * Ruling: a successful saving throw means NO damage and no effects — unless the spell's
 * description says the target still takes half ("half as much on a success", "half on
 * success", "full or half damage", …). Those spells carry `halfDamageOnSave: true`; the
 * server reads the flag when it resolves a cast onto a target (CharacterService.cast).
 *
 * Same shape as tag-spells.ts: derived from the description text once, written into
 * src/data/spells-*.json, reviewable in the diff, idempotent on re-run (the flag is removed
 * again from spells the rules no longer match). Hand corrections go in MANUAL below and
 * always win over the text rules.
 *
 *   npm run flag-half-on-save          # write flags
 *   npm run flag-half-on-save -- --dry # report only, touch nothing
 */
import * as fs from 'fs';
import * as path from 'path';
import { fileURLToPath } from 'url';

const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url));
const DATA_DIR = path.resolve(SCRIPT_DIR, '..', 'src', 'data');
const DRY_RUN = process.argv.includes('--dry');

/**
 * Spells the text rules get wrong, corrected by hand. Key = spell id, value = whether a
 * successful save still deals half damage. Add here rather than editing the JSON, so a
 * re-run doesn't silently undo the fix.
 */
const MANUAL: Record<string, boolean> = {};

/**
 * "Half" tied to a SUCCESSFUL save, inside one sentence. Deliberately narrow — "takes half
 * damage from physical sources" (an ethereal buff) or "half mana" must not match.
 */
const HALF_ON_SAVE: RegExp[] = [
  /\bhalf\b[^.;]{0,60}\bon (?:a )?success/i, // "half as much on a success", "Half damage on success"
  /\bon (?:a )?success\b[^.;]{0,60}\bhalf\b/i, // "On a success they only take half damage", "on success half"
  /\bfull or half damage\b/i, // "DEX save, full or half damage"
  /\bsucceed[^.;]{0,60}\bhalf\b/i, // "if they succeed ... take half"
  /\bhalf\b[^.;]{0,60}\b(?:who|that|if they|if it|when they) (?:succeed|save)/i, // "half as much to those who succeed"
];

interface Spell {
  id: string;
  name: string;
  description: string;
  saveStat?: string | null;
  attackType?: string | null;
  damage?: unknown;
  halfDamageOnSave?: boolean;
  [k: string]: unknown;
}

function matchFor(spell: Spell): RegExpMatchArray | null {
  for (const re of HALF_ON_SAVE) {
    const m = spell.description?.match(re);
    if (m) return m;
  }
  return null;
}

function halfOnSave(spell: Spell): boolean {
  if (spell.id in MANUAL) return MANUAL[spell.id];
  return matchFor(spell) !== null;
}

/** Rebuild the object so the flag sits right after saveStat (or attackType) instead of trailing. */
function withFlag(spell: Spell, flag: boolean): Spell {
  const out: Spell = {} as Spell;
  const anchor = 'saveStat' in spell ? 'saveStat' : 'attackType';
  for (const [k, v] of Object.entries(spell)) {
    if (k === 'halfDamageOnSave') continue;
    out[k] = v;
    if (k === anchor && flag) out.halfDamageOnSave = true;
  }
  if (flag && !('halfDamageOnSave' in out)) out.halfDamageOnSave = true;
  return out;
}

// ── Run ──

const files = fs.readdirSync(DATA_DIR).filter((f) => /^spells-.*\.json$/.test(f)).sort();
const flagged: string[] = [];
const review: string[] = [];
let total = 0;
let saveSpells = 0;
let changed = 0;

for (const file of files) {
  const filepath = path.join(DATA_DIR, file);
  const spells: Spell[] = JSON.parse(fs.readFileSync(filepath, 'utf-8'));
  let fileChanged = false;

  const next = spells.map((spell) => {
    total++;
    if (spell.saveStat) saveSpells++;
    const flag = halfOnSave(spell);
    const m = matchFor(spell);
    if (flag) {
      const why = spell.id in MANUAL ? 'manual' : `"…${m![0].slice(0, 70)}…"`;
      flagged.push(`${spell.id.padEnd(36)} ${why}`);
    } else if (spell.saveStat && spell.damage && /\bhalf\b/i.test(spell.description ?? '')) {
      // A save spell that deals damage and mentions "half" somewhere the rules didn't
      // recognise — worth a human look; add to MANUAL if it should be flagged.
      const i = spell.description.search(/\bhalf\b/i);
      review.push(`${spell.id.padEnd(36)} "…${spell.description.slice(Math.max(0, i - 50), i + 40)}…"`);
    }
    if (Boolean(spell.halfDamageOnSave) !== flag) {
      fileChanged = true;
      changed++;
    }
    return withFlag(spell, flag);
  });

  if (fileChanged && !DRY_RUN) {
    fs.writeFileSync(filepath, JSON.stringify(next, null, 2) + '\n', 'utf-8');
  }
}

console.log(`\n${DRY_RUN ? '[dry run] ' : ''}${total} spells, ${saveSpells} with a save; ${flagged.length} flagged halfDamageOnSave (${changed} changed)\n`);
console.log('Flagged (id — matched text):');
for (const line of flagged) console.log(`  ${line}`);
if (review.length) {
  console.log(`\nNot flagged but mention "half" (save + damage spells) — review, add to MANUAL if wrong:`);
  for (const line of review) console.log(`  ${line}`);
}
