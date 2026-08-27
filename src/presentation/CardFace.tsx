// Ported from the Deck of Fates extension (src/components/CardArt.jsx CardFace),
// including the CLASS-card icon slots: a skill-restriction badge (left) and a
// redraw-bonus badge (right), rendered only when the card carries them.
import type { CSSProperties } from 'react';
import type { Card } from '../platform/types';
import {
  resolveCardTheme,
  resolveCardLabel,
  cardModifierDisplay,
  normalizeThemeId,
  skillDisplayName,
} from '../domain/cardThemes';
import { formatModifier } from '../domain/stats';
import { getFrameComponent } from './CardFrames';
import { SkillIcon } from './SkillIcon';

interface CardFaceProps {
  card: Card;
  /** Path id of the drawing character — themes CLASS (personal) cards. */
  pathId?: string;
  size?: number;
  animating?: boolean;
}

export function CardFace({ card, pathId, size = 200, animating = false }: CardFaceProps) {
  const themeId = card.type === 'CLASS' && pathId ? normalizeThemeId(pathId) : undefined;
  const theme = resolveCardTheme(card, themeId);
  const label = resolveCardLabel(card, themeId);
  const modDisplay = cardModifierDisplay(card);
  const w = size;
  const h = size * 1.45;
  // The type ramp below is designed for size 200; under ~120px it needs more
  // height than the card has and the name/description spill past the bottom edge
  // (overflow must stay visible for the frame protrusions, so nothing clips it).
  const compact = size < 120;
  const isCrit = card.type === 'STEEL_CRITICAL' || card.type === 'MIGHT_CRITICAL';
  const isSteel = card.type === 'STEEL_CRITICAL';
  const FrameComponent = getFrameComponent(card.type, themeId);
  const glowName = isSteel ? 'pulseGlowSteel' : 'pulseGlowMight';

  // Icon slots exist on CLASS cards only (base cards never carry these fields).
  const checkType = card.type === 'CLASS' ? card.checkType || null : null;
  const hasRedraw = card.type === 'CLASS' && card.redrawModifier != null;
  const hasSlots = checkType !== null || hasRedraw;
  // Gallery/toast thumbnails (56px) have no spare height: the slot row would push the
  // name off the card, so trade symbol/modifier size for it there.
  const squeeze = hasSlots && size < 70;
  const slotStyle: CSSProperties = {
    display: 'flex',
    alignItems: 'center',
    gap: 3,
    background: `${theme.border}18`,
    border: `1px solid ${theme.border}30`,
    borderRadius: 4,
    padding: compact ? '1px 3px' : '3px 6px',
    lineHeight: 1,
    cursor: 'help',
  };

  const cardStyle: CSSProperties = {
    width: w,
    height: h,
    borderRadius: 14,
    background: theme.bg,
    border: 'none',
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'space-between',
    boxShadow: `0 4px 32px ${theme.glow}, inset 0 1px 0 rgba(255,255,255,0.08)`,
    position: 'relative',
    overflow: 'visible',
    padding: compact ? '10px 8px' : '16px 12px',
    animation: isCrit ? `${glowName} 2s ease-in-out infinite` : 'none',
  };

  return (
    <>
      {isCrit && (
        <style>{`
          @keyframes ${glowName} {
            0%, 100% { box-shadow: 0 4px 32px ${theme.glow}, 0 0 48px ${theme.glow}, inset 0 1px 0 rgba(255,255,255,0.08); }
            50% { box-shadow: 0 4px 48px ${theme.glow}, 0 0 72px ${theme.glow}, inset 0 1px 0 rgba(255,255,255,0.12); }
          }
          @keyframes shimmerSweep {
            0% { transform: translateX(-150%); }
            100% { transform: translateX(150%); }
          }
        `}</style>
      )}

      <div className={animating ? 'card-flip-in' : ''} style={cardStyle}>
        {FrameComponent && <FrameComponent color={theme.border} w={w} h={h} />}

        {isCrit && (
          <div style={{ position: 'absolute', inset: 0, overflow: 'hidden', pointerEvents: 'none', borderRadius: 12, zIndex: 2 }}>
            <div
              style={{
                position: 'absolute',
                top: 0,
                left: 0,
                width: '60%',
                height: '100%',
                background: isSteel
                  ? 'linear-gradient(90deg, transparent 0%, rgba(192,205,232,0.25) 40%, rgba(255,255,255,0.35) 50%, rgba(192,205,232,0.25) 60%, transparent 100%)'
                  : 'linear-gradient(90deg, transparent 0%, rgba(240,128,96,0.2) 40%, rgba(255,255,255,0.3) 50%, rgba(240,128,96,0.2) 60%, transparent 100%)',
                animation: 'shimmerSweep 2.5s ease-in-out infinite',
              }}
            />
          </div>
        )}

        {isCrit && (
          <div
            style={{
              position: 'absolute',
              inset: 0,
              background: isSteel
                ? 'repeating-linear-gradient(45deg, transparent, transparent 8px, rgba(139,157,195,0.04) 8px, rgba(139,157,195,0.04) 16px)'
                : 'radial-gradient(ellipse at center, rgba(224,64,48,0.15) 0%, transparent 70%)',
              pointerEvents: 'none',
            }}
          />
        )}

        <div
          style={{
            background: theme.labelBg,
            borderRadius: 6,
            padding: compact ? '2px 6px' : '3px 10px',
            fontSize: compact ? 8 : 10,
            color: theme.accent,
            fontFamily: "'Cinzel', 'Palatino', serif",
            letterSpacing: compact ? 1 : 2,
            textTransform: 'uppercase',
            border: `1px solid ${theme.border}44`,
            zIndex: 5,
          }}
        >
          {label}
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: squeeze ? 2 : compact ? 4 : 6, zIndex: 5 }}>
          <div style={{ fontSize: isCrit ? (compact ? 30 : 48) : (squeeze ? 16 : compact ? 24 : 36), color: theme.accent, textShadow: `0 0 20px ${theme.glow}`, lineHeight: 1 }}>
            {theme.symbol}
          </div>
          {modDisplay && (
            <div
              style={{
                fontSize: isCrit ? (compact ? 14 : 22) : (squeeze ? 15 : compact ? 18 : 28),
                fontWeight: 700,
                color: '#fff',
                fontFamily: "'Cinzel', 'Palatino', serif",
                textShadow: `0 0 12px ${theme.glow}`,
                letterSpacing: isCrit ? (compact ? 1 : 3) : 1,
              }}
            >
              {modDisplay}
            </div>
          )}
        </div>

        {hasSlots && (
          <div
            style={{
              display: 'flex',
              justifyContent: checkType && hasRedraw ? 'space-between' : checkType ? 'flex-start' : 'flex-end',
              alignItems: 'center',
              width: '100%',
              padding: compact ? 0 : '0 4px',
              zIndex: 6,
            }}
          >
            {checkType ? (
              <div
                style={{ ...slotStyle, gap: compact ? 0 : 4 }}
                title={`Only on ${skillDisplayName(checkType)} checks — auto-passes on any other check`}
              >
                <SkillIcon skillId={checkType} color={theme.accent} size={compact ? 9 : 12} />
                {!compact && (
                  <span
                    style={{
                      fontSize: 7,
                      color: theme.accent,
                      opacity: 0.6,
                      fontFamily: "'Cinzel', 'Palatino', serif",
                      letterSpacing: 0.5,
                      textTransform: 'uppercase',
                      whiteSpace: 'nowrap',
                    }}
                  >
                    {skillDisplayName(checkType)}
                  </span>
                )}
              </div>
            ) : (
              <div />
            )}
            {hasRedraw && (
              <div
                style={slotStyle}
                title={`Redraw bonus ${formatModifier(card.redrawModifier ?? 0)} — passed on draw; the bonus adds to the check's total`}
              >
                <span
                  style={{
                    fontSize: compact ? 7 : 10,
                    color: theme.accent,
                    opacity: 0.6,
                    fontFamily: "'Cinzel', 'Palatino', serif",
                    whiteSpace: 'nowrap',
                  }}
                >
                  ↻{formatModifier(card.redrawModifier ?? 0)}
                </span>
              </div>
            )}
          </div>
        )}

        <div style={{ textAlign: 'center', zIndex: 5, maxWidth: '100%' }}>
          <div
            style={{
              fontSize: compact ? 11 : 13,
              fontWeight: 600,
              color: theme.accent,
              fontFamily: "'Cinzel', 'Palatino', serif",
              marginBottom: compact ? 0 : 2,
              whiteSpace: 'nowrap',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
            }}
          >
            {card.name}
          </div>
          {!isCrit && !compact && card.description && (
            <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.55)', fontStyle: 'italic', lineHeight: 1.3, maxHeight: 30, overflow: 'hidden' }}>
              {card.description}
            </div>
          )}
        </div>
      </div>
    </>
  );
}
