/**
 * Component smoke test (Story 6.1): the resolution log renders a resolve-onto-target
 * payload the way the table reads it — hit/miss vs the target's AC, the save line, and
 * where the target ended up. Runs in jsdom (see vitest.config.ts).
 */
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { ResolutionResult } from '../platform/types';
import { useCharacterStore } from '../application/characterStore';

// The die animation runs on timers; the smoke test cares about the words, not the spin.
vi.mock('./DiceRoll', () => ({ DiceRoll: ({ result }: { result: number }) => <span>d20:{result}</span> }));

import { ResolutionLog } from './ResolutionLog';

afterEach(cleanup);

function resolution(payload: NonNullable<ResolutionResult['payload']>): ResolutionResult {
  return {
    steps: [{ rule: 'Goblin:armor', note: 'PA 2 reduces slashing damage', valueBefore: 20, valueAfter: 18 }],
    effectsTriggered: [],
    payload,
  };
}

describe('ResolutionLog with a named target', () => {
  it('shows the hit against the real AC and the target’s HP afterwards', () => {
    render(
      <ResolutionLog
        resolution={resolution({
          weapon: { id: 'greataxe', name: 'Greataxe' },
          attackRoll: { roll: 10, bonus: 6, total: 16, targetAC: 13, hit: true },
          damage: { rolls: [10], flat: 7, modifier: 3, total: 20 },
          damageType: 'slashing',
          target: { combatantId: 'monster:1', name: 'Goblin', hpAfter: 22, hpMax: 40, status: 'ALIVE' },
        })}
        targetName="Goblin"
        onClose={() => undefined}
      />,
    );

    expect(screen.getByText(/Greataxe vs AC 13/)).toBeTruthy();
    expect(screen.getByText('HIT')).toBeTruthy();
    expect(screen.getByText('22/40 HP')).toBeTruthy();
    expect(screen.getByText('PA 2 reduces slashing damage')).toBeTruthy();
  });

  it('shows a miss and leaves the target unharmed', () => {
    render(
      <ResolutionLog
        resolution={resolution({
          attackRoll: { roll: 3, bonus: 6, total: 9, targetAC: 13, hit: false },
          target: { combatantId: 'monster:1', name: 'Goblin' },
        })}
        onClose={() => undefined}
      />,
    );

    expect(screen.getByText('MISS')).toBeTruthy();
    expect(screen.getByText(/unharmed/)).toBeTruthy();
  });

  it('shows the save outcome and resolves a monster id to its name', () => {
    useCharacterStore.setState({
      monsters: [
        {
          id: 1, combatantId: 'monster:1', templateId: 1, name: 'Goblin', level: 4,
          hp: { current: 23, max: 40, temp: 0 }, ac: 13, pa: 2, ma: 0, speed: 30, might: 3,
          stats: { STR: 10, DEX: 30, CON: 10, INT: 10, WIS: 10, WILL: 8, CHA: 10 },
          modifiers: { STR: 0, DEX: 10, CON: 0, INT: 0, WIS: 0, WILL: -1, CHA: 0 },
          status: 'ALIVE', stackThreshold: 2, savingThrowProficiencies: [], damageTaken: {},
          activeEffects: [], conditions: [], abilitiesText: null,
        },
      ],
    });
    render(
      <ResolutionLog
        resolution={resolution({
          saveDC: 14,
          save: { stat: 'DEX', dc: 14, success: true },
          damage: { rolls: [8, 8, 8], flat: 8, modifier: 3, total: 35 },
          effectsAppliedTo: 'monster:1',
        })}
        onClose={() => undefined}
      />,
    );

    expect(screen.getByText(/DEX save vs DC 14/)).toBeTruthy();
    expect(screen.getByText(/saved \(half damage, no effects\)/)).toBeTruthy();
    // effectsAppliedTo carries the raw combatant id — the log shows the monster's name.
    expect(screen.getByText('Goblin')).toBeTruthy();
  });
});
