/**
 * Pure display-logic tests (Story 6.1): the domain layer never touches React, the SDK or
 * fetch, so these run without any mocking.
 */
import { describe, expect, it } from 'vitest';
import { liveVitalsFromSlice } from './partyMirror';
import { cardModifierDisplay, resolveCardLabel, skillDisplayName } from './cardThemes';
import { DAMAGE_TYPE_OPTIONS, EFFECT_OPTIONS, effectOption } from './combatCatalog';
import { isMonsterId } from '../platform/types';
import type { Card } from '../platform/types';

describe('partyMirror.liveVitalsFromSlice', () => {
  it('reads vitals from a mirrored combat viewport', () => {
    const slice = { viewport: 'combat', data: { hp: { current: 42, max: 100, temp: 0 }, ac: 15 } };
    expect(liveVitalsFromSlice(slice)).toEqual({ currentHp: 42, maxHp: 100, ac: 15 });
  });

  it('ignores other viewports and malformed slices (older builds, wrong tab)', () => {
    expect(liveVitalsFromSlice({ viewport: 'bio', data: { hp: { current: 1, max: 2 }, ac: 3 } })).toBeNull();
    expect(liveVitalsFromSlice({ viewport: 'combat', data: { hp: { current: 'x', max: 2 }, ac: 3 } })).toBeNull();
    expect(liveVitalsFromSlice({ viewport: 'combat', data: null })).toBeNull();
    expect(liveVitalsFromSlice(undefined)).toBeNull();
  });
});

describe('cardThemes', () => {
  const card = (type: Card['type'], modifier: number | null): Card => ({ type, name: 'x', modifier, description: '' });

  it('a zero Neutral shows no modifier at all (Deck of Fates feedback round 1)', () => {
    expect(cardModifierDisplay(card('NEUTRAL', 0))).toBe('');
    expect(cardModifierDisplay(card('NEUTRAL', 1))).toBe('+1');
    expect(cardModifierDisplay(card('NEUTRAL', -2))).toBe('-2');
  });

  it('other cards keep their intrinsic display', () => {
    expect(cardModifierDisplay(card('CLASS', 0))).toBe('±0');
    expect(cardModifierDisplay(card('ENCOUNTER', -3))).toBe('-3');
    expect(cardModifierDisplay(card('STAT', null))).toBe('STAT');
    expect(cardModifierDisplay(card('STEEL_CRITICAL', null))).toBe('CRITICAL');
    expect(cardModifierDisplay(card('MIGHT_CRITICAL', null))).toBe('CRITICAL');
  });

  it('labels criticals by their steel / might identity and class cards by path', () => {
    expect(resolveCardLabel(card('MIGHT_CRITICAL', null))).toBe('Might');
    expect(resolveCardLabel(card('CLASS', 2), 'wraith_hunter')).toBe('Wraith Hunter');
    expect(resolveCardLabel(card('CLASS', 2))).toBe('Class');
  });

  it('maps skill ids to their display names for the card icon slot', () => {
    expect(skillDisplayName('animal-handling')).toBe('Animal Handling');
    expect(skillDisplayName('stealth')).toBe('Stealth');
    expect(skillDisplayName('not-a-skill')).toBe('not-a-skill');
  });
});

describe('combatant ids', () => {
  it('monsters live in the monster: namespace, players anywhere else', () => {
    expect(isMonsterId('monster:12')).toBe(true);
    expect(isMonsterId('dragons-lair-kael@example.com')).toBe(false);
    expect(isMonsterId('')).toBe(false);
    expect(isMonsterId(null)).toBe(false);
    expect(isMonsterId(undefined)).toBe(false);
  });
});

describe('combatCatalog', () => {
  it('exposes every damage type with its category', () => {
    const fire = DAMAGE_TYPE_OPTIONS.find((d) => d.id === 'FIRE');
    expect(fire?.category).toBe('magical');
    expect(DAMAGE_TYPE_OPTIONS.find((d) => d.id === 'CRUSHING')?.category).toBe('physical');
    expect(DAMAGE_TYPE_OPTIONS.find((d) => d.id === 'TRUE')?.category).toBe('true');
  });

  it('knows the taunt effect the engine enforces', () => {
    const taunted = effectOption('taunted');
    expect(taunted?.polarity).toBe('negative');
    expect(EFFECT_OPTIONS.map((e) => e.id)).toContain('burning');
  });
});
