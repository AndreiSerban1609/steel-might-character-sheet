import { useState } from 'react';
import { useCharacterStore } from '../application/characterStore';
import { StatsPanel } from './StatsPanel';
import { SkillsPanel } from './SkillsPanel';
import { PlayerDeckPanel } from './PlayerDeckPanel';
import { InventoryPanel } from './InventoryPanel';
import { BioPanel } from './BioPanel';

type Tab = 'stats' | 'skills' | 'inventory' | 'bio' | 'deck';

const TABS: { id: Tab; label: string }[] = [
  { id: 'stats', label: 'Stats' },
  { id: 'skills', label: 'Skills' },
  { id: 'inventory', label: 'Inventory' },
  { id: 'bio', label: 'Bio' },
  { id: 'deck', label: 'Deck' },
];

export function Sheet() {
  const back = useCharacterStore((s) => s.back);
  const role = useCharacterStore((s) => s.role);
  const [tab, setTab] = useState<Tab>('stats');

  return (
    <section className="sheet">
      <div className="sheet-topbar">
        <button className="btn btn--ghost" onClick={back}>
          {role === 'gm' ? '← Roster' : '← Exit'}
        </button>
        <div className="tabs">
          {TABS.map((t) => (
            <button
              key={t.id}
              className={tab === t.id ? 'tab tab--active' : 'tab'}
              onClick={() => setTab(t.id)}
            >
              {t.label}
            </button>
          ))}
        </div>
        <span className="sheet-topbar-spacer" />
      </div>

      {tab === 'stats' && <StatsPanel />}
      {tab === 'skills' && <SkillsPanel />}
      {tab === 'inventory' && <InventoryPanel />}
      {tab === 'bio' && <BioPanel />}
      {tab === 'deck' && <PlayerDeckPanel />}
    </section>
  );
}
