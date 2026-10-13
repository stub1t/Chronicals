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
