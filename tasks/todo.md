# Android app (standalone) + GitHub releases

Plan: data layer runs unchanged on both server (better-sqlite3) and phone (sql.js);
one API route table serves both the SvelteKit server and an in-process phone transport.

- [x] 1. Move queries/weather/seed/security to `src/lib/data`, add swappable `db`
- [x] 2. Single API route table (`src/lib/data/api.ts`) + catch-all server route
- [x] 3. `api()` client transport; `+page.server.ts` → `+page.ts` loads
- [x] 4. On-device sql.js database with migrations, seed, persistence
- [x] 5. Mobile build target (adapter-static, no PWA/service worker)
- [x] 6. Capacitor Android project, icons, back button, safe areas, system bar style
- [x] 7. Release keystore (`.signing/`, gitignored) + GitHub Actions release workflow
- [x] 8. Docs (AGENTS.md)
- [x] Verify (see review)
- [ ] Publish: push + tag v0.1.0 (needs GitHub credentials + secrets from user)

## Review

- `npm run check`: only the 3 pre-existing errors in `src/lib/stores/locale.ts` (untouched).
- Web: 62-request diff (every API route incl. validation errors, CSRF, all SSR pages)
  between the previous build and this one → 0 differences. Web bundle contains no
  sql.js/Capacitor code.
- Mobile build in headless Chromium: all 10 routes render; add trigger + log symptom,
  reload → both persisted; 0 `/api` network requests; 0 console errors.
- APK: `assembleRelease` with JDK 21 / SDK 36 → 3.9 MB, `apksigner verify` OK,
  `io.github.stub1t.chronicals` versionName 0.1.0 / versionCode 100.
- Not verified here: running on a device/emulator (no KVM in sandbox). Capacitor
  Filesystem/SystemBars native behavior needs a real-device check.
- Workflow passes actionlint + shellcheck; first real run happens on tag push.
- Pre-existing, unrelated: Docker healthcheck uses `localhost`, which resolves to ::1
  while the server binds 0.0.0.0 → container reports unhealthy (also on the previous commit).

# Backup, DNS rebinding fix, medications (2026-10-13)

- [x] Host allowlist in hooks.server.ts (421), ALLOWED_HOSTS, Docker healthcheck on 127.0.0.1
- [x] Medications: stop/start side effects in data layer; edit, stop, restart, delete in UI
- [x] Backup: GET/POST /api/backup, server + sql.js restore, settings UI, share sheet on Android, SW excludes /api/backup
- [ ] Docs (README, AGENTS.md), verify, commit per item, container rebuild, push

## Review (2026-10-13)

- Host check: unknown Host gets 421 on /api and pages, with and without ORIGIN; localhost, IPs,
  ORIGIN host and ALLOWED_HOSTS names served; SSR loads work; container reports healthy.
  The first version broke the mobile build (fallback page rendered at build time); fixed by
  skipping the check while `building`.
- Medications: 11/11 browser checks (add, edit, stop to past list with end date, take again,
  delete keeps history as "Deleted medication", reload).
- Backup API: 19/19 checks (round trip, trigger, newer version, foreign, damaged, oversized,
  wrong type and cross-origin uploads rejected, data intact, survives restart).
- Backup UI: web 8/8 (download, import, bad file), mobile build 9/9 (fresh device imports a
  server backup, persists, bad file); sql.js export imports on the server.
- APK builds with @capacitor/share registered. Native share sheet and file picker are not
  tested on a device.
