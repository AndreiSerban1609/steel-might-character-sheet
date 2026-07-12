import { useState } from 'react';
import { useCharacterStore } from '../application/characterStore';
import type { Viewport } from '../platform/metadataGateway';
import { casterTypeOf } from '../domain/spellCatalog';
import { StatsPanel } from './StatsPanel';
import { CombatPanel } from './CombatPanel';
import { SkillsPanel } from './SkillsPanel';
import { PlayerDeckPanel } from './PlayerDeckPanel';
import { InventoryPanel } from './InventoryPanel';
import { BioPanel } from './BioPanel';
import { SpellbookPanel } from './SpellbookPanel';

type Tab = 'stats' | 'combat' | 'spells' | 'skills' | 'inventory' | 'bio' | 'deck';

const TABS: { id: Tab; label: string }[] = [
  { id: 'stats', label: 'Stats' },
  { id: 'combat', label: 'Combat' },
  { id: 'spells', label: 'Spells' },
  { id: 'skills', label: 'Skills' },
  { id: 'inventory', label: 'Inventory' },
  { id: 'bio', label: 'Bio' },
  { id: 'deck', label: 'Deck' },
];

/** Which snapshot slice each tab mirrors to OBR metadata. */
const VIEWPORT_BY_TAB: Record<Tab, Viewport> = {
  stats: 'combat',
  combat: 'combat',
  spells: 'spellbook',
  skills: 'combat',
  inventory: 'inventory',
  bio: 'bio',
  deck: 'combat',
};

export function Sheet() {
  const back = useCharacterStore((s) => s.back);
  const role = useCharacterStore((s) => s.role);
  const classId = useCharacterStore((s) => s.snapshot?.classId);
  const setActiveViewport = useCharacterStore((s) => s.setActiveViewport);
  const [rawTab, setTab] = useState<Tab>('stats');

  // Non-casters have no spellbook — hide the tab (their abilities arrive with
  // the use-ability round; resource spend/gain lives in the Combat tab).
  const tabs = TABS.filter((t) => t.id !== 'spells' || !classId || casterTypeOf(classId) !== 'none');
  const tab: Tab = tabs.some((t) => t.id === rawTab) ? rawTab : 'stats';

  function selectTab(t: Tab) {
    setTab(t);
    setActiveViewport(VIEWPORT_BY_TAB[t]);
  }

  return (
    <section className="sheet">
      <div className="sheet-topbar">
        <button className="btn btn--ghost" onClick={back}>
          {role === 'gm' ? '← Roster' : '← Exit'}
        </button>
        <div className="tabs">
          {tabs.map((t) => (
            <button
              key={t.id}
              className={tab === t.id ? 'tab tab--active' : 'tab'}
              onClick={() => selectTab(t.id)}
            >
              {t.label}
            </button>
          ))}
        </div>
        <span className="sheet-topbar-spacer" />
      </div>

      {tab === 'stats' && <StatsPanel />}
      {tab === 'combat' && <CombatPanel />}
      {tab === 'spells' && <SpellbookPanel />}
      {tab === 'skills' && <SkillsPanel />}
      {tab === 'inventory' && <InventoryPanel />}
      {tab === 'bio' && <BioPanel />}
      {tab === 'deck' && <PlayerDeckPanel />}
    </section>
  );
}
