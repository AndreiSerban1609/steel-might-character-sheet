// Ported from the Deck of Fates extension (src/lib/classThemes.js + CardArt resolveTheme/label).
// Pure display data — no React, no SDK, no fetch.

import type { Card, CardType } from '../platform/types';
import skillsRaw from '../data/skills.json';

const SKILL_NAME = new Map((skillsRaw as { id: string; name: string }[]).map((s) => [s.id, s.name]));

export interface CardTheme {
  bg: string;
  border: string;
  glow: string;
  accent: string;
  symbol: string;
  labelBg: string;
}

interface ClassTheme {
  label: string;
  bg: string;
  border: string;
  glow: string;
  accent: string;
  labelBg: string;
}

/** Per-path visual themes (keyed by the path id, underscored). */
export const CLASS_THEMES: Record<string, ClassTheme> = {
  musician: {
    label: 'Musician',
    bg: 'linear-gradient(135deg, #2e1422 0%, #5c2040 40%, #7a2850 60%, #2e1422 100%)',
    border: '#c4587a',
    glow: 'rgba(196, 88, 122, 0.5)',
    accent: '#f0a0be',
    labelBg: '#5c2040',
  },
  disciple: {
    label: 'Disciple',
    bg: 'linear-gradient(135deg, #2e2408 0%, #5c4a10 40%, #8b7218 60%, #2e2408 100%)',
    border: '#e0c040',
    glow: 'rgba(224, 192, 64, 0.5)',
    accent: '#f5e088',
    labelBg: '#5c4a10',
  },
  wildborn: {
    label: 'Wildborn',
    bg: 'linear-gradient(135deg, #0a2e18 0%, #1a5c30 40%, #2a7a48 60%, #0a2e18 100%)',
    border: '#4aaa6a',
    glow: 'rgba(74, 170, 106, 0.5)',
    accent: '#80d0a0',
    labelBg: '#1a5c30',
  },
  warrior: {
    label: 'Warrior',
    bg: 'linear-gradient(135deg, #2e0a0a 0%, #6c1a1a 40%, #8b2020 60%, #2e0a0a 100%)',
    border: '#c43030',
    glow: 'rgba(196, 48, 48, 0.5)',
    accent: '#f07070',
    labelBg: '#6c1a1a',
  },
  monk: {
    label: 'Monk',
    bg: 'linear-gradient(135deg, #0a2e2a 0%, #106058 40%, #188878 60%, #0a2e2a 100%)',
    border: '#40b8a8',
    glow: 'rgba(64, 184, 168, 0.5)',
    accent: '#80e0d0',
    labelBg: '#106058',
  },
  archer: {
    label: 'Archer',
    bg: 'linear-gradient(135deg, #122e0a 0%, #2a5c18 40%, #3a7a28 60%, #122e0a 100%)',
    border: '#5aaa40',
    glow: 'rgba(90, 170, 64, 0.45)',
    accent: '#90d870',
    labelBg: '#2a5c18',
  },
  rogue: {
    label: 'Rogue',
    bg: 'linear-gradient(135deg, #18141e 0%, #302848 40%, #443860 60%, #18141e 100%)',
    border: '#8068a0',
    glow: 'rgba(128, 104, 160, 0.5)',
    accent: '#b8a0d8',
    labelBg: '#302848',
  },
  corruptor: {
    label: 'Corruptor',
    bg: 'linear-gradient(135deg, #1a0e24 0%, #3a1848 40%, #501e68 60%, #1a0e24 100%)',
    border: '#8a40c0',
    glow: 'rgba(138, 64, 192, 0.5)',
    accent: '#c080f0',
    labelBg: '#3a1848',
  },
  wizard: {
    label: 'Wizard',
    bg: 'linear-gradient(135deg, #0a0e2e 0%, #182060 40%, #203088 60%, #0a0e2e 100%)',
    border: '#4060d4',
    glow: 'rgba(64, 96, 212, 0.5)',
    accent: '#80a0f0',
    labelBg: '#182060',
  },
  wraith_hunter: {
    label: 'Wraith Hunter',
    bg: 'linear-gradient(135deg, #1a1e22 0%, #384048 40%, #506068 60%, #1a1e22 100%)',
    border: '#90a8b8',
    glow: 'rgba(144, 168, 184, 0.5)',
    accent: '#b8d0e0',
    labelBg: '#384048',
  },
  battlemage: {
    label: 'Battlemage',
    bg: 'linear-gradient(135deg, #1e0a1e 0%, #4a1848 20%, #182050 80%, #0a0e2e 100%)',
    border: '#a040c0',
    glow: 'rgba(160, 64, 192, 0.4)',
    accent: '#c890e0',
    labelBg: '#381848',
  },
};

