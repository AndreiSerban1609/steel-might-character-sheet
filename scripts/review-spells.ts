/**
 * Build a human-readable spell ledger for asserting tags: every spell (all 817), its full
 * description, costs/mechanics, and tag chips — with search, class/level/tag filters and a
 * "bare utility only" switch. Self-contained HTML, no network (fonts aside).
 *
 *   npm run review-spells                → writes ./spell-review.html
 *   npm run review-spells -- out/x.html  → custom path
 *
 * Read-only: never touches src/data. Re-run after `npm run tag-spells`. Tag corrections
 * still belong in MANUAL_TAGS (scripts/tag-spells.ts), keyed by the spell id shown per row.
 */
import * as fs from 'fs';
import * as path from 'path';
import { fileURLToPath } from 'url';

const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(SCRIPT_DIR, '..');
const DATA_DIR = path.join(ROOT, 'src', 'data');

interface Formula {
  modMultiplier?: number | null;
  flat?: number | null;
  dice?: string | null;
}
interface Spell {
  id: string;
  name: string;
  classId: string;
  level: number;
  apCost: number | null;
  manaCost: number | null;
  range: string | null;
  duration: string | null;
  concentration: boolean;
  channeling: boolean;
  damageType: string | null;
  attackType: string | null;
  saveStat: string | null;
  damage: Formula | null;
  healing: Formula | null;
  effects?: string[];
  description: string;
  tags?: string[];
}
interface PathDef {
  id: string;
  name: string;
  classes: string[];
}

const ROLE_TAGS = ['damage', 'healing', 'buff', 'debuff', 'control', 'utility', 'summon', 'movement', 'defense', 'ritual', 'aoe'];
const ELEMENT_TAGS = ['pure', 'spectral', 'light', 'shadow', 'fire', 'ice', 'lightning', 'poison', 'thunder', 'psychic', 'force'];

const titleCase = (id: string) =>
  id
    .split('-')
    .map((w) => w[0].toUpperCase() + w.slice(1))
    .join(' ');

const paths = JSON.parse(fs.readFileSync(path.join(DATA_DIR, 'classes.json'), 'utf-8')) as PathDef[];
const classInfo: Record<string, { name: string; path: string }> = {};
for (const p of paths) for (const c of p.classes) classInfo[c] = { name: titleCase(c), path: p.name };

const spells: Spell[] = [];
for (const file of fs.readdirSync(DATA_DIR).filter((f) => /^spells-.*\.json$/.test(f)).sort()) {
  spells.push(...(JSON.parse(fs.readFileSync(path.join(DATA_DIR, file), 'utf-8')) as Spell[]));
}
for (const s of spells) if (!classInfo[s.classId]) classInfo[s.classId] = { name: titleCase(s.classId), path: '—' };
spells.sort(
  (a, b) =>
    classInfo[a.classId].path.localeCompare(classInfo[b.classId].path) ||
    a.classId.localeCompare(b.classId) ||
    a.level - b.level ||
    a.name.localeCompare(b.name),
);

const formula = (f: Formula | null | undefined): string | null => {
  if (!f) return null;
  const parts: string[] = [];
  if (f.dice) parts.push(f.dice);
  if (f.flat) parts.push(String(f.flat));
  if (f.modMultiplier) parts.push(f.modMultiplier === 1 ? 'mod' : `${f.modMultiplier}×mod`);
  return parts.length ? parts.join(' + ') : null;
};
const attackLabel: Record<string, string> = {
  rangedSpellAttack: 'ranged spell attack',
  meleeSpellAttack: 'melee spell attack',
  rangedWeaponAttack: 'ranged weapon attack',
  meleeWeaponAttack: 'melee weapon attack',
};

