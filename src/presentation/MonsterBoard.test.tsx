/**
 * Component smoke test (Story 6.1): the GM's monster board renders what the store holds
 * and wires its controls to the store actions. The store's loaders are stubbed so the
 * board's mount effects don't reach the network.
 */
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { MonsterView } from '../platform/types';
import { useCharacterStore } from '../application/characterStore';
import { MonsterBoard } from './MonsterBoard';

afterEach(cleanup);

function goblin(id: number, name: string, hp: number, status: MonsterView['status'] = 'ALIVE'): MonsterView {
  return {
    id, combatantId: `monster:${id}`, templateId: 1, name, level: 4,
    hp: { current: hp, max: 40, temp: 0 }, ac: 13, pa: 2, ma: 0, speed: 30, might: 3,
    stats: { STR: 10, DEX: 14, CON: 10, INT: 10, WIS: 10, WILL: 8, CHA: 10 },
    modifiers: { STR: 0, DEX: 2, CON: 0, INT: 0, WIS: 0, WILL: -1, CHA: 0 },
    status, stackThreshold: 2, savingThrowProficiencies: [], damageTaken: {},
    activeEffects: [{ id: 'burning', name: 'Burning', stacks: 2, value: null, rounds: null, active: true, threshold: null }],
    conditions: ['injured'], abilitiesText: 'Bites.',
  };
}

const endMonsterTurn = vi.fn(async () => undefined);
const doTargetedDamage = vi.fn(async () => undefined);
const removeMonster = vi.fn(async () => undefined);

beforeEach(() => {
  useCharacterStore.setState({
    roomName: 'test-room',
    roster: [],
    monsters: [goblin(1, 'Goblin', 20), goblin(2, 'Goblin 2', 40, 'DEAD')],
    monsterTemplates: [],
    encounter: {
      active: true, round: 1, currentPlayerId: 'monster:1', turnStarted: true, xpPool: 0,
      entries: [{ playerId: 'monster:1', name: 'Goblin', initiative: 12, status: 'ALIVE', surprised: false, combatantType: 'MONSTER', hp: 20, maxHp: 40, prepared: [] }],
    },
    lastResolution: null,
    lastResolutionTarget: null,
    acting: false,
    loadMonsters: vi.fn(async () => undefined),
    loadMonsterTemplates: vi.fn(async () => undefined),
    endMonsterTurn,
    doTargetedDamage,
    removeMonster,
  });
});

describe('MonsterBoard', () => {
  it('renders one card per monster with vitals, effect chips and conditions', () => {
    render(<MonsterBoard />);

    expect(screen.getByText('2 in the fight')).toBeTruthy();
    expect(screen.getByText('Goblin')).toBeTruthy();
    expect(screen.getByText('Goblin 2')).toBeTruthy();
    // effect chips (the quick-effect <select> also lists "Burning" — hence the class query)
    const chips = [...document.querySelectorAll('.monster-chip')].map((c) => c.textContent);
    expect(chips).toHaveLength(2);
    expect(chips[0]).toContain('Burning ×2');
    expect(screen.getAllByText('injured')).toHaveLength(2);
    // the dead one is greyed, the current one is highlighted
    expect(document.querySelectorAll('.monster-card--dead')).toHaveLength(1);
    expect(document.querySelectorAll('.monster-card--current')).toHaveLength(1);
  });

  it('only the monster whose turn it is gets an End turn button, wired to the store', () => {
    render(<MonsterBoard />);

    const endButtons = screen.getAllByRole('button', { name: 'End turn' });
    expect(endButtons).toHaveLength(1);
    fireEvent.click(endButtons[0]);
    expect(endMonsterTurn).toHaveBeenCalledWith('monster:1');
  });

  it('the quick damage form targets that card’s monster', () => {
    render(<MonsterBoard />);

    const damageButtons = screen.getAllByRole('button', { name: 'Damage' });
    fireEvent.click(damageButtons[0]);
    expect(doTargetedDamage).toHaveBeenCalledWith('monster:1', 10, 'SLASHING');
    // dead monsters can't be hit again
    expect((damageButtons[1] as HTMLButtonElement).disabled).toBe(true);
  });
});
