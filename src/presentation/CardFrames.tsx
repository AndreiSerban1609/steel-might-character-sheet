// Ported from the Deck of Fates extension (src/components/CardFrames.jsx).
import { useId, type ReactNode } from 'react';

interface FrameProps {
  color?: string;
  w?: number;
  h?: number;
}

const P = 20;
const S: React.CSSProperties = { position: 'absolute', top: -P, left: -P, pointerEvents: 'none', zIndex: 4 };

const rrect = (w: number, h: number, r = 14) =>
  `M${r} 0H${w - r}Q${w} 0 ${w} ${r}V${h - r}Q${w} ${h} ${w - r} ${h}H${r}Q0 ${h} 0 ${h - r}V${r}Q0 0 ${r} 0Z`;

const outerRect = (w: number, h: number) => `M${-P} ${-P}H${w + P}V${h + P}H${-P}Z`;

function Frame({
  w,
  h,
  children,
}: {
  w: number;
  h: number;
  children: ReactNode | ((clipId: string) => ReactNode);
}) {
  const clipId = useId().replace(/:/g, '_');
  return (
    <svg style={S} width={w + P * 2} height={h + P * 2} viewBox={`0 0 ${w + P * 2} ${h + P * 2}`} fill="none">
      <defs>
        <clipPath id={clipId}>
          <path d={`${outerRect(w, h)} ${rrect(w, h)}`} clipRule="evenodd" />
        </clipPath>
      </defs>
      <g transform={`translate(${P},${P})`}>{typeof children === 'function' ? children(clipId) : children}</g>
    </svg>
  );
}

// ── Base frames ──

export function SteelCriticalFrame({ color = '#8b9dc3', w = 200, h = 290 }: FrameProps) {
  return (
    <Frame w={w} h={h}>
      <path d={rrect(w, h)} stroke={color} strokeWidth="3" />
      <path d={rrect(w - 8, h - 8, 10)} stroke={color} strokeWidth="0.8" opacity="0.4" transform="translate(4,4)" />
      {[[14, 14], [w - 14, 14], [14, h - 14], [w - 14, h - 14]].map(([cx, cy], i) => (
        <g key={i}>
          <circle cx={cx} cy={cy} r="5.5" stroke={color} strokeWidth="1.8" fill="none" opacity="0.7" />
          <circle cx={cx} cy={cy} r="2" fill={color} opacity="0.6" />
        </g>
      ))}
      {[w * 0.35, w * 0.5, w * 0.65].map((x, i) => (
        <g key={`t${i}`}>
          <line x1={x - 8} y1="1" x2={x + 8} y2="1" stroke={color} strokeWidth="4" opacity="0.4" />
          <line x1={x - 8} y1={h - 1} x2={x + 8} y2={h - 1} stroke={color} strokeWidth="4" opacity="0.4" />
        </g>
      ))}
      {[h * 0.3, h * 0.5, h * 0.7].map((y, i) => (
        <g key={`s${i}`}>
          <line x1="1" y1={y - 8} x2="1" y2={y + 8} stroke={color} strokeWidth="4" opacity="0.4" />
          <line x1={w - 1} y1={y - 8} x2={w - 1} y2={y + 8} stroke={color} strokeWidth="4" opacity="0.4" />
        </g>
      ))}
    </Frame>
  );
}

export function MightCriticalFrame({ color = '#e04030', w = 200, h = 290 }: FrameProps) {
  return (
    <Frame w={w} h={h}>
      <path d={rrect(w, h)} stroke={color} strokeWidth="2.5" />
      <path d={`M${w * 0.18} 0 L${w * 0.2} -8 L${w * 0.24} 3 L${w * 0.27} -7 L${w * 0.3} 0`} stroke={color} strokeWidth="2.5" opacity="0.8" />
      <path d={`M${w * 0.58} 0 L${w * 0.62} -9 L${w * 0.65} 2 L${w * 0.69} -6 L${w * 0.73} 0`} stroke={color} strokeWidth="2.5" opacity="0.7" />
      <path d={`M${w * 0.28} ${h} L${w * 0.32} ${h + 8} L${w * 0.35} ${h - 2} L${w * 0.39} ${h + 6} L${w * 0.42} ${h}`} stroke={color} strokeWidth="2.5" opacity="0.8" />
      <path d={`M${w * 0.62} ${h} L${w * 0.65} ${h + 9} L${w * 0.69} ${h - 2} L${w * 0.72} ${h + 5} L${w * 0.76} ${h}`} stroke={color} strokeWidth="2.5" opacity="0.7" />
      <path d={`M0 ${h * 0.2} L-7 ${h * 0.24} L2 ${h * 0.28} L-6 ${h * 0.32} L0 ${h * 0.36}`} stroke={color} strokeWidth="2.5" opacity="0.7" />
      <path d={`M${w} ${h * 0.55} L${w + 7} ${h * 0.59} L${w - 2} ${h * 0.63} L${w + 6} ${h * 0.67} L${w} ${h * 0.71}`} stroke={color} strokeWidth="2.5" opacity="0.7" />
      {[[16, 16], [w - 16, 16], [16, h - 16], [w - 16, h - 16]].map(([cx, cy], i) => (
        <g key={i}>
          <circle cx={cx} cy={cy} r="4" fill={color} opacity="0.25" />
          <circle cx={cx} cy={cy} r="1.5" fill={color} opacity="0.7" />
        </g>
      ))}
      <path d={rrect(w - 6, h - 6, 11)} stroke={color} strokeWidth="0.8" opacity="0.3" transform="translate(3,3)" strokeDasharray="8 4" />
    </Frame>
  );
}

export function NeutralFrame({ color = '#7a7568', w = 200, h = 290 }: FrameProps) {
  return (
    <Frame w={w} h={h}>
      <path d={rrect(w, h)} stroke={color} strokeWidth="2" />
      <path d={rrect(w - 10, h - 10, 9)} stroke={color} strokeWidth="0.8" opacity="0.35" transform="translate(5,5)" />
      <rect x={w / 2 - 5} y="1" width="10" height="3" rx="1.5" fill={color} opacity="0.5" />
      <rect x={w / 2 - 5} y={h - 4} width="10" height="3" rx="1.5" fill={color} opacity="0.5" />
      <rect x="1" y={h / 2 - 5} width="3" height="10" rx="1.5" fill={color} opacity="0.5" />
      <rect x={w - 4} y={h / 2 - 5} width="3" height="10" rx="1.5" fill={color} opacity="0.5" />
    </Frame>
  );
}

export function EncounterFrame({ color = '#d4622a', w = 200, h = 290 }: FrameProps) {
  return (
    <Frame w={w} h={h}>
      <path d={rrect(w, h)} stroke={color} strokeWidth="2.5" />
      {[0.1, 0.28, 0.46, 0.64, 0.82].map((p, i) => (
        <path key={i} d={`M${w * p} ${h} Q${w * p + 3} ${h - 14 - i * 3} ${w * p + 8} ${h - 22 - i * 4} Q${w * p + 12} ${h - 10 - i * 2} ${w * p + 16} ${h}`} fill={color} opacity={0.2 + i * 0.03} />
      ))}
      <circle cx={w * 0.22} cy={h - 34} r="2.5" fill={color} opacity="0.5" />
      <circle cx={w * 0.55} cy={h - 40} r="2" fill={color} opacity="0.45" />
      <circle cx={w * 0.78} cy={h - 28} r="2.2" fill={color} opacity="0.4" />
      <line x1="4" y1="20" x2="11" y2="10" stroke={color} strokeWidth="2" opacity="0.5" strokeLinecap="round" />
      <line x1="7" y1="26" x2="13" y2="16" stroke={color} strokeWidth="1.5" opacity="0.4" strokeLinecap="round" />
      <line x1={w - 4} y1="20" x2={w - 11} y2="10" stroke={color} strokeWidth="2" opacity="0.5" strokeLinecap="round" />
      <line x1={w - 7} y1="26" x2={w - 13} y2="16" stroke={color} strokeWidth="1.5" opacity="0.4" strokeLinecap="round" />
    </Frame>
  );
}