const CARD_THEMES: Record<CardType, CardTheme> = {
  STEEL_CRITICAL: {
    bg: 'linear-gradient(135deg, #2a2d3e 0%, #4a4d5e 40%, #6b7094 60%, #3a3d4e 100%)',
    border: '#8b9dc3',
    glow: 'rgba(139, 157, 195, 0.6)',
    accent: '#c0cde8',
    symbol: '⚔',
    labelBg: '#4a5068',
  },
  MIGHT_CRITICAL: {
    bg: 'linear-gradient(135deg, #2e0a0a 0%, #6b1515 40%, #8b2020 60%, #2e0a0a 100%)',
    border: '#e04030',
    glow: 'rgba(224, 64, 48, 0.6)',
    accent: '#f08060',
    symbol: '✦',
    labelBg: '#6b1515',
  },
  NEUTRAL: {
    bg: 'linear-gradient(135deg, #2a2a28 0%, #3d3b35 50%, #2a2a28 100%)',
    border: '#7a7568',
    glow: 'rgba(122, 117, 104, 0.3)',
    accent: '#a8a090',
    symbol: '◆',
    labelBg: '#3d3a34',
  },
  ENCOUNTER: {
    bg: 'linear-gradient(135deg, #2e1a0a 0%, #5c2a0e 40%, #8b3a12 60%, #2e1a0a 100%)',
    border: '#d4622a',
    glow: 'rgba(212, 98, 42, 0.5)',
    accent: '#f0a060',
    symbol: '☠',
    labelBg: '#5c2a0e',
  },
  STAT: {
    bg: 'linear-gradient(135deg, #0a1a2e 0%, #0e3a5c 40%, #126a8b 60%, #0a1a2e 100%)',
    border: '#2a9cd4',
    glow: 'rgba(42, 156, 212, 0.5)',
    accent: '#60c0f0',
    symbol: '◈',
    labelBg: '#0e3a5c',
  },
  CLASS: {
    bg: 'linear-gradient(135deg, #2e2a0a 0%, #5c4a0e 40%, #8b7a12 60%, #2e2a0a 100%)',
    border: '#d4b22a',
    glow: 'rgba(212, 178, 42, 0.5)',
    accent: '#f0d860',
    symbol: '★',
    labelBg: '#5c4a0e',
  },
};

/** Path ids in our data use hyphens (wraith-hunter); the theme/frame maps use underscores. */
export function normalizeThemeId(pathId: string): string {
  return pathId.replace(/-/g, '_');
}

export function resolveCardTheme(card: Card, themeId?: string): CardTheme {
  if (card.type === 'CLASS' && themeId && CLASS_THEMES[themeId]) {
    const ct = CLASS_THEMES[themeId];
    return { bg: ct.bg, border: ct.border, glow: ct.glow, accent: ct.accent, symbol: '★', labelBg: ct.labelBg };
  }
  return CARD_THEMES[card.type] ?? CARD_THEMES.NEUTRAL;
}

export function resolveCardLabel(card: Card, themeId?: string): string {
  if (card.type === 'CLASS' && themeId && CLASS_THEMES[themeId]) {
    return CLASS_THEMES[themeId].label;
  }
  const labels: Record<CardType, string> = {
    STEEL_CRITICAL: 'Steel',
    MIGHT_CRITICAL: 'Might',
    NEUTRAL: 'Neutral',
    ENCOUNTER: 'Encounter',
    STAT: 'Stat',
    CLASS: 'Class',
  };
  return labels[card.type] ?? 'Unknown';
}

/**
 * The card's intrinsic display (the resolved d10/total is shown separately).
 * Empty string = show nothing: a ±0 Neutral is just symbol + label + name
 * (Deck of Fates feedback round 1).
 */
export function cardModifierDisplay(card: Card): string {
  if (card.type === 'STEEL_CRITICAL' || card.type === 'MIGHT_CRITICAL') return 'CRITICAL';
  if (card.type === 'STAT') return 'STAT';
  const m = card.modifier ?? 0;
  if (m === 0) return card.type === 'NEUTRAL' ? '' : '±0';
  return m > 0 ? `+${m}` : `${m}`;
}

/** Skill-check restriction label for a CLASS card's icon slot (id → display name). */
export function skillDisplayName(skillId: string): string {
  return SKILL_NAME.get(skillId) ?? skillId;
}
