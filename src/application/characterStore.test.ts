/**
 * Store tests for the combat / targeting actions (Story 6.1). The platform seams are
 * mocked: every HTTP function the actions call, and the OBR metadata mirror. What is
 * asserted is the store's contract — which route an action takes for which target kind,
 * what state it adopts from the response, and how it refreshes the rest of the table.
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type {
  ActionResponse,
  CombatSnapshot,
  CombatantView,
  EncounterView,
  MonsterView,
  ResolutionResult,
} from '../platform/types';

vi.mock('../platform/http', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../platform/http')>();
  return {
    ...actual,
    sendDamage: vi.fn(),
    sendHeal: vi.fn(),
    applyEffect: vi.fn(),
    weaponAttack: vi.fn(),
    useAbility: vi.fn(),
    combatantDamage: vi.fn(),
    combatantHeal: vi.fn(),
    combatantApplyEffect: vi.fn(),
    combatantTurnEnd: vi.fn(),
    fetchMonsters: vi.fn(),
    fetchEncounter: vi.fn(),
    fetchCombatSnapshot: vi.fn(),
    fetchAbilities: vi.fn(),
    spawnMonsters: vi.fn(),
  };
});

vi.mock('../platform/metadataGateway', () => ({
  writeViewport: vi.fn(async () => undefined),
  clearSheetMetadata: vi.fn(async () => 0),
}));

import * as http from '../platform/http';
import { writeViewport } from '../platform/metadataGateway';
import { useCharacterStore } from './characterStore';

const mocked = <T extends (...args: never[]) => unknown>(fn: T) => fn as unknown as ReturnType<typeof vi.fn>;

// ---- fixtures (shape-minimal; the store never inspects these beyond what it adopts) ----

const resolution: ResolutionResult = {
  steps: [{ rule: 'hp-reduction', note: 'Took 10', valueBefore: 30, valueAfter: 20 }],
  effectsTriggered: [],
};

function snapshot(name: string, hp: number): CombatSnapshot {
  return { name, hp: { current: hp, max: 100, temp: 0 }, ac: 12 } as unknown as CombatSnapshot;
}

function monster(id: number, name: string, hp: number): MonsterView {
  return {
    id,
    combatantId: `monster:${id}`,
    templateId: 1,
    name,
    level: 4,
    hp: { current: hp, max: 40, temp: 0 },
    ac: 13,
    pa: 2,
    ma: 0,
    speed: 30,
    might: 3,
    stats: { STR: 10, DEX: 14, CON: 10, INT: 10, WIS: 10, WILL: 8, CHA: 10 },
    modifiers: { STR: 0, DEX: 2, CON: 0, INT: 0, WIS: 0, WILL: -1, CHA: 0 },
    status: 'ALIVE',
    stackThreshold: 2,
    savingThrowProficiencies: [],
    damageTaken: {},
    activeEffects: [],
    conditions: [],
    abilitiesText: null,
  };
}

function monsterResponse(m: MonsterView): ActionResponse<CombatantView> {
  return {
    resolution,
    snapshot: { type: 'MONSTER', combatantId: m.combatantId, name: m.name, character: null, monster: m },
  };
}

function encounter(current: string, active = true): EncounterView {
  return {
    active,
    round: 1,
    currentPlayerId: current,
    turnStarted: true, xpPool: 0,
    entries: [
      { playerId: 'p1', name: 'Alpha', initiative: 15, status: 'ALIVE', surprised: false, combatantType: 'PLAYER', hp: null, maxHp: null, prepared: [] },
      { playerId: 'monster:1', name: 'Goblin', initiative: 12, status: 'ALIVE', surprised: false, combatantType: 'MONSTER', hp: 30, maxHp: 40, prepared: [] },
    ],
  };
}

const pristine = useCharacterStore.getState();

function arrange(partial: Partial<ReturnType<typeof useCharacterStore.getState>>) {
  useCharacterStore.setState(
    {
      ...pristine,
      roomName: 'test-room',
      selectedPlayerId: 'p1',
      snapshot: snapshot('Alpha', 100),
      roster: [
        { playerId: 'p1', roomName: 'test-room', email: 'a@x', name: 'Alpha', level: 5, pathId: 'warrior', classId: 'barbarian', currentHp: 100, maxHp: 100, ac: 12 },
        { playerId: 'p2', roomName: 'test-room', email: 'b@x', name: 'Bravo', level: 5, pathId: 'musician', classId: 'bard', currentHp: 80, maxHp: 90, ac: 11 },
      ],
      monsters: [monster(1, 'Goblin', 30)],
      encounter: null,
      lastResolution: null,
      lastResolutionTarget: null,
      error: null,
      acting: false,
      ...partial,
    },
    true,
  );
}

const flush = () => new Promise((r) => setTimeout(r, 0));

beforeEach(() => {
  arrange({});
  mocked(http.fetchMonsters).mockResolvedValue([monster(1, 'Goblin', 30)]);
  mocked(http.fetchEncounter).mockResolvedValue(encounter('p1'));
});

describe('targeted combat actions pick the route by target kind', () => {
  it('a monster target goes through the combatant route and replaces its board row', async () => {
    const hit = monster(1, 'Goblin', 20);
    mocked(http.combatantDamage).mockResolvedValue(monsterResponse(hit));

    await useCharacterStore.getState().doTargetedDamage('monster:1', 10, 'SLASHING');

    expect(http.combatantDamage).toHaveBeenCalledWith('test-room', 'monster:1', 10, 'SLASHING', undefined, undefined, undefined);
    expect(http.sendDamage).not.toHaveBeenCalled();
    expect(writeViewport).not.toHaveBeenCalled();
    const s = useCharacterStore.getState();
    expect(s.monsters[0].hp.current).toBe(20);
    expect(s.lastResolution).toBe(resolution);
    expect(s.lastResolutionTarget).toBe('Goblin');
    expect(s.snapshot?.hp.current).toBe(100); // the viewed sheet is untouched
    expect(s.acting).toBe(false);
  });

  it('a party target goes through the character route and mirrors THEIR sheet, not ours', async () => {
    const bravo = snapshot('Bravo', 70);
    mocked(http.sendDamage).mockResolvedValue({ resolution, snapshot: bravo });

    await useCharacterStore.getState().doTargetedDamage('p2', 10, 'SLASHING');

    expect(http.sendDamage).toHaveBeenCalledWith('p2', 10, 'SLASHING', undefined, undefined, undefined);
    expect(http.combatantDamage).not.toHaveBeenCalled();
    expect(writeViewport).toHaveBeenCalledWith('p2', 'combat', bravo);
    const s = useCharacterStore.getState();
    expect(s.snapshot?.name).toBe('Alpha');
    expect(s.lastResolutionTarget).toBe('Bravo');
  });

  it('targeting the viewed character adopts the response snapshot as our own', async () => {
    const mine = snapshot('Alpha', 90);
    mocked(http.sendDamage).mockResolvedValue({ resolution, snapshot: mine });

    await useCharacterStore.getState().doTargetedDamage('p1', 10, 'SLASHING');

    const s = useCharacterStore.getState();
    expect(s.snapshot).toBe(mine);
    expect(s.lastResolutionTarget).toBeNull();
    expect(writeViewport).not.toHaveBeenCalled();
  });

  it('a rejected action surfaces the message and releases the acting flag', async () => {
    mocked(http.combatantDamage).mockRejectedValue(new Error('Barb is taunted by Goblin'));

    await useCharacterStore.getState().doTargetedDamage('monster:1', 10, 'SLASHING');

    const s = useCharacterStore.getState();
    expect(s.error).toBe('Barb is taunted by Goblin');
    expect(s.acting).toBe(false);
    expect(s.monsters[0].hp.current).toBe(30);
  });

  it('a monster the board did not know yet is appended rather than dropped', async () => {
    arrange({ monsters: [] });
    mocked(http.combatantHeal).mockResolvedValue(monsterResponse(monster(7, 'Wolf', 25)));

    await useCharacterStore.getState().doTargetedHeal('monster:7', 5);

    expect(useCharacterStore.getState().monsters.map((m) => m.name)).toEqual(['Wolf']);
  });

  it('a monster action during a running encounter refreshes the turn order (HP chips ride in it)', async () => {
    arrange({ encounter: encounter('p1') });
    mocked(http.combatantApplyEffect).mockResolvedValue(monsterResponse(monster(1, 'Goblin', 30)));

    await useCharacterStore.getState().doTargetedApplyEffect('monster:1', { effectId: 'burning', stacks: 2 });
    await flush();

    expect(http.combatantApplyEffect).toHaveBeenCalledWith('test-room', 'monster:1', { effectId: 'burning', stacks: 2 });
    expect(http.fetchEncounter).toHaveBeenCalled();
  });
});

describe('resolve-onto-target actions refresh whoever they landed on', () => {
  it('a weapon attack at a monster names the target and refreshes the fight list', async () => {
    mocked(http.weaponAttack).mockResolvedValue({ resolution, snapshot: snapshot('Alpha', 100) });

    await useCharacterStore.getState().doWeaponAttack(undefined, 'monster:1');
    await flush();

    expect(http.weaponAttack).toHaveBeenCalledWith('p1', undefined, 'monster:1');
    expect(useCharacterStore.getState().lastResolutionTarget).toBe('Goblin');
    expect(http.fetchMonsters).toHaveBeenCalled();
  });

  it('an ability aimed at a party member re-fetches THEIR snapshot into the mirror', async () => {
    mocked(http.useAbility).mockResolvedValue({ resolution, snapshot: snapshot('Alpha', 100) });
    mocked(http.fetchAbilities).mockResolvedValue({ classId: 'barbarian', known: [], picked: [], uses: [], custom: [] });
    const bravo = snapshot('Bravo', 80);
    mocked(http.fetchCombatSnapshot).mockResolvedValue(bravo);

    await useCharacterStore.getState().doUseAbility('unseen-blade-scar', 'p2');
    await flush();

    expect(http.useAbility).toHaveBeenCalledWith('p1', 'unseen-blade-scar', 'p2');
    expect(http.fetchCombatSnapshot).toHaveBeenCalledWith('p2');
    expect(writeViewport).toHaveBeenCalledWith('p2', 'combat', bravo);
    expect(useCharacterStore.getState().lastResolutionTarget).toBe('Bravo');
  });
});

describe('encounter adoption', () => {
  it('reloads the fight list when the turn changes hands and pulls our sheet when it becomes our turn', async () => {
    arrange({ encounter: encounter('monster:1') });
    const fresh = snapshot('Alpha', 95);
    mocked(http.fetchCombatSnapshot).mockResolvedValue(fresh);
    mocked(http.fetchAbilities).mockResolvedValue({ classId: 'barbarian', known: [], picked: [], uses: [], custom: [] });

    useCharacterStore.getState().adoptEncounter(encounter('p1'));
    await flush();

    expect(http.fetchMonsters).toHaveBeenCalled();
    expect(http.fetchCombatSnapshot).toHaveBeenCalledWith('p1');
    expect(useCharacterStore.getState().snapshot).toBe(fresh);
  });

  it('does not refetch anything when the same turn is re-adopted', async () => {
    arrange({ encounter: encounter('p1') });

    useCharacterStore.getState().adoptEncounter(encounter('p1'));
    await flush();

    expect(http.fetchMonsters).not.toHaveBeenCalled();
    expect(http.fetchCombatSnapshot).not.toHaveBeenCalled();
  });

  it('the GM ending a monster turn adopts its row, then re-reads order and fight', async () => {
    arrange({ encounter: encounter('monster:1') });
    mocked(http.combatantTurnEnd).mockResolvedValue(monsterResponse(monster(1, 'Goblin', 28)));
    mocked(http.fetchEncounter).mockResolvedValue(encounter('p1'));
    mocked(http.fetchMonsters).mockResolvedValue([monster(1, 'Goblin', 28)]);
    mocked(http.fetchCombatSnapshot).mockResolvedValue(snapshot('Alpha', 100));
    mocked(http.fetchAbilities).mockResolvedValue({ classId: 'barbarian', known: [], picked: [], uses: [], custom: [] });

    await useCharacterStore.getState().endMonsterTurn('monster:1');
    await flush();

    expect(http.combatantTurnEnd).toHaveBeenCalledWith('test-room', 'monster:1');
    const s = useCharacterStore.getState();
    expect(s.encounter?.currentPlayerId).toBe('p1');
    expect(s.monsters[0].hp.current).toBe(28);
    expect(s.acting).toBe(false);
  });
});

describe('spawning', () => {
  it('spawns, refreshes the fight list, and re-reads a running order (reinforcements)', async () => {
    arrange({ encounter: encounter('p1') });
    mocked(http.spawnMonsters).mockResolvedValue([monster(2, 'Goblin 2', 40)]);
    mocked(http.fetchMonsters).mockResolvedValue([monster(1, 'Goblin', 30), monster(2, 'Goblin 2', 40)]);

    await useCharacterStore.getState().spawnMonsters(1, 1);

    expect(http.spawnMonsters).toHaveBeenCalledWith('test-room', 1, 1);
    expect(useCharacterStore.getState().monsters).toHaveLength(2);
    expect(http.fetchEncounter).toHaveBeenCalled();
  });
});