export function StatFrame({ color = '#2a9cd4', w = 200, h = 290 }: FrameProps) {
  return (
    <Frame w={w} h={h}>
      <path d={rrect(w, h)} stroke={color} strokeWidth="2" />
      {[[14, 14], [w - 14, 14], [14, h - 14], [w - 14, h - 14]].map(([cx, cy], i) => (
        <g key={i}>
          <circle cx={cx} cy={cy} r="7" stroke={color} strokeWidth="1.5" fill="none" opacity="0.5" />
          <circle cx={cx} cy={cy} r="3" fill={color} opacity="0.5" />
        </g>
      ))}
      {[[w / 2, 0], [w / 2, h], [0, h / 2], [w, h / 2]].map(([cx, cy], i) => (
        <circle key={`m${i}`} cx={cx} cy={cy} r="4" stroke={color} strokeWidth="1.5" fill="none" opacity="0.4" />
      ))}
      <path d={rrect(w - 12, h - 12, 8)} stroke={color} strokeWidth="0.7" opacity="0.25" strokeDasharray="4 6" transform="translate(6,6)" />
    </Frame>
  );
}

// ── Class frames ──

export function MusicianFrame({ color = '#c4587a', w = 200, h = 290 }: FrameProps) {
  return (
    <Frame w={w} h={h}>
      {(clipId) => (
        <>
          <g clipPath={`url(#${clipId})`}>
            <g transform="translate(24, -2) rotate(-45, 0, 0)">
              <path d={`M0 16 Q-17 14 -17 0 Q-17 -8 -12 -12 Q-6 -15 -4 -15 L4 -15 Q6 -15 12 -12 Q17 -8 17 0 Q17 14 0 16 Z`} stroke={color} strokeWidth="2.5" fill={color} fillOpacity="0.15" opacity="0.8" />
              <circle cx="0" cy="2" r="5" stroke={color} strokeWidth="1.5" fill="none" opacity="0.5" />
              <circle cx="0" cy="2" r="2" fill={color} opacity="0.3" />
              <circle cx="0" cy="2" r="7" stroke={color} strokeWidth="0.6" fill="none" opacity="0.25" strokeDasharray="2 2" />
              <rect x="-6" y="10" width="12" height="2" rx="1" fill={color} opacity="0.35" />
              <line x1="0" y1="-15" x2="0" y2="-42" stroke={color} strokeWidth="4" opacity="0.7" strokeLinecap="round" />
              {[-20, -25, -30, -35].map((y, i) => (
                <line key={`f${i}`} x1="-3" y1={y} x2="3" y2={y} stroke={color} strokeWidth="1" opacity="0.35" />
              ))}
              <rect x="-5" y="-46" width="10" height="6" rx="2.5" fill={color} opacity="0.4" />
              <path d={`M-5 -44 Q-8 -44 -8 -42`} stroke={color} strokeWidth="1.5" opacity="0.3" fill="none" />
              <path d={`M5 -44 Q8 -44 8 -42`} stroke={color} strokeWidth="1.5" opacity="0.3" fill="none" />
              <circle cx="-6" cy="-43" r="1.5" fill={color} opacity="0.5" />
              <circle cx="-6" cy="-46" r="1.5" fill={color} opacity="0.5" />
              <circle cx="6" cy="-43" r="1.5" fill={color} opacity="0.5" />
              <circle cx="6" cy="-46" r="1.5" fill={color} opacity="0.5" />
              <line x1="-2" y1="10" x2="-2" y2="-40" stroke={color} strokeWidth="0.5" opacity="0.25" />
              <line x1="0" y1="10" x2="0" y2="-40" stroke={color} strokeWidth="0.5" opacity="0.2" />
              <line x1="2" y1="10" x2="2" y2="-40" stroke={color} strokeWidth="0.5" opacity="0.25" />
            </g>
            <g transform={`translate(${w - 24}, -2) rotate(45, 0, 0)`}>
              <path d={`M0 16 Q-17 14 -17 0 Q-17 -8 -12 -12 Q-6 -15 -4 -15 L4 -15 Q6 -15 12 -12 Q17 -8 17 0 Q17 14 0 16 Z`} stroke={color} strokeWidth="2.5" fill={color} fillOpacity="0.15" opacity="0.8" />
              <circle cx="0" cy="2" r="5" stroke={color} strokeWidth="1.5" fill="none" opacity="0.5" />
              <circle cx="0" cy="2" r="2" fill={color} opacity="0.3" />
              <circle cx="0" cy="2" r="7" stroke={color} strokeWidth="0.6" fill="none" opacity="0.25" strokeDasharray="2 2" />
              <rect x="-6" y="10" width="12" height="2" rx="1" fill={color} opacity="0.35" />
              <line x1="0" y1="-15" x2="0" y2="-42" stroke={color} strokeWidth="4" opacity="0.7" strokeLinecap="round" />
              {[-20, -25, -30, -35].map((y, i) => (
                <line key={`f${i}`} x1="-3" y1={y} x2="3" y2={y} stroke={color} strokeWidth="1" opacity="0.35" />
              ))}
              <rect x="-5" y="-46" width="10" height="6" rx="2.5" fill={color} opacity="0.4" />
              <path d={`M-5 -44 Q-8 -44 -8 -42`} stroke={color} strokeWidth="1.5" opacity="0.3" fill="none" />
              <path d={`M5 -44 Q8 -44 8 -42`} stroke={color} strokeWidth="1.5" opacity="0.3" fill="none" />
              <circle cx="-6" cy="-43" r="1.5" fill={color} opacity="0.5" />
              <circle cx="-6" cy="-46" r="1.5" fill={color} opacity="0.5" />
              <circle cx="6" cy="-43" r="1.5" fill={color} opacity="0.5" />
              <circle cx="6" cy="-46" r="1.5" fill={color} opacity="0.5" />
              <line x1="-2" y1="10" x2="-2" y2="-40" stroke={color} strokeWidth="0.5" opacity="0.25" />
              <line x1="0" y1="10" x2="0" y2="-40" stroke={color} strokeWidth="0.5" opacity="0.2" />
              <line x1="2" y1="10" x2="2" y2="-40" stroke={color} strokeWidth="0.5" opacity="0.25" />
            </g>
            <path d={`M${w * 0.05} ${h - 2} Q${w * 0.15} ${h - 16} ${w * 0.28} ${h - 2} Q${w * 0.4} ${h + 14} ${w * 0.52} ${h - 2} Q${w * 0.64} ${h - 14} ${w * 0.76} ${h - 2} Q${w * 0.88} ${h + 12} ${w * 0.97} ${h - 2}`} stroke={color} strokeWidth="2.5" opacity="0.45" fill="none" />
            <path d={`M${w * 0.12} ${h + 2} Q${w * 0.22} ${h + 10} ${w * 0.35} ${h + 2} Q${w * 0.48} ${h - 6} ${w * 0.6} ${h + 2} Q${w * 0.72} ${h + 8} ${w * 0.85} ${h + 2}`} stroke={color} strokeWidth="1.5" opacity="0.25" fill="none" />
          </g>
          <path d={rrect(w, h)} stroke={color} strokeWidth="2.5" />
        </>
      )}
    </Frame>
  );
}