const rows = spells.map((s) => {
  const mech: string[] = [];
  const d = formula(s.damage);
  if (d) mech.push(`${d} ${s.damageType ?? ''}`.trim());
  else if (s.damageType) mech.push(s.damageType);
  const h = formula(s.healing);
  if (h) mech.push(`heals ${h}`);
  if (s.attackType) mech.push(attackLabel[s.attackType] ?? s.attackType);
  if (s.saveStat) mech.push(`${s.saveStat.toUpperCase()} save`);
  if (s.concentration) mech.push('concentration');
  if (s.channeling) mech.push('channeling');
  if (s.effects?.length) mech.push(`effects: ${s.effects.join(', ')}`);
  return {
    id: s.id,
    name: s.name,
    classId: s.classId,
    cls: classInfo[s.classId].name,
    path: classInfo[s.classId].path,
    level: s.level,
    ap: s.apCost,
    mana: s.manaCost,
    range: s.range,
    duration: s.duration,
    mech: mech.join(' · '),
    desc: s.description,
    tags: s.tags ?? [],
  };
});
const levels = [...new Set(rows.map((r) => r.level))].sort((a, b) => a - b);
const classOptions = paths
  .map(
    (p) =>
      `<optgroup label="${p.name}">${p.classes
        .filter((c) => rows.some((r) => r.classId === c))
        .map((c) => `<option value="${c}">${classInfo[c].name}</option>`)
        .join('')}</optgroup>`,
  )
  .join('');
