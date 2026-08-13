// Ported from the Deck of Fates extension (src/components/DiceRoll.jsx).
// Animated die: spins through random faces, then settles on the result. The d10 is the
// skill-check die; `sides={20}` gives attack rolls the same treatment (demo feedback #17)
// with a twenty-sided silhouette instead of the hexagonal one.
import { useState, useEffect, useRef, type CSSProperties } from 'react';

interface DiceRollProps {
  result: number;
  rolling: boolean;
  size?: number;
  /** Faces to spin through, and which silhouette to draw. */
  sides?: 10 | 20;
  onRollComplete?: () => void;
}

export function DiceRoll({ result, rolling, size = 120, sides = 10, onRollComplete }: DiceRollProps) {
  const [displayNumber, setDisplayNumber] = useState<number>(result || 1);
  const intervalRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  // Hold the latest callback in a ref so the animation effect doesn't restart
  // when the parent re-renders with a new closure.
  const onCompleteRef = useRef(onRollComplete);
  useEffect(() => {
    onCompleteRef.current = onRollComplete;
  });

  useEffect(() => {
    if (!rolling) return;

    let elapsed = 0;
    const spinDuration = 900;
    const settlePause = 500;
    let delay = 50;

    function tick() {
      elapsed += delay;
      if (elapsed >= spinDuration) {
        setDisplayNumber(result);
        timeoutRef.current = setTimeout(() => {
          onCompleteRef.current?.();
        }, settlePause);
        return;
      }
      const progress = elapsed / spinDuration;
      // Increasingly likely to flash the real number as we approach the end.
      if (progress > 0.6 && Math.random() < (progress - 0.6) * 2) {
        setDisplayNumber(result);
      } else {
        setDisplayNumber(Math.floor(Math.random() * sides) + 1);
      }
      delay = 50 + progress * 200;
      intervalRef.current = setTimeout(tick, delay);
    }

    intervalRef.current = setTimeout(tick, delay);

    return () => {
      if (intervalRef.current) clearTimeout(intervalRef.current);
      if (timeoutRef.current) clearTimeout(timeoutRef.current);
    };
  }, [rolling, result, sides]);

  useEffect(() => {
    if (!rolling && result != null) setDisplayNumber(result);
  }, [rolling, result]);

  const s = size;
  const settled = !rolling && result != null;
  // d20s read as a triangle-faced icosahedron in profile; d10s keep the flat hex.
  const hex =
    sides === 20
      ? 'polygon(50% 0%, 93% 25%, 93% 75%, 50% 100%, 7% 75%, 7% 25%)'
      : 'polygon(50% 0%, 95% 30%, 95% 70%, 50% 100%, 5% 70%, 5% 30%)';

  const dieStyle: CSSProperties = {
    width: s,
    height: s,
    clipPath: hex,
    background: rolling
      ? 'linear-gradient(135deg, #1a1a2e 0%, #2a1a0a 50%, #1a1a2e 100%)'
      : 'linear-gradient(135deg, #2e1a0a 0%, #5c3a10 40%, #8b6a20 60%, #2e1a0a 100%)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    position: 'relative',
    transition: 'background 0.3s ease',
    animation: rolling ? 'diceJitter 0.1s ease-in-out infinite' : 'none',
  };

  const borderStyle: CSSProperties = {
    position: 'absolute',
    inset: 3,
    clipPath: hex,
    border: '1px solid',
    borderColor: settled ? '#e0a040' : '#7a6830',
    background: 'transparent',
    transition: 'border-color 0.3s ease',
    pointerEvents: 'none',
  };

  const numberStyle: CSSProperties = {
    // Two-digit d20 results need to fit the same face.
    fontSize: s * (sides === 20 ? 0.36 : 0.42),
    fontWeight: 700,
    color: settled ? '#fff' : '#d4c9a8',
    fontFamily: "'Cinzel', 'Palatino', serif",
    textShadow: settled
      ? '0 0 16px rgba(224, 160, 64, 0.8), 0 0 32px rgba(224, 160, 64, 0.4)'
      : 'none',
    transition: 'text-shadow 0.3s ease, color 0.3s ease',
    zIndex: 2,
    animation: settled ? 'diceSettle 0.4s ease-out' : 'none',
  };

  return (
    <div style={dieStyle} aria-label={`d${sides}: ${settled ? result : 'rolling'}`}>
      <div style={borderStyle} />
      <span style={numberStyle}>{displayNumber}</span>
    </div>
  );
}