export function DiscipleFrame({ color = '#e0c040', w = 200, h = 290 }: FrameProps) {
  const hw = w / 2;
  return (
    <Frame w={w} h={h}>
      {(clipId) => (
        <>
          <g clipPath={`url(#${clipId})`}>
            <circle cx={hw} cy="0" r="12" fill={color} opacity="0.1" />
            <circle cx={hw} cy="0" r="12" stroke={color} strokeWidth="2.5" fill="none" opacity="0.5" />
            <circle cx={hw} cy="0" r="6" stroke={color} strokeWidth="1.2" fill="none" opacity="0.35" />
            <circle cx={hw} cy="0" r="2.5" fill={color} opacity="0.5" />
            <line x1={hw} y1="-12" x2={hw} y2="-20" stroke={color} strokeWidth="3" opacity="0.7" strokeLinecap="round" />
            <line x1={hw - 9} y1="-8" x2={hw - 20} y2="-18" stroke={color} strokeWidth="2.5" opacity="0.6" strokeLinecap="round" />
            <line x1={hw + 9} y1="-8" x2={hw + 20} y2="-18" stroke={color} strokeWidth="2.5" opacity="0.6" strokeLinecap="round" />
            <line x1={hw - 16} y1="0" x2={hw - 30} y2="-8" stroke={color} strokeWidth="2" opacity="0.45" strokeLinecap="round" />
            <line x1={hw + 16} y1="0" x2={hw + 30} y2="-8" stroke={color} strokeWidth="2" opacity="0.45" strokeLinecap="round" />
            <line x1={hw - 5} y1="-12" x2={hw - 10} y2="-18" stroke={color} strokeWidth="1.5" opacity="0.35" strokeLinecap="round" />
            <line x1={hw + 5} y1="-12" x2={hw + 10} y2="-18" stroke={color} strokeWidth="1.5" opacity="0.35" strokeLinecap="round" />
            <circle cx={hw} cy={h} r="8" fill={color} opacity="0.08" />
            <circle cx={hw} cy={h} r="8" stroke={color} strokeWidth="1.5" fill="none" opacity="0.3" />
            <line x1={hw} y1={h + 8} x2={hw} y2={h + 3} stroke={color} strokeWidth="2" opacity="0.35" strokeLinecap="round" />
            <line x1={hw - 6} y1={h + 6} x2={hw - 3} y2={h + 2} stroke={color} strokeWidth="1.5" opacity="0.25" strokeLinecap="round" />
            <line x1={hw + 6} y1={h + 6} x2={hw + 3} y2={h + 2} stroke={color} strokeWidth="1.5" opacity="0.25" strokeLinecap="round" />
          </g>
          <path d={rrect(w, h)} stroke={color} strokeWidth="2.5" />
          <path d={rrect(w - 8, h - 8, 10)} stroke={color} strokeWidth="0.7" opacity="0.2" transform="translate(4,4)" />
        </>
      )}
    </Frame>
  );
}

export function WildbornFrame({ color = '#4aaa6a', w = 200, h = 290 }: FrameProps) {
  const thorn = '#3a8a5a';
  return (
    <Frame w={w} h={h}>
      {(clipId) => (
        <>
          <g clipPath={`url(#${clipId})`}>
            <path d={`M-6 ${h * 0.05} Q-14 ${h * 0.14} -8 ${h * 0.24} Q2 ${h * 0.34} -10 ${h * 0.44} Q-18 ${h * 0.54} -6 ${h * 0.64} Q4 ${h * 0.74} -12 ${h * 0.84} Q-16 ${h * 0.92} -4 ${h}`} stroke={color} strokeWidth="4" opacity="0.6" strokeLinecap="round" />
            <path d={`M-4 ${h * 0.12} Q-12 ${h * 0.2} -6 ${h * 0.3} Q2 ${h * 0.38} -8 ${h * 0.48}`} stroke={color} strokeWidth="1.8" opacity="0.3" strokeLinecap="round" />
            <g transform={`rotate(-40 -6 ${h * 0.2})`}>
              <ellipse cx="-6" cy={h * 0.2} rx="11" ry="5.5" fill={color} opacity="0.3" />
              <line x1="-13" y1={h * 0.2} x2="1" y2={h * 0.2} stroke={color} strokeWidth="0.8" opacity="0.5" />
              <line x1="-9" y1={h * 0.2 - 2.5} x2="-6" y2={h * 0.2} stroke={color} strokeWidth="0.6" opacity="0.35" />
              <line x1="-3" y1={h * 0.2 + 2.5} x2="-6" y2={h * 0.2} stroke={color} strokeWidth="0.6" opacity="0.35" />
            </g>
            <g transform={`rotate(35 -8 ${h * 0.42})`}>
              <ellipse cx="-8" cy={h * 0.42} rx="10" ry="5" fill={color} opacity="0.25" />
              <line x1="-14" y1={h * 0.42} x2="-2" y2={h * 0.42} stroke={color} strokeWidth="0.8" opacity="0.45" />
            </g>
            <g transform={`rotate(-30 -4 ${h * 0.6})`}>
              <ellipse cx="-4" cy={h * 0.6} rx="11" ry="5" fill={color} opacity="0.3" />
              <line x1="-11" y1={h * 0.6} x2="3" y2={h * 0.6} stroke={color} strokeWidth="0.8" opacity="0.5" />
              <line x1="-7" y1={h * 0.6 - 2} x2="-4" y2={h * 0.6} stroke={color} strokeWidth="0.6" opacity="0.35" />
            </g>
            <g transform={`rotate(25 -10 ${h * 0.8})`}>
              <ellipse cx="-10" cy={h * 0.8} rx="9" ry="4" fill={color} opacity="0.25" />
              <line x1="-16" y1={h * 0.8} x2="-4" y2={h * 0.8} stroke={color} strokeWidth="0.8" opacity="0.4" />
            </g>
            {[0.16, 0.3, 0.5, 0.7, 0.88].map((p, i) => (
              <path key={`tl${i}`} d={`M${-6 + (i % 2) * 4} ${h * p} L${-10 + (i % 2) * 2} ${h * p - 5}`} stroke={thorn} strokeWidth="1.8" opacity="0.45" strokeLinecap="round" />
            ))}
            <circle cx="-4" cy={h * 0.48} r="3" fill="#6ac080" opacity="0.3" />
            <circle cx="-4" cy={h * 0.48} r="1.5" fill="#90d8a0" opacity="0.4" />
            <path d={`M${w + 6} ${h * 0.12} Q${w + 14} ${h * 0.22} ${w + 8} ${h * 0.32} Q${w - 2} ${h * 0.42} ${w + 10} ${h * 0.52} Q${w + 16} ${h * 0.62} ${w + 6} ${h * 0.72} Q${w - 2} ${h * 0.82} ${w + 8} ${h * 0.92}`} stroke={color} strokeWidth="4" opacity="0.5" strokeLinecap="round" />
            <path d={`M${w + 4} ${h * 0.2} Q${w + 12} ${h * 0.28} ${w + 6} ${h * 0.36}`} stroke={color} strokeWidth="1.8" opacity="0.25" strokeLinecap="round" />
            <g transform={`rotate(35 ${w + 8} ${h * 0.28})`}>
              <ellipse cx={w + 8} cy={h * 0.28} rx="10" ry="5" fill={color} opacity="0.25" />
              <line x1={w + 2} y1={h * 0.28} x2={w + 14} y2={h * 0.28} stroke={color} strokeWidth="0.8" opacity="0.45" />
            </g>
            <g transform={`rotate(-25 ${w + 6} ${h * 0.5})`}>
              <ellipse cx={w + 6} cy={h * 0.5} rx="11" ry="5" fill={color} opacity="0.3" />
              <line x1={w} y1={h * 0.5} x2={w + 12} y2={h * 0.5} stroke={color} strokeWidth="0.8" opacity="0.5" />
              <line x1={w + 3} y1={h * 0.5 - 2} x2={w + 6} y2={h * 0.5} stroke={color} strokeWidth="0.6" opacity="0.35" />
            </g>
            <g transform={`rotate(30 ${w + 10} ${h * 0.72})`}>
              <ellipse cx={w + 10} cy={h * 0.72} rx="9" ry="4" fill={color} opacity="0.25" />
              <line x1={w + 4} y1={h * 0.72} x2={w + 16} y2={h * 0.72} stroke={color} strokeWidth="0.8" opacity="0.4" />
            </g>
            {[0.24, 0.44, 0.62, 0.82].map((p, i) => (
              <path key={`tr${i}`} d={`M${w + 6 + (i % 2) * 4} ${h * p} L${w + 10 + (i % 2) * 2} ${h * p - 5}`} stroke={thorn} strokeWidth="1.8" opacity="0.4" strokeLinecap="round" />
            ))}
            <circle cx={w + 6} cy={h * 0.62} r="2.5" fill="#6ac080" opacity="0.25" />
            <circle cx={w + 6} cy={h * 0.62} r="1.2" fill="#90d8a0" opacity="0.35" />
            <path d={`M${w * 0.25} 4 Q${w * 0.27} -8 ${w * 0.22} -14 Q${w * 0.2} -17 ${w * 0.22} -19`} stroke={color} strokeWidth="2.5" opacity="0.45" strokeLinecap="round" />
            <path d={`M${w * 0.75} 4 Q${w * 0.77} -10 ${w * 0.72} -16 Q${w * 0.7} -19 ${w * 0.73} -20`} stroke={color} strokeWidth="2.5" opacity="0.45" strokeLinecap="round" />
            <path d={`M${w * 0.5} 4 Q${w * 0.52} -4 ${w * 0.48} -8`} stroke={color} strokeWidth="1.8" opacity="0.3" strokeLinecap="round" />
          </g>
          <path d={rrect(w, h)} stroke={color} strokeWidth="2.5" />
        </>
      )}
    </Frame>
  );
}