const bareCount = rows.filter((r) => r.tags.length === 1 && r.tags[0] === 'utility').length;
const dataJson = JSON.stringify(rows).replace(/<\//g, '<\\/');

const THEME_DARK = `
  --bg: #14110d; --panel: #1e1a13; --panel-2: #262118; --border: #4a3f2a; --border-soft: #2f2819;
  --gold: #d4af37; --gold-soft: #b08d2e; --text: #e8e0cf; --dim: #a89a7c; --mute: #7a6e57;
  --mark: #e0713f; --chip-bg: rgba(212,175,55,0.08);
  --fire:#f0a060; --ice:#60c0f0; --lightning:#f5e088; --shadow:#c080f0; --light:#f5e6a0; --poison:#80d0a0;
  --psychic:#f0a0be; --thunder:#b8d0e0; --force:#80a0f0; --pure:#c0cde8; --spectral:#b8d0e0;`;

const html = `<title>Spell Tag Ledger</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Cinzel:wght@500;600;700&family=Crimson+Text:ital,wght@0,400;0,600;1,400&display=swap">
<style>
:root {
  --bg: #f3ecdc; --panel: #faf6ec; --panel-2: #efe6d2; --border: #cdbf9c; --border-soft: #e2d7bc;
  --gold: #8a6a1c; --gold-soft: #b8952f; --text: #2a2318; --dim: #6e6250; --mute: #9a8d74;
  --mark: #b1471f; --chip-bg: rgba(138,106,28,0.08);
  --fire:#b8471a; --ice:#1f7fb0; --lightning:#9a7a08; --shadow:#6d2fa3; --light:#a98515; --poison:#2f8a4f;
  --psychic:#a53b62; --thunder:#5a7788; --force:#2f4fb5; --pure:#5b6b95; --spectral:#5b8090;
}
@media (prefers-color-scheme: dark) { :root:not([data-theme="light"]) {${THEME_DARK}
} }
:root[data-theme="dark"] {${THEME_DARK}
}
* { box-sizing: border-box; }
body { margin: 0; background: var(--bg); color: var(--text); font-family: 'Crimson Text', Georgia, serif; font-size: 16px; line-height: 1.45; }
.bar { position: sticky; top: 0; z-index: 10; background: var(--panel); border-bottom: 1px solid var(--border); padding: 12px 20px 10px; display: flex; flex-direction: column; gap: 10px; }
.bar-head { display: flex; align-items: baseline; gap: 14px; flex-wrap: wrap; }
h1 { font-family: 'Cinzel', 'Palatino', serif; font-weight: 600; font-size: 20px; letter-spacing: 1px; margin: 0; color: var(--gold); }
.count { color: var(--dim); font-variant-numeric: tabular-nums; }
.count b { color: var(--text); font-weight: 600; }
.hint { color: var(--mute); font-style: italic; margin-left: auto; font-size: 14px; }
.controls { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }
.controls input[type=search], .controls select { font: inherit; font-size: 15px; color: var(--text); background: var(--bg); border: 1px solid var(--border); border-radius: 4px; padding: 5px 9px; }
.controls input[type=search] { flex: 1 1 260px; min-width: 200px; }
.controls input:focus-visible, .controls select:focus-visible, .chip:focus-visible, button:focus-visible { outline: 2px solid var(--gold); outline-offset: 1px; }
.switch { display: inline-flex; align-items: center; gap: 6px; color: var(--dim); cursor: pointer; white-space: nowrap; }
.switch input { accent-color: var(--gold); }
button.clear { font: inherit; font-size: 14px; background: none; border: 1px solid var(--border); color: var(--dim); border-radius: 4px; padding: 4px 10px; cursor: pointer; }
button.clear:hover { color: var(--text); border-color: var(--gold-soft); }
.tagbar { display: flex; gap: 6px; flex-wrap: wrap; align-items: center; }
.tagbar .sep { width: 1px; height: 18px; background: var(--border); margin: 0 4px; }
.chip { --c: var(--gold); display: inline-flex; align-items: center; gap: 4px; font-family: 'Cinzel', 'Palatino', serif; font-size: 10.5px; letter-spacing: 0.8px; text-transform: uppercase; color: var(--c); background: var(--chip-bg); border: 1px solid color-mix(in srgb, var(--c) 45%, transparent); border-radius: 3px; padding: 3px 7px; cursor: pointer; user-select: none; line-height: 1; }
.chip .n { color: var(--mute); font-size: 9.5px; letter-spacing: 0; }
.chip:hover { border-color: var(--c); }
.chip.on { background: var(--c); color: var(--bg); border-color: var(--c); }
.chip.on .n { color: var(--bg); opacity: 0.8; }
.chip[data-tag=fire]{--c:var(--fire)} .chip[data-tag=ice]{--c:var(--ice)} .chip[data-tag=lightning]{--c:var(--lightning)}
.chip[data-tag=shadow]{--c:var(--shadow)} .chip[data-tag=light]{--c:var(--light)} .chip[data-tag=poison]{--c:var(--poison)}
.chip[data-tag=psychic]{--c:var(--psychic)} .chip[data-tag=thunder]{--c:var(--thunder)} .chip[data-tag=force]{--c:var(--force)}
.chip[data-tag=pure]{--c:var(--pure)} .chip[data-tag=spectral]{--c:var(--spectral)}
main { max-width: 1180px; margin: 0 auto; padding: 8px 20px 80px; }
.group-head { position: sticky; top: var(--bar-h, 132px); z-index: 5; background: var(--bg); display: flex; align-items: baseline; gap: 12px; margin: 22px 0 6px; padding: 6px 0; border-bottom: 1px solid var(--border); }
.group-head h2 { font-family: 'Cinzel', 'Palatino', serif; font-size: 17px; font-weight: 600; letter-spacing: 1px; margin: 0; color: var(--gold); }
.group-head span { color: var(--dim); font-size: 14px; font-variant-numeric: tabular-nums; }
.spell { display: grid; grid-template-columns: 220px 1fr; gap: 6px 22px; padding: 12px 0 12px 12px; border-bottom: 1px solid var(--border-soft); border-left: 3px solid transparent; margin-left: -15px; }
.spell.bare { border-left-color: var(--mark); background: linear-gradient(90deg, color-mix(in srgb, var(--mark) 7%, transparent), transparent 40%); }
.name { font-family: 'Cinzel', 'Palatino', serif; font-size: 15px; font-weight: 600; margin: 0; line-height: 1.25; }
.sub { color: var(--dim); font-size: 14px; margin-top: 2px; }
.stats { display: grid; grid-template-columns: auto auto; gap: 1px 10px; margin: 6px 0 0; font-size: 13.5px; font-variant-numeric: tabular-nums; }
.stats dt { color: var(--mute); text-transform: uppercase; font-size: 10px; letter-spacing: 0.8px; font-family: 'Cinzel', serif; }
.stats dd { margin: 0; color: var(--text); }
.stats div { display: contents; }
.id { font-family: ui-monospace, Consolas, monospace; font-size: 11.5px; color: var(--mute); margin-top: 6px; cursor: copy; word-break: break-all; }
.id:hover { color: var(--gold); }
.mech { color: var(--dim); font-size: 14px; font-style: italic; margin-bottom: 4px; }
.desc { margin: 0; max-width: 72ch; white-space: pre-line; }
.tags { display: flex; gap: 5px; flex-wrap: wrap; margin-top: 8px; }
.empty { color: var(--dim); font-style: italic; padding: 40px 0; text-align: center; }
.toast { position: fixed; bottom: 18px; left: 50%; transform: translateX(-50%); background: var(--gold); color: var(--bg); font-family: 'Cinzel', serif; font-size: 12px; letter-spacing: 1px; padding: 6px 14px; border-radius: 4px; opacity: 0; transition: opacity 0.2s; pointer-events: none; }
.toast.show { opacity: 1; }
@media (max-width: 720px) { .spell { grid-template-columns: 1fr; } .stats { grid-template-columns: auto auto auto auto; } }
@media (prefers-reduced-motion: reduce) { .toast { transition: none; } }
</style>

<header class="bar" id="bar">
  <div class="bar-head">
    <h1>Spell Tag Ledger</h1>
    <span class="count"><b id="shown">0</b> of ${rows.length} spells</span>
    <span class="hint">Marked rows carry only a bare <em>utility</em> tag (${bareCount}). Click a tag to filter; click an id to copy it.</span>
  </div>
  <div class="controls">
    <input id="q" type="search" placeholder="Search name or description" autocomplete="off">
    <select id="cls"><option value="">All classes</option>${classOptions}</select>
    <select id="lvl"><option value="">All levels</option>${levels.map((l) => `<option value="${l}">Level ${l}</option>`).join('')}</select>
    <label class="switch"><input type="checkbox" id="bare"> Bare utility only</label>
    <button class="clear" id="clear" type="button">Clear filters</button>
  </div>
  <div class="tagbar" id="tagbar"></div>
</header>
<main id="ledger"></main>
<div class="toast" id="toast">Copied</div>

<script>
const SPELLS = ${dataJson};
const ROLE_TAGS = ${JSON.stringify(ROLE_TAGS)};
const ELEMENT_TAGS = ${JSON.stringify(ELEMENT_TAGS)};
const esc = (s) => String(s ?? '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
const state = { q: '', cls: '', lvl: '', bare: false, tags: new Set() };
const $ = (id) => document.getElementById(id);

const tagCounts = {};
for (const s of SPELLS) for (const t of s.tags) tagCounts[t] = (tagCounts[t] || 0) + 1;
const chipHtml = (t, withCount) =>
  '<button type="button" class="chip' + (state.tags.has(t) ? ' on' : '') + '" data-tag="' + t + '">' + t +
  (withCount ? ' <span class="n">' + (tagCounts[t] || 0) + '</span>' : '') + '</button>';

function renderTagbar() {
  $('tagbar').innerHTML = ROLE_TAGS.map((t) => chipHtml(t, true)).join('') + '<span class="sep"></span>' +
    ELEMENT_TAGS.map((t) => chipHtml(t, true)).join('');
}

function matches(s) {
  if (state.cls && s.classId !== state.cls) return false;
  if (state.lvl && String(s.level) !== state.lvl) return false;
  if (state.bare && !(s.tags.length === 1 && s.tags[0] === 'utility')) return false;
  for (const t of state.tags) if (!s.tags.includes(t)) return false;
  if (state.q) {
    const q = state.q.toLowerCase();
    if (!s.name.toLowerCase().includes(q) && !s.desc.toLowerCase().includes(q) && !s.id.includes(q)) return false;
  }
  return true;
}

function rowHtml(s) {
  const bare = s.tags.length === 1 && s.tags[0] === 'utility';
  const stat = (k, v) => '<div><dt>' + k + '</dt><dd>' + esc(v ?? '—') + '</dd></div>';
  return '<article class="spell' + (bare ? ' bare' : '') + '">' +
    '<div class="meta"><h3 class="name">' + esc(s.name) + '</h3>' +
    '<div class="sub">' + esc(s.cls) + ' · Level ' + s.level + '</div>' +
    '<dl class="stats">' + stat('AP', s.ap) + stat('Mana', s.mana) + stat('Range', s.range) + stat('Duration', s.duration) + '</dl>' +
    '<div class="id" title="Copy id" data-id="' + esc(s.id) + '">' + esc(s.id) + '</div></div>' +
    '<div class="body">' + (s.mech ? '<div class="mech">' + esc(s.mech) + '</div>' : '') +
    '<p class="desc">' + esc(s.desc) + '</p>' +
    '<div class="tags">' + s.tags.map((t) => chipHtml(t, false)).join('') + '</div></div></article>';
}

function render() {
  const shown = SPELLS.filter(matches);
  $('shown').textContent = shown.length;
  if (!shown.length) { $('ledger').innerHTML = '<p class="empty">No spells match these filters.</p>'; return; }
  const perClass = {};
  for (const s of shown) perClass[s.classId] = (perClass[s.classId] || 0) + 1;
  let html = '', lastClass = null;
  for (const s of shown) {
    if (s.classId !== lastClass) {
      lastClass = s.classId;
      const n = perClass[s.classId];
      html += '<div class="group-head"><h2>' + esc(s.cls) + '</h2><span>' + esc(s.path) + ' path · ' + n + (n === 1 ? ' spell' : ' spells') + '</span></div>';
    }
    html += rowHtml(s);
  }
  $('ledger').innerHTML = html;
}

function sync() { renderTagbar(); render(); }
$('q').addEventListener('input', (e) => { state.q = e.target.value.trim(); render(); });
$('cls').addEventListener('change', (e) => { state.cls = e.target.value; render(); });
$('lvl').addEventListener('change', (e) => { state.lvl = e.target.value; render(); });
$('bare').addEventListener('change', (e) => { state.bare = e.target.checked; render(); });
$('clear').addEventListener('click', () => {
  state.q = ''; state.cls = ''; state.lvl = ''; state.bare = false; state.tags.clear();
  $('q').value = ''; $('cls').value = ''; $('lvl').value = ''; $('bare').checked = false; sync();
});
document.addEventListener('click', (e) => {
  const chip = e.target.closest('.chip');
  if (chip) { const t = chip.dataset.tag; if (state.tags.has(t)) state.tags.delete(t); else state.tags.add(t); sync(); return; }
  const id = e.target.closest('.id');
  if (id && navigator.clipboard) {
    navigator.clipboard.writeText(id.dataset.id).then(() => {
      const t = $('toast'); t.classList.add('show'); setTimeout(() => t.classList.remove('show'), 1200);
    }).catch(() => {});
  }
});
const fixBarHeight = () => document.documentElement.style.setProperty('--bar-h', $('bar').offsetHeight + 'px');
new ResizeObserver(fixBarHeight).observe($('bar'));
fixBarHeight();
sync();
</script>
`;

const outPath = path.resolve(ROOT, process.argv[2] ?? 'spell-review.html');
fs.mkdirSync(path.dirname(outPath), { recursive: true });
fs.writeFileSync(outPath, html, 'utf-8');
console.log(
  `Wrote ${rows.length} spells → ${path.relative(ROOT, outPath)} (${bareCount} bare utility, ${Math.round(html.length / 1024)} kB)`,
);
