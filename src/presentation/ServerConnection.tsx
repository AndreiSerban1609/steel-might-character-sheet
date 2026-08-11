import { useState } from 'react';
import { getApiBase, setApiBase } from '../platform/http';

/** Collapsible backend-URL field (Entry screen + offline banner).
 *  `reloadOnApply` reruns the full OBR bootstrap against the new URL —
 *  needed mid-session, where stores already hold state from the old server.
 *  `forceOpen` expands the box (entry gate failed — the field IS the fix);
 *  `onApply` lets the parent re-run the blocked step with the new URL. */
export function ServerConnection({
  reloadOnApply = false,
  forceOpen = false,
  onApply,
}: {
  reloadOnApply?: boolean;
  forceOpen?: boolean;
  onApply?: () => void;
}) {
  const [apiInput, setApiInput] = useState(getApiBase());
  const [apiSaved, setApiSaved] = useState(false);

  function applyApi() {
    setApiBase(apiInput);
    setApiInput(getApiBase());
    setApiSaved(true);
    if (reloadOnApply) window.location.reload();
    else onApply?.();
  }

  return (
    <details className="conn" open={forceOpen || undefined}>
      <summary>Server connection</summary>
      <p className="conn-hint">
        Running inside Owlbear or against a hosted backend? Paste the backend URL (e.g. your Cloudflare
        Tunnel URL). Leave as <code>/api</code> for local development.
      </p>
      <div className="conn-row">
        <input
          value={apiInput}
          onChange={(e) => {
            setApiInput(e.target.value);
            setApiSaved(false);
          }}
          placeholder="https://xxxx.trycloudflare.com"
        />
        <button className="btn btn--ghost" onClick={applyApi}>
          {apiSaved ? 'Saved' : reloadOnApply ? 'Apply & reload' : 'Apply'}
        </button>
      </div>
    </details>
  );
}