export function WarriorFrame({ color = '#c43030', w = 200, h = 290 }: FrameProps) {
  const sx = 26;
  const sx2 = w - sx;
  return (
    <Frame w={w} h={h}>
      {(clipId) => (
        <>
          <g clipPath={`url(#${clipId})`}>
            <ellipse cx={sx} cy="-19" rx="5" ry="4" fill={color} opacity="0.55" stroke={color} strokeWidth="1.5" />
            <circle cx={sx} cy="-19" r="2" fill={color} opacity="0.3" />
            <rect x={sx - 3} y="-15" width="6" height="12" rx="2" fill={color} fillOpacity="0.2" stroke={color} strokeWidth="1.5" opacity="0.7" />
            {[-12, -9, -6].map((y, i) => (
              <line key={`gw${i}`} x1={sx - 3} y1={y} x2={sx + 3} y2={y + 2} stroke={color} strokeWidth="1" opacity="0.35" />
            ))}
            <rect x={sx - 17} y="-5" width="34" height="6" rx="2" fill={color} fillOpacity="0.2" stroke={color} strokeWidth="2" opacity="0.8" />
            <circle cx={sx - 17} cy="-2" r="4" fill={color} opacity="0.5" stroke={color} strokeWidth="1.2" />
            <circle cx={sx + 17} cy="-2" r="4" fill={color} opacity="0.5" stroke={color} strokeWidth="1.2" />
            <circle cx={sx - 17} cy="-2" r="1.5" fill={color} opacity="0.3" />
            <circle cx={sx + 17} cy="-2" r="1.5" fill={color} opacity="0.3" />
            <path d={`M${sx - 4.5} 1 L${sx - 3.5} 50 L${sx} 70 L${sx + 3.5} 50 L${sx + 4.5} 1`} fill={color} fillOpacity="0.2" stroke={color} strokeWidth="1.5" opacity="0.7" strokeLinejoin="round" />
            <line x1={sx} y1="2" x2={sx} y2="68" stroke={color} strokeWidth="0.8" opacity="0.4" />
            <ellipse cx={sx2} cy="-19" rx="5" ry="4" fill={color} opacity="0.55" stroke={color} strokeWidth="1.5" />
            <circle cx={sx2} cy="-19" r="2" fill={color} opacity="0.3" />
            <rect x={sx2 - 3} y="-15" width="6" height="12" rx="2" fill={color} fillOpacity="0.2" stroke={color} strokeWidth="1.5" opacity="0.7" />
            {[-12, -9, -6].map((y, i) => (
              <line key={`gw2${i}`} x1={sx2 - 3} y1={y} x2={sx2 + 3} y2={y + 2} stroke={color} strokeWidth="1" opacity="0.35" />
            ))}
            <rect x={sx2 - 17} y="-5" width="34" height="6" rx="2" fill={color} fillOpacity="0.2" stroke={color} strokeWidth="2" opacity="0.8" />
            <circle cx={sx2 - 17} cy="-2" r="4" fill={color} opacity="0.5" stroke={color} strokeWidth="1.2" />
            <circle cx={sx2 + 17} cy="-2" r="4" fill={color} opacity="0.5" stroke={color} strokeWidth="1.2" />
            <circle cx={sx2 - 17} cy="-2" r="1.5" fill={color} opacity="0.3" />
            <circle cx={sx2 + 17} cy="-2" r="1.5" fill={color} opacity="0.3" />
            <path d={`M${sx2 - 4.5} 1 L${sx2 - 3.5} 50 L${sx2} 70 L${sx2 + 3.5} 50 L${sx2 + 4.5} 1`} fill={color} fillOpacity="0.2" stroke={color} strokeWidth="1.5" opacity="0.7" strokeLinejoin="round" />
            <line x1={sx2} y1="2" x2={sx2} y2="68" stroke={color} strokeWidth="0.8" opacity="0.4" />
          </g>
          <path d={rrect(w, h)} stroke={color} strokeWidth="3" />
        </>
      )}
    </Frame>
  );
}

