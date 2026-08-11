import { useState } from 'react';
import { useCharacterStore } from '../application/characterStore';
import type { Viewport } from '../platform/metadataGateway';
import { casterTypeOf } from '../domain/spellCatalog';
import { AbilitiesPanel } from './AbilitiesPanel';
import { StatsPanel } from './StatsPanel';
import { CombatPanel } from './CombatPanel';
import { SkillsPanel } from './SkillsPanel';
import { PlayerDeckPanel } from './PlayerDeckPanel';
import { InventoryPanel } from './InventoryPanel';
import { BioPanel } from './BioPanel';
import { SpellbookPanel } from './SpellbookPanel';

type Tab = 'stats' | 'combat' | 'spells' | 'abilities' | 'skills' | 'inventory' | 'bio' | 'deck';

const TABS: { id: Tab; label: string }[] = [
  { id: 'stats', label: 'Stats' },
  { id: 'combat', label: 'Combat' },
  { id: 'spells', label: 'Spells' },
  { id: 'abilities', label: 'Abilities' },
  { id: 'skills', label: 'Skills' },
  { id: 'inventory', label: 'Inventory' },
  { id: 'bio', label: 'Bio' },
  { id: 'deck', label: 'Skill Deck' },
];

/** Which snapshot slice each tab mirrors to OBR metadata. */
const VIEWPORT_BY_TAB: Record<Tab, Viewport> = {
  stats: 'combat',
  combat: 'combat',
  spells: 'spellbook',
  abilities: 'combat',
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

  // Non-casters have no spellbook. Abilities shows for EVERY class: even those
  // without extracted data get the free-text custom section (2026-07-20 ruling).
  const tabs = TABS.filter((t) => {
    if (t.id === 'spells') return !classId || casterTypeOf(classId) !== 'none';
    return true;
  });
  const tab: Tab = tabs.some((t) => t.id === rawTab) ? rawTab : 'stats';

  function selectTab(t: Tab) {
    setTab(t);
    setActiveViewport(VIEWPORT_BY_TAB[t]);
  }

  return (
    <section className="sheet">
      <div className="sheet-topbar">
        {role === 'gm' && (
          <button className="btn btn--ghost" onClick={back}>
            ← Roster
          </button>
        )}
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
      {tab === 'abilities' && <AbilitiesPanel />}
      {tab === 'skills' && <SkillsPanel />}
      {tab === 'inventory' && <InventoryPanel />}
      {tab === 'bio' && <BioPanel />}
      {tab === 'deck' && <PlayerDeckPanel />}
    </section>
  );
}
