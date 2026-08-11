import type { ReactNode } from 'react';

/**
 * Inline wrapper that reveals a small tooltip on hover (and keyboard focus when
 * the wrapper isn't already inside a focusable control). Pure CSS show/hide —
 * see `.hoverinfo` in index.css. Renders nothing extra when `info` is empty.
 */
export function HoverInfo({
  info,
  children,
  focusable = true,
}: {
  info: ReactNode;
  children: ReactNode;
  focusable?: boolean;
}) {
  if (!info) return <>{children}</>;
  return (
    <span className="hoverinfo" tabIndex={focusable ? 0 : undefined}>
      {children}
      <span className="hoverinfo-pop" role="tooltip">
        {info}
      </span>
    </span>
  );
}