export function MonkFrame({ color = '#40b8a8', w = 200, h = 290 }: FrameProps) {
  const hw = w / 2;
  return (
    <Frame w={w} h={h}>
      {(clipId) => (
        <>
          <g clipPath={`url(#${clipId})`}>
            <path d={`M${hw - 5} 4 Q${hw} -24 ${hw + 5} 4`} stroke={color} strokeWidth="2" opacity="0.6" fill={color} fillOpacity="0.08" />
            <path d={`M${hw - 12} 4 Q${hw} -18 ${hw + 12} 4`} stroke={color} strokeWidth="2.5" opacity="0.65" fill={color} fillOpacity="0.06" />
            <path d={`M${hw - 18} 2 Q${hw - 10} -12 ${hw} -16 Q${hw + 10} -12 ${hw + 18} 2`} stroke={color} strokeWidth="2" opacity="0.45" fill={color} fillOpacity="0.04" />
            <path d={`M${hw - 24} 3 Q${hw - 14} -6 ${hw} -10 Q${hw + 14} -6 ${hw + 24} 3`} stroke={color} strokeWidth="1.5" opacity="0.3" fill="none" />
            <circle cx={hw} cy="-6" r="4" fill={color} opacity="0.25" />
            <circle cx={hw} cy="-6" r="2" fill={color} opacity="0.45" />
            <path d={`M${hw - 8} ${h - 4} Q${hw} ${h + 14} ${hw + 8} ${h - 4}`} stroke={color} strokeWidth="2" opacity="0.4" fill={color} fillOpacity="0.06" />
            <path d={`M${hw - 14} ${h - 2} Q${hw} ${h + 10} ${hw + 14} ${h - 2}`} stroke={color} strokeWidth="1.5" opacity="0.3" fill="none" />
            <circle cx={hw} cy={h + 3} r="3" fill={color} opacity="0.25" />
            <circle cx={-2} cy={h / 2} r="6" stroke={color} strokeWidth="1.5" fill={color} fillOpacity="0.08" opacity="0.4" />
            <circle cx={-2} cy={h / 2} r="2.5" fill={color} opacity="0.3" />
            <circle cx={w + 2} cy={h / 2} r="6" stroke={color} strokeWidth="1.5" fill={color} fillOpacity="0.08" opacity="0.4" />
            <circle cx={w + 2} cy={h / 2} r="2.5" fill={color} opacity="0.3" />
          </g>
          <path d={rrect(w, h)} stroke={color} strokeWidth="2" />
          {[[6, 6, 1, 1], [w - 6, 6, -1, 1], [6, h - 6, 1, -1], [w - 6, h - 6, -1, -1]].map(([x, y, dx, dy], i) => (
            <g key={i}>
              <line x1={x} y1={y} x2={x + dx * 22} y2={y} stroke={color} strokeWidth="1.8" opacity="0.5" />
              <line x1={x} y1={y} x2={x} y2={y + dy * 22} stroke={color} strokeWidth="1.8" opacity="0.5" />
              <line x1={x + dx * 6} y1={y + dy * 6} x2={x + dx * 18} y2={y + dy * 6} stroke={color} strokeWidth="1.2" opacity="0.35" />
              <line x1={x + dx * 6} y1={y + dy * 6} x2={x + dx * 6} y2={y + dy * 18} stroke={color} strokeWidth="1.2" opacity="0.35" />
              <circle cx={x + dx * 3} cy={y + dy * 3} r="1.5" fill={color} opacity="0.3" />
            </g>
          ))}
        </>
      )}
    </Frame>
  );
}

export function ArcherFrame({ color = '#5aaa40', w = 200, h = 290 }: FrameProps) {
  return (
    <Frame w={w} h={h}>
      {(clipId) => (
        <>
          <g clipPath={`url(#${clipId})`}>
            <path d={`M0 ${h * 0.08} Q-22 ${h * 0.5} 0 ${h * 0.92}`} stroke={color} strokeWidth="4.5" opacity="0.6" fill="none" strokeLinecap="round" />
            <path d={`M0 ${h * 0.08} Q-18 ${h * 0.5} 0 ${h * 0.92}`} stroke={color} strokeWidth="2" opacity="0.3" fill="none" />
            <path d={`M-2 ${h * 0.08} L-5 ${h * 0.06} L0 ${h * 0.05}`} stroke={color} strokeWidth="2" opacity="0.55" strokeLinecap="round" fill="none" />
            <path d={`M-2 ${h * 0.92} L-5 ${h * 0.94} L0 ${h * 0.95}`} stroke={color} strokeWidth="2" opacity="0.55" strokeLinecap="round" fill="none" />
            <line x1="0" y1={h * 0.08} x2="0" y2={h * 0.92} stroke={color} strokeWidth="1.2" opacity="0.4" />
            {[0.46, 0.48, 0.5, 0.52, 0.54].map((p, i) => (
              <line key={`bw${i}`} x1="-9" y1={h * p} x2="-4" y2={h * (p + 0.01)} stroke={color} strokeWidth="1.5" opacity="0.3" strokeLinecap="round" />
            ))}
            <line x1={w * 0.1} y1="-4" x2={w * 0.84} y2="-4" stroke={color} strokeWidth="3" opacity="0.65" strokeLinecap="round" />
            <path d={`M${w * 0.84} -4 L${w * 0.94} -4`} stroke={color} strokeWidth="2.5" opacity="0.65" strokeLinecap="round" />
            <path d={`M${w * 0.9} -11 L${w * 0.97} -4 L${w * 0.9} 3`} fill={color} opacity="0.55" stroke={color} strokeWidth="1" strokeLinejoin="round" />
            <line x1={w * 0.9} y1="-11" x2={w * 0.9} y2="3" stroke={color} strokeWidth="0.8" opacity="0.4" />
            <path d={`M${w * 0.14} -4 Q${w * 0.1} -11 ${w * 0.07} -13`} stroke={color} strokeWidth="1.8" opacity="0.45" fill="none" strokeLinecap="round" />
            <path d={`M${w * 0.14} -4 Q${w * 0.1} 3 ${w * 0.07} 5`} stroke={color} strokeWidth="1.8" opacity="0.45" fill="none" strokeLinecap="round" />
            <path d={`M${w * 0.18} -4 Q${w * 0.14} -10 ${w * 0.12} -11`} stroke={color} strokeWidth="1.2" opacity="0.35" fill="none" strokeLinecap="round" />
            <path d={`M${w * 0.18} -4 Q${w * 0.14} 2 ${w * 0.12} 3`} stroke={color} strokeWidth="1.2" opacity="0.35" fill="none" strokeLinecap="round" />
            {[0.26, 0.32, 0.38].map((p, i) => (
              <g key={`qa${i}`}>
                <line x1={w + 2} y1={h * p} x2={w + 2} y2={h * p + 28} stroke={color} strokeWidth="2" opacity={0.35 - i * 0.05} strokeLinecap="round" />
                <path d={`M${w - 1} ${h * p + 2} L${w + 2} ${h * p} L${w + 5} ${h * p + 2}`} stroke={color} strokeWidth="1.5" opacity={0.4 - i * 0.05} fill="none" />
              </g>
            ))}
          </g>
          <path d={rrect(w, h)} stroke={color} strokeWidth="2.5" />
        </>
      )}
    </Frame>
  );
}

