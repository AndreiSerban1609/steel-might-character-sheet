import { useState } from 'react';
import { getApiBase, setApiBase } from '../platform/http';

/** Collapsible backend-URL field (Entry screen + offline banner).
 *  `reloadOnApply` reruns the full OBR bootstrap against the new URL —
 *  needed mid-session, where stores already hold state from the old server. */
export function ServerConnection({ reloadOnApply = false }: { reloadOnApply?: boolean }) {
  const [apiInput, setApiInput] = useState(getApiBase());
  const [apiSaved, setApiSaved] = useState(false);

  function applyApi() {
    setApiBase(apiInput);
    setApiInput(getApiBase());
    setApiSaved(true);
    if (reloadOnApply) window.location.reload();
  }

  return (
    <details className="conn">
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
