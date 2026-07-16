import { useCharacterStore } from './application/characterStore';
import { EntryView } from './presentation/EntryView';
import { CreateView } from './presentation/CreateView';
import { RosterView } from './presentation/RosterView';
import { DeckEditor } from './presentation/DeckEditor';
import { Sheet } from './presentation/Sheet';

export function App() {
  const view = useCharacterStore((s) => s.view);
  const serverOffline = useCharacterStore((s) => s.serverOffline);
  return (
    <main className="app">
      {serverOffline && (
        <div className="offline-banner">
          Server unreachable — the sheet is read-only until it returns.
        </div>
      )}
      {view === 'entry' && <EntryView />}
      {view === 'create' && <CreateView />}
      {view === 'roster' && <RosterView />}
      {view === 'deck' && <DeckEditor />}
      {view === 'sheet' && <Sheet />}
    </main>
  );
}