export function RogueFrame({ color = '#8068a0', w = 200, h = 290 }: FrameProps) {
  const dx = 30;
  const dx2 = w - dx;
  return (
    <Frame w={w} h={h}>
      {(clipId) => (
        <>
          <g clipPath={`url(#${clipId})`}>
            <path d={`M${dx - 3} -18 L${dx} -22 L${dx + 3} -18 L${dx} -14 Z`} fill={color} opacity="0.55" stroke={color} strokeWidth="1.5" />
            <circle cx={dx} cy="-18" r="1.5" fill={color} opacity="0.3" />
            <rect x={dx - 2.5} y="-14" width="5" height="11" rx="1.5" fill={color} fillOpacity="0.2" stroke={color} strokeWidth="1.5" opacity="0.7" />
            {[-11, -7].map((y, i) => (
              <line key={`dg${i}`} x1={dx - 2.5} y1={y} x2={dx + 2.5} y2={y + 1.5} stroke={color} strokeWidth="0.8" opacity="0.35" />
            ))}
            <path d={`M${dx - 15} -5 Q${dx - 8} 0 ${dx} -3 Q${dx + 8} -6 ${dx + 15} -1`} stroke={color} strokeWidth="2.5" opacity="0.8" fill="none" strokeLinecap="round" />
            <circle cx={dx - 15} cy="-5" r="2.5" fill={color} opacity="0.45" stroke={color} strokeWidth="0.8" />
            <circle cx={dx + 15} cy="-1" r="2.5" fill={color} opacity="0.45" stroke={color} strokeWidth="0.8" />
            <path d={`M${dx - 3} -1 L${dx - 2} 40 L${dx} 58 L${dx + 2} 40 L${dx + 3} -1`} fill={color} fillOpacity="0.2" stroke={color} strokeWidth="1.2" opacity="0.65" strokeLinejoin="round" />
            <line x1={dx} y1="0" x2={dx} y2="56" stroke={color} strokeWidth="0.6" opacity="0.35" />
            <path d={`M${dx2 - 3} -18 L${dx2} -22 L${dx2 + 3} -18 L${dx2} -14 Z`} fill={color} opacity="0.55" stroke={color} strokeWidth="1.5" />
            <circle cx={dx2} cy="-18" r="1.5" fill={color} opacity="0.3" />
            <rect x={dx2 - 2.5} y="-14" width="5" height="11" rx="1.5" fill={color} fillOpacity="0.2" stroke={color} strokeWidth="1.5" opacity="0.7" />
            {[-11, -7].map((y, i) => (
              <line key={`dg2${i}`} x1={dx2 - 2.5} y1={y} x2={dx2 + 2.5} y2={y + 1.5} stroke={color} strokeWidth="0.8" opacity="0.35" />
            ))}
            <path d={`M${dx2 - 15} -1 Q${dx2 - 8} -6 ${dx2} -3 Q${dx2 + 8} 0 ${dx2 + 15} -5`} stroke={color} strokeWidth="2.5" opacity="0.8" fill="none" strokeLinecap="round" />
            <circle cx={dx2 - 15} cy="-1" r="2.5" fill={color} opacity="0.45" stroke={color} strokeWidth="0.8" />
            <circle cx={dx2 + 15} cy="-5" r="2.5" fill={color} opacity="0.45" stroke={color} strokeWidth="0.8" />
            <path d={`M${dx2 - 3} -1 L${dx2 - 2} 40 L${dx2} 58 L${dx2 + 2} 40 L${dx2 + 3} -1`} fill={color} fillOpacity="0.2" stroke={color} strokeWidth="1.2" opacity="0.65" strokeLinejoin="round" />
            <line x1={dx2} y1="0" x2={dx2} y2="56" stroke={color} strokeWidth="0.6" opacity="0.35" />
            <path d={`M-2 ${h * 0.2} Q-14 ${h * 0.32} -4 ${h * 0.44} Q6 ${h * 0.56} -6 ${h * 0.66} Q-14 ${h * 0.76} -2 ${h * 0.85}`} stroke={color} strokeWidth="2.5" opacity="0.3" strokeLinecap="round" />
            <path d={`M${w + 2} ${h * 0.28} Q${w + 12} ${h * 0.4} ${w + 4} ${h * 0.52} Q${w - 4} ${h * 0.62} ${w + 6} ${h * 0.72} Q${w + 12} ${h * 0.8} ${w + 2} ${h * 0.88}`} stroke={color} strokeWidth="2.5" opacity="0.25" strokeLinecap="round" />
          </g>
          <path d={rrect(w, h)} stroke={color} strokeWidth="2" />
        </>
      )}
    </Frame>
  );
}

export function CorruptorFrame({ color = '#8a40c0', w = 200, h = 290 }: FrameProps) {
  const gc = '#50b050';
  return (
    <Frame w={w} h={h}>
      {(clipId) => (
        <>
          <g clipPath={`url(#${clipId})`}>
            <path d={`M-8 ${h * 0.02} Q-18 ${h * 0.1} -10 ${h * 0.2} Q4 ${h * 0.28} -8 ${h * 0.36} Q-16 ${h * 0.44} -2 ${h * 0.52} Q8 ${h * 0.58} -6 ${h * 0.66} Q-14 ${h * 0.72} -4 ${h * 0.8}`} stroke={color} strokeWidth="5.5" opacity="0.55" strokeLinecap="round" />
            {[0.1, 0.2, 0.32, 0.44, 0.56, 0.68, 0.76].map((p, i) => (
              <g key={`sl${i}`}>
                <circle cx={-8 + (i % 2) * 8} cy={h * p} r={4 - i * 0.3} fill={color} opacity="0.2" />
                <circle cx={-8 + (i % 2) * 8} cy={h * p} r={1.8} stroke={color} strokeWidth="0.8" fill="none" opacity="0.3" />
              </g>
            ))}
            <path d={`M${w + 8} ${h * 0.18} Q${w + 16} ${h * 0.28} ${w + 6} ${h * 0.38} Q${w - 4} ${h * 0.46} ${w + 6} ${h * 0.54} Q${w + 14} ${h * 0.62} ${w + 4} ${h * 0.72} Q${w - 4} ${h * 0.8} ${w + 4} ${h * 0.88}`} stroke={color} strokeWidth="5.5" opacity="0.5" strokeLinecap="round" />
            {[0.28, 0.38, 0.5, 0.62, 0.74, 0.84].map((p, i) => (
              <g key={`sr${i}`}>
                <circle cx={w + 8 - (i % 2) * 8} cy={h * p} r={4 - i * 0.3} fill={color} opacity="0.18" />
                <circle cx={w + 8 - (i % 2) * 8} cy={h * p} r={1.8} stroke={color} strokeWidth="0.8" fill="none" opacity="0.25" />
              </g>
            ))}
            <path d={`M${w * 0.15} ${h - 4} Q${w * 0.12} ${h + 14} ${w * 0.2} ${h + 18} Q${w * 0.28} ${h + 12} ${w * 0.24} ${h + 20}`} stroke={gc} strokeWidth="4" opacity="0.4" strokeLinecap="round" />
            <path d={`M${w * 0.45} ${h - 2} Q${w * 0.5} ${h + 14} ${w * 0.42} ${h + 18}`} stroke={gc} strokeWidth="3.5" opacity="0.35" strokeLinecap="round" />
            <path d={`M${w * 0.7} ${h - 2} Q${w * 0.74} ${h + 12} ${w * 0.68} ${h + 16}`} stroke={gc} strokeWidth="3.5" opacity="0.35" strokeLinecap="round" />
            <path d={`M${w * 0.88} ${h - 2} Q${w * 0.92} ${h + 8} ${w * 0.86} ${h + 12}`} stroke={gc} strokeWidth="2.5" opacity="0.25" strokeLinecap="round" />
            <path d={`M2 ${h * 0.68} Q-6 ${h * 0.74} -2 ${h * 0.82} Q6 ${h * 0.88} -4 ${h * 0.94} Q-8 ${h * 0.98} 0 ${h + 4}`} stroke={gc} strokeWidth="2.5" opacity="0.35" strokeLinecap="round" />
            <path d={`M${w - 2} ${h * 0.78} Q${w + 6} ${h * 0.84} ${w + 2} ${h * 0.9} Q${w - 4} ${h * 0.96} ${w + 2} ${h + 4}`} stroke={gc} strokeWidth="2.5" opacity="0.3" strokeLinecap="round" />
          </g>
          <path d={rrect(w, h)} stroke={color} strokeWidth="2.5" />
        </>
      )}
    </Frame>
  );
}

