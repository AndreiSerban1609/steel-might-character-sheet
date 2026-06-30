import { useCharacterStore } from './application/characterStore';
import { EntryView } from './presentation/EntryView';
import { CreateView } from './presentation/CreateView';
import { RosterView } from './presentation/RosterView';
import { DeckEditor } from './presentation/DeckEditor';
import { Sheet } from './presentation/Sheet';

export function App() {
  const view = useCharacterStore((s) => s.view);
  return (
    <main className="app">
      {view === 'entry' && <EntryView />}
      {view === 'create' && <CreateView />}
      {view === 'roster' && <RosterView />}
      {view === 'deck' && <DeckEditor />}
      {view === 'sheet' && <Sheet />}
    </main>
  );
}
