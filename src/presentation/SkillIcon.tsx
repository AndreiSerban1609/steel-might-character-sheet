// Ported from the Deck of Fates extension (src/components/CardArt.jsx SkillIcon),
// re-keyed by our skill ids (skills.json) instead of display names.
// 14x14 SVG silhouettes, drawn in the caller's colour.
import type { ReactElement } from 'react';

interface SkillIconProps {
  /** A skills.json id, e.g. "animal-handling". Unknown ids render nothing. */
  skillId: string;
  color: string;
  size?: number;
}

export function SkillIcon({ skillId, color, size = 14 }: SkillIconProps) {
  const p = { fill: color, fillRule: 'evenodd' as const };
  const s = { fill: 'none', stroke: color, strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const };
  const icons: Record<string, ReactElement> = {
    // Dumbbell (horizontal)
    lifting: <g><rect x="3" y="5.5" width="8" height="3" rx="1" {...p} /><rect x="0.5" y="3.5" width="3" height="7" rx="0.8" {...p} /><rect x="10.5" y="3.5" width="3" height="7" rx="0.8" {...p} /></g>,
    // Running figure
    athletics: <g><circle cx="9" cy="2" r="1.8" {...p} /><path d="M4 13 L6.5 8 L5 6.5 L7 4.5 L9.5 7 L12 5.5" {...s} strokeWidth="1.6" /><path d="M7 4.5 L7 8 L9 13" {...s} strokeWidth="1.6" /></g>,
    // Lockpick and keyhole
    thievery: <g><rect x="3" y="5" width="8" height="7" rx="1.5" {...s} strokeWidth="1.4" /><path d="M7 2 Q7 0.5 8.5 0.5 Q10 0.5 10 2 L10 5" {...s} strokeWidth="1.4" /><circle cx="7" cy="8.5" r="1.2" {...p} /><rect x="6.5" y="9.5" width="1" height="1.5" {...p} /></g>,
    // Lightning bolt
    reflex: <g><path d="M8.5 0.5 L5 6.5 L7.5 6.5 L5.5 13.5 L10 6 L7.5 6 L9.5 0.5 Z" {...p} /></g>,
    // Eye with a slash through it
    stealth: <g><path d="M1 7 Q4 2.5 7 2.5 Q10 2.5 13 7 Q10 11.5 7 11.5 Q4 11.5 1 7 Z" {...s} strokeWidth="1.3" /><circle cx="7" cy="7" r="2.2" {...p} /><line x1="2" y1="12" x2="12" y2="2" stroke={color} strokeWidth="1.6" strokeLinecap="round" /></g>,
    // Open book
    knowledge: <g><path d="M7 3 L7 12" {...s} strokeWidth="1" /><path d="M7 3 Q4 2 1.5 3 L1.5 12 Q4 11 7 12" {...s} strokeWidth="1.3" /><path d="M7 3 Q10 2 12.5 3 L12.5 12 Q10 11 7 12" {...s} strokeWidth="1.3" /></g>,
    // Arcane rune circle
    arcana: <g><circle cx="7" cy="7" r="5.5" {...s} strokeWidth="1.3" /><circle cx="7" cy="7" r="2.5" {...s} strokeWidth="1.1" /><line x1="7" y1="1.5" x2="7" y2="4.5" {...s} strokeWidth="1.1" /><line x1="7" y1="9.5" x2="7" y2="12.5" {...s} strokeWidth="1.1" /><line x1="1.5" y1="7" x2="4.5" y2="7" {...s} strokeWidth="1.1" /><line x1="9.5" y1="7" x2="12.5" y2="7" {...s} strokeWidth="1.1" /></g>,
    // Magnifying glass
    investigation: <g><circle cx="6" cy="6" r="4" {...s} strokeWidth="1.6" /><line x1="9" y1="9.5" x2="13" y2="13" {...s} strokeWidth="2.2" /></g>,
    // Medical cross
    medicine: <g><rect x="5" y="1.5" width="4" height="11" rx="0.8" {...p} /><rect x="1.5" y="5" width="11" height="4" rx="0.8" {...p} /></g>,
    // Open eye
    perception: <g><path d="M1 7 Q4 2.5 7 2.5 Q10 2.5 13 7 Q10 11.5 7 11.5 Q4 11.5 1 7 Z" {...s} strokeWidth="1.3" /><circle cx="7" cy="7" r="2.2" {...p} /></g>,
    // Campfire
    survival: <g><path d="M7 1 Q9 4 8 6 Q9 5 9.5 3 Q11 6 9 9 L5 9 Q3 6 4.5 3 Q5 5 6 6 Q5 4 7 1 Z" {...p} /><line x1="3" y1="12" x2="5" y2="9" {...s} strokeWidth="1.4" /><line x1="11" y1="12" x2="9" y2="9" {...s} strokeWidth="1.4" /><line x1="7" y1="9" x2="7" y2="12.5" {...s} strokeWidth="1.4" /></g>,
    // Paw print
    'animal-handling': <g><ellipse cx="7" cy="9" rx="3" ry="2.5" {...p} /><circle cx="4" cy="5.5" r="1.3" {...p} /><circle cx="10" cy="5.5" r="1.3" {...p} /><circle cx="5.5" cy="3.5" r="1.2" {...p} /><circle cx="8.5" cy="3.5" r="1.2" {...p} /></g>,
    // Third eye
    insight: <g><path d="M1 7 Q4 3 7 3 Q10 3 13 7 Q10 11 7 11 Q4 11 1 7 Z" {...s} strokeWidth="1.2" /><circle cx="7" cy="7" r="2" {...s} strokeWidth="1.2" /><circle cx="7" cy="7" r="0.8" {...p} /><line x1="7" y1="0.5" x2="7" y2="3" {...s} strokeWidth="1.2" /></g>,
    // Ankh / holy symbol
    religion: <g><circle cx="7" cy="3.5" r="2.5" {...s} strokeWidth="1.5" /><line x1="7" y1="6" x2="7" y2="13" {...s} strokeWidth="1.8" /><line x1="4" y1="8.5" x2="10" y2="8.5" {...s} strokeWidth="1.8" /></g>,
    // Heart
    seduction: <g><path d="M7 12 Q1 7 1 4.5 Q1 2 3.5 2 Q5.5 2 7 4.5 Q8.5 2 10.5 2 Q13 2 13 4.5 Q13 7 7 12 Z" {...p} /></g>,
    // Music note
    performance: <g><circle cx="4.5" cy="10.5" r="2.2" {...p} /><line x1="6.7" y1="10.5" x2="6.7" y2="1.5" stroke={color} strokeWidth="1.8" strokeLinecap="round" /><path d="M6.7 1.5 L12 0.5 L12 4.5 L6.7 5.5" {...p} /></g>,
    // Two overlapping speech bubbles
    persuasion: <g><rect x="1" y="1" width="8" height="6" rx="1.5" {...s} strokeWidth="1.3" /><rect x="5" y="5" width="8" height="6" rx="1.5" {...s} strokeWidth="1.3" /><path d="M3 7 L2 9.5" {...s} strokeWidth="1.3" /><path d="M11 11 L12 13" {...s} strokeWidth="1.3" /></g>,
    // Theatre mask
    deception: <g><circle cx="7" cy="6" r="5.5" {...s} strokeWidth="1.3" /><circle cx="5" cy="5" r="1" {...p} /><circle cx="9" cy="5" r="1" {...p} /><path d="M4.5 8 Q5.5 9.5 7 8 Q8.5 6.5 9.5 8" {...s} strokeWidth="1.2" /></g>,
    // Skull
    intimidation: <g><path d="M3 7 Q3 1.5 7 1.5 Q11 1.5 11 7 L11 9 Q11 10 10 10 L4 10 Q3 10 3 9 Z" {...p} /><circle cx="5.5" cy="6" r="1.3" fill="#0e0e14" /><circle cx="8.5" cy="6" r="1.3" fill="#0e0e14" /><rect x="6" y="8.5" width="0.7" height="1.5" fill="#0e0e14" /><rect x="7.3" y="8.5" width="0.7" height="1.5" fill="#0e0e14" /><rect x="5.5" y="10" width="3" height="3" rx="0.3" {...p} /><line x1="6.5" y1="10" x2="6.5" y2="13" stroke="#0e0e14" strokeWidth="0.6" /><line x1="7.5" y1="10" x2="7.5" y2="13" stroke="#0e0e14" strokeWidth="0.6" /></g>,
  };
  const icon = icons[skillId];
  if (!icon) return null;
  return (
    <svg width={size} height={size} viewBox="0 0 14 14" fill="none" aria-hidden="true">
      {icon}
    </svg>
  );
}