export function WizardFrame({ color = '#4060d4', w = 200, h = 290 }: FrameProps) {
  return (
    <Frame w={w} h={h}>
      <path d={rrect(w, h)} stroke={color} strokeWidth="2" />
      {[0.15, 0.3, 0.45, 0.6, 0.75, 0.85].map((p, i) => {
        const x = w * p;
        return i % 3 === 0 ? (
          <g key={i}>
            <line x1={x} y1="3" x2={x} y2="12" stroke={color} strokeWidth="2" opacity="0.5" />
            <line x1={x - 4} y1="7" x2={x + 4} y2="7" stroke={color} strokeWidth="1.8" opacity="0.45" />
          </g>
        ) : i % 3 === 1 ? (
          <circle key={i} cx={x} cy="7" r="4" stroke={color} strokeWidth="1.5" fill="none" opacity="0.45" />
        ) : (
          <g key={i}>
            <path d={`M${x - 3} 4 L${x} 12 L${x + 3} 4`} stroke={color} strokeWidth="1.5" fill="none" opacity="0.45" />
          </g>
        );
      })}
      {[0.2, 0.38, 0.56, 0.74, 0.9].map((p, i) => {
        const x = w * p;
        return i % 2 === 0 ? (
          <g key={`b${i}`}>
            <line x1={x - 5} y1={h - 7} x2={x + 5} y2={h - 7} stroke={color} strokeWidth="2" opacity="0.45" />
            <line x1={x} y1={h - 3} x2={x} y2={h - 12} stroke={color} strokeWidth="2" opacity="0.45" />
          </g>
        ) : (
          <circle key={`b${i}`} cx={x} cy={h - 7} r="3.5" stroke={color} strokeWidth="1.5" fill="none" opacity="0.4" />
        );
      })}
      {[0.2, 0.4, 0.6, 0.8].map((p, i) => (
        <g key={`l${i}`}>
          <line x1="3" y1={h * p} x2="10" y2={h * p} stroke={color} strokeWidth="1.5" opacity="0.35" />
          <line x1="6" y1={h * p - 4} x2="6" y2={h * p + 4} stroke={color} strokeWidth="1.5" opacity="0.35" />
          <line x1={w - 3} y1={h * p + 14} x2={w - 10} y2={h * p + 14} stroke={color} strokeWidth="1.5" opacity="0.35" />
          <line x1={w - 6} y1={h * p + 10} x2={w - 6} y2={h * p + 18} stroke={color} strokeWidth="1.5" opacity="0.35" />
        </g>
      ))}
      <path d="M4 24 A20 20 0 0 1 24 4" stroke={color} strokeWidth="2" opacity="0.5" strokeDasharray="4 3" />
      <path d={`M${w - 4} 24 A20 20 0 0 0 ${w - 24} 4`} stroke={color} strokeWidth="2" opacity="0.5" strokeDasharray="4 3" />
      <path d={`M4 ${h - 24} A20 20 0 0 0 24 ${h - 4}`} stroke={color} strokeWidth="2" opacity="0.5" strokeDasharray="4 3" />
      <path d={`M${w - 4} ${h - 24} A20 20 0 0 1 ${w - 24} ${h - 4}`} stroke={color} strokeWidth="2" opacity="0.5" strokeDasharray="4 3" />
      <circle cx="14" cy="14" r="2" fill={color} opacity="0.6" />
      <circle cx={w - 14} cy="14" r="2" fill={color} opacity="0.6" />
      <circle cx="14" cy={h - 14} r="2" fill={color} opacity="0.6" />
      <circle cx={w - 14} cy={h - 14} r="2" fill={color} opacity="0.6" />
    </Frame>
  );
}

export function WraithHunterFrame({ color = '#90a8b8', w = 200, h = 290 }: FrameProps) {
  const hw = w / 2;
  return (
    <Frame w={w} h={h}>
      {(clipId) => (
        <>
          <g clipPath={`url(#${clipId})`}>
            <circle cx={hw} cy="0" r="14" fill={color} fillOpacity="0.1" />
            <circle cx={hw} cy="0" r="14" stroke={color} strokeWidth="2.5" fill="none" opacity="0.75" />
            <circle cx={hw} cy="0" r="9" stroke={color} strokeWidth="1.5" fill="none" opacity="0.45" />
            <circle cx={hw} cy="0" r="4" fill={color} opacity="0.5" />
            <line x1={hw} y1="-10" x2={hw} y2="-18" stroke={color} strokeWidth="2" opacity="0.5" strokeLinecap="round" />
            <line x1={hw - 10} y1="-2" x2={hw - 16} y2="-6" stroke={color} strokeWidth="1.8" opacity="0.4" strokeLinecap="round" />
            <line x1={hw + 10} y1="-2" x2={hw + 16} y2="-6" stroke={color} strokeWidth="1.8" opacity="0.4" strokeLinecap="round" />
            <line x1={hw} y1="-14" x2={hw - 6} y2="-19" stroke={color} strokeWidth="1.2" opacity="0.3" strokeLinecap="round" />
            <line x1={hw} y1="-14" x2={hw + 6} y2="-19" stroke={color} strokeWidth="1.2" opacity="0.3" strokeLinecap="round" />
            {[0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9].map((p, i) => (
              <g key={`l${i}`}>
                <ellipse cx="-2" cy={h * p} rx="7" ry="9" stroke={color} strokeWidth="2.2" fill="none" opacity={0.3 + (i % 2) * 0.12} />
                {i % 3 === 0 && (
                  <g>
                    <line x1="-6" y1={h * p} x2="-12" y2={h * p} stroke={color} strokeWidth="1.5" opacity="0.3" />
                    <line x1="-9" y1={h * p - 3} x2="-9" y2={h * p + 3} stroke={color} strokeWidth="1" opacity="0.25" />
                  </g>
                )}
              </g>
            ))}
            {[0.15, 0.25, 0.35, 0.45, 0.55, 0.65, 0.75, 0.85, 0.95].map((p, i) => (
              <g key={`r${i}`}>
                <ellipse cx={w + 2} cy={h * p} rx="7" ry="9" stroke={color} strokeWidth="2.2" fill="none" opacity={0.3 + (i % 2) * 0.12} />
                {i % 3 === 0 && (
                  <g>
                    <line x1={w + 6} y1={h * p} x2={w + 12} y2={h * p} stroke={color} strokeWidth="1.5" opacity="0.3" />
                    <line x1={w + 9} y1={h * p - 3} x2={w + 9} y2={h * p + 3} stroke={color} strokeWidth="1" opacity="0.25" />
                  </g>
                )}
              </g>
            ))}
            <circle cx={hw} cy={h} r="8" stroke={color} strokeWidth="1.8" fill={color} fillOpacity="0.08" opacity="0.4" />
            <circle cx={hw} cy={h} r="3.5" fill={color} opacity="0.3" />
            <line x1={hw} y1={h + 8} x2={hw} y2={h + 14} stroke={color} strokeWidth="1.5" opacity="0.25" strokeLinecap="round" />
          </g>
          <path d={rrect(w, h)} stroke={color} strokeWidth="2.5" />
          <path d={rrect(w - 10, h - 10, 9)} stroke={color} strokeWidth="0.7" opacity="0.2" transform="translate(5,5)" />
        </>
      )}
    </Frame>
  );
}

