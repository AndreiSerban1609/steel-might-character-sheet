# Testing in Owlbear Rodeo (Cloudflare Tunnel + GitHub Pages)

An OBR extension runs as an **HTTPS iframe** inside owlbear.rodeo, so both halves must be
served over HTTPS:

- **Frontend** (this SPA) → GitHub Pages (static, stable HTTPS URL — used in the OBR manifest).
- **Backend** (Spring Boot) → stays on your machine, exposed through a **Cloudflare Tunnel**
  (outbound-only; **no router ports opened**).

> Identity note: OBR-native auto-login (room/player id from the OBR SDK) isn't wired yet, so in
> the iframe testers still type their room + email on the Entry screen. That's expected for now.

---

## 1. Publish the frontend to GitHub Pages

Deployment is automated by `.github/workflows/deploy.yml`. **One-time setup:** on GitHub →
Settings → Pages → **Source: GitHub Actions**. After that, every push to `main` that touches the
frontend builds and deploys automatically (or run it manually via the Actions tab → "Deploy to
GitHub Pages" → Run workflow).

```bash
git push origin main   # → Actions builds (npm ci && npm run build) and publishes dist/
```

To build locally for a sanity check: `npm run build` (vite base is `./`, so a subpath works).

Your site will be at `https://andreiserban1609.github.io/<repo>/`. The committed
`public/manifest.json` assumes the repo is **`steel-might-character-sheet`** — if it differs,
update the URLs (`homepage_url`, `icon`, `action.icon`, `action.popover`) to match.

Confirm these load in a browser:
- `…/<repo>/manifest.json`
- `…/<repo>/icon.svg`
- `…/<repo>/index.html`

## 2. Run the backend + tunnel

```bash
# from server/ — dev profile, file-backed H2. Do NOT set H2_CONSOLE (console stays off).
./gradlew bootRun

# in another shell — install cloudflared first (https://developers.cloudflare.com/cloudflare-one/)
cloudflared tunnel --url http://localhost:8080
```

`cloudflared` prints `https://<random>.trycloudflare.com`. Each run gives a **new** URL.

CORS already allows `https://andreiserban1609.github.io`. If your frontend origin differs, start
the backend with `CORS_ORIGINS=https://your-origin` (comma-separated for several).

Sanity-check the tunnel reaches the API:

```bash
curl https://<random>.trycloudflare.com/api/characters/roster
```

## 3. Point the app at the backend

The frontend resolves its backend URL at runtime (no rebuild needed). Either:
- **In-app**: open the app, expand **“Server connection”**, paste the tunnel URL, **Apply**
  (persisted in localStorage), or
- **URL param**: append `?api=https://<random>.trycloudflare.com` — handy to bake into the
  manifest's `action.popover` so the iframe is pre-pointed.

A pasted root URL gets `/api` appended automatically.

> Browser storage is partitioned per top-level site, so a value you set by visiting the Pages URL
> directly may **not** carry into the OBR iframe. Most reliable inside OBR: set it via the
> connection field **while in the OBR popover**, or put `?api=…` in the manifest popover URL.

## 4. Install in Owlbear

In your OBR room → add a custom extension by URL → paste:

```
https://andreiserban1609.github.io/<repo>/manifest.json
```

Open it from the toolbar, set the Server connection (step 3), then enter room + email.

---

## Security checklist (the API is unauthenticated by design)

- ✅ **H2 console is off** unless you explicitly set `H2_CONSOLE=true` (never do that while tunneled).
- ✅ **CORS** is locked to specific origins (`CORS_ORIGINS`), never `*`.
- ⚠️ Anyone with the tunnel URL can read/write character data — treat it as **public**: use the
  ephemeral `trycloudflare.com` URL, no real/sensitive data, and **stop the tunnel when done**
  (Ctrl-C). For a longer-lived, named tunnel use a Cloudflare account + your own domain.
- Next step toward sharing with a table: host the backend (Fly.io/Render) with the `prod` profile
  (Postgres) and a stable HTTPS URL.
