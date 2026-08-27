/**
 * Export every spell with its tags as one JSON array — ALL 817 spells, whatever their tags.
 *
 *   npm run export-spell-tags                 → writes ./spell-tags.json
 *   npm run export-spell-tags -- out/x.json   → writes to the given path
 *   npm run export-spell-tags -- --stdout     → prints the JSON instead
 *
 * Each entry: { id, name, classId, level, tags }. Sorted by classId, level, name.
 * Read-only: never touches src/data. Re-run after `npm run tag-spells` to refresh.
 */
import * as fs from 'fs';
import * as path from 'path';
import { fileURLToPath } from 'url';

const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(SCRIPT_DIR, '..');
const DATA_DIR = path.join(ROOT, 'src', 'data');

interface Spell {
  id: string;
  name: string;
  classId: string;
  level: number;
  tags?: string[];
}

interface Row {
  id: string;
  name: string;
  classId: string;
  level: number;
  tags: string[];
}

const rows: Row[] = [];
for (const file of fs.readdirSync(DATA_DIR).filter((f) => /^spells-.*\.json$/.test(f)).sort()) {
  const spells = JSON.parse(fs.readFileSync(path.join(DATA_DIR, file), 'utf-8')) as Spell[];
  for (const s of spells) {
    rows.push({ id: s.id, name: s.name, classId: s.classId, level: s.level, tags: s.tags ?? [] });
  }
}

rows.sort(
  (a, b) => a.classId.localeCompare(b.classId) || a.level - b.level || a.name.localeCompare(b.name),
);

const json = JSON.stringify(rows, null, 2) + '\n';
const arg = process.argv[2];

if (arg === '--stdout') {
  process.stdout.write(json);
} else {
  const outPath = path.resolve(ROOT, arg ?? 'spell-tags.json');
  fs.mkdirSync(path.dirname(outPath), { recursive: true });
  fs.writeFileSync(outPath, json, 'utf-8');
  const untagged = rows.filter((r) => r.tags.length === 0).length;
  const bareUtility = rows.filter((r) => r.tags.length === 1 && r.tags[0] === 'utility').length;
  console.log(
    `Wrote ${rows.length} spells → ${path.relative(ROOT, outPath)} ` +
      `(${bareUtility} bare utility, ${untagged} with no tags)`,
  );
}