export function BattlemageFrame({ color = '#a040c0', w = 200, h = 290 }: FrameProps) {
  const m = '#d04040';
  const a = '#4060d0';
  const hw = w / 2;
  const stx = w + 8;
  return (
    <Frame w={w} h={h}>
      {(clipId) => (
        <>
          <g clipPath={`url(#${clipId})`}>
            <path d={`M2 ${h * 0.24} L-10 ${h * 0.28} L-16 ${h * 0.46} L-8 ${h * 0.7} L2 ${h * 0.64} Z`} stroke={m} strokeWidth="2.5" fill={m} fillOpacity="0.12" opacity="0.65" strokeLinejoin="round" />
            <path d={`M0 ${h * 0.3} L-8 ${h * 0.34} L-12 ${h * 0.44} L-6 ${h * 0.62} L0 ${h * 0.58} Z`} stroke={m} strokeWidth="1" fill="none" opacity="0.3" strokeLinejoin="round" />
            <line x1="-8" y1={h * 0.34} x2="-8" y2={h * 0.62} stroke={m} strokeWidth="1.5" opacity="0.35" />
            <line x1="-12" y1={h * 0.46} x2="0" y2={h * 0.46} stroke={m} strokeWidth="1.5" opacity="0.3" />
            <circle cx="-6" cy={h * 0.46} r="2.5" fill={m} opacity="0.25" />
            <line x1={stx} y1={h * 0.08} x2={stx} y2={h * 0.88} stroke={a} strokeWidth="4.5" opacity="0.65" strokeLinecap="round" />
            <line x1={stx - 1} y1={h * 0.2} x2={stx - 1} y2={h * 0.85} stroke={a} strokeWidth="0.6" opacity="0.2" />
            <line x1={stx + 1.5} y1={h * 0.25} x2={stx + 1.5} y2={h * 0.8} stroke={a} strokeWidth="0.6" opacity="0.15" />
            {[0.48, 0.51, 0.54, 0.57, 0.6, 0.63].map((p, i) => (
              <line key={`sw${i}`} x1={stx - 3} y1={h * p} x2={stx + 3} y2={h * (p + 0.012)} stroke={a} strokeWidth="1.8" opacity="0.35" strokeLinecap="round" />
            ))}
            <circle cx={stx} cy={h * 0.08} r="13" fill={a} fillOpacity="0.06" />
            <circle cx={stx} cy={h * 0.08} r="13" stroke={a} strokeWidth="2" fill="none" opacity="0.55" />
            <circle cx={stx} cy={h * 0.08} r="9" stroke={a} strokeWidth="1.2" fill="none" opacity="0.35" strokeDasharray="3 2" />
            <circle cx={stx} cy={h * 0.08} r="5" fill={a} opacity="0.4" />
            <circle cx={stx} cy={h * 0.08} r="2.5" fill="#8090ff" opacity="0.6" />
            {[[-8, -10], [11, -6], [9, 11], [-6, 9], [0, -14], [13, 2]].map(([ddx, dy], i) => (
              <g key={`sp${i}`}>
                <circle cx={stx + ddx} cy={h * 0.08 + dy} r="1.5" fill={a} opacity={0.45 - i * 0.04} />
                <line x1={stx + ddx - 2} y1={h * 0.08 + dy} x2={stx + ddx + 2} y2={h * 0.08 + dy} stroke={a} strokeWidth="0.5" opacity={0.3 - i * 0.03} />
              </g>
            ))}
            <path d={`M${stx - 2} ${h * 0.08 + 13} Q${stx - 6} ${h * 0.08 + 6} ${stx - 5} ${h * 0.08 - 4}`} stroke={a} strokeWidth="1.5" opacity="0.45" fill="none" />
            <path d={`M${stx + 2} ${h * 0.08 + 13} Q${stx + 6} ${h * 0.08 + 6} ${stx + 5} ${h * 0.08 - 4}`} stroke={a} strokeWidth="1.5" opacity="0.45" fill="none" />
            <path d={`M${stx - 3} ${h * 0.86} L${stx} ${h * 0.92} L${stx + 3} ${h * 0.86}`} stroke={a} strokeWidth="2" opacity="0.45" fill={a} fillOpacity="0.15" />
            <circle cx={hw} cy="0" r="6" fill={color} opacity="0.4" />
            <circle cx={hw} cy="0" r="3" fill="#fff" opacity="0.2" />
            <line x1={hw - 20} y1="0" x2={hw - 6} y2="0" stroke={m} strokeWidth="2.5" opacity="0.5" strokeLinecap="round" />
            <line x1={hw + 6} y1="0" x2={hw + 20} y2="0" stroke={a} strokeWidth="2.5" opacity="0.5" strokeLinecap="round" />
          </g>
          <path d={`M14 0 H${hw}`} stroke={m} strokeWidth="3" opacity="0.85" />
          <path d={`M${hw} 0 H${w - 14} Q${w} 0 ${w} 14`} stroke={a} strokeWidth="3" opacity="0.85" />
          <path d={`M0 14 Q0 0 14 0`} stroke={m} strokeWidth="3" opacity="0.85" />
          <path d={`M0 14 V${h - 14} Q0 ${h} 14 ${h}`} stroke={m} strokeWidth="3" opacity="0.85" />
          <path d={`M${w} 14 V${h - 14} Q${w} ${h} ${w - 14} ${h}`} stroke={a} strokeWidth="3" opacity="0.85" />
          <path d={`M14 ${h} H${hw}`} stroke={m} strokeWidth="3" opacity="0.85" />
          <path d={`M${hw} ${h} H${w - 14}`} stroke={a} strokeWidth="3" opacity="0.85" />
        </>
      )}
    </Frame>
  );
}

// ── Resolver ──

type FrameComponent = (props: FrameProps) => React.JSX.Element;

const BASE_FRAMES: Record<string, FrameComponent> = {
  STEEL_CRITICAL: SteelCriticalFrame,
  MIGHT_CRITICAL: MightCriticalFrame,
  NEUTRAL: NeutralFrame,
  ENCOUNTER: EncounterFrame,
  STAT: StatFrame,
};
const CLASS_FRAMES: Record<string, FrameComponent> = {
  musician: MusicianFrame,
  disciple: DiscipleFrame,
  wildborn: WildbornFrame,
  warrior: WarriorFrame,
  monk: MonkFrame,
  archer: ArcherFrame,
  rogue: RogueFrame,
  corruptor: CorruptorFrame,
  wizard: WizardFrame,
  wraith_hunter: WraithHunterFrame,
  battlemage: BattlemageFrame,
};

export function getFrameComponent(cardType: string, themeId?: string): FrameComponent {
  if (cardType === 'CLASS' && themeId && CLASS_FRAMES[themeId]) return CLASS_FRAMES[themeId];
  return BASE_FRAMES[cardType] ?? NeutralFrame;
}
