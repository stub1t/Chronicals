# Android app (standalone) + GitHub releases

Plan: data layer runs unchanged on both server (better-sqlite3) and phone (sql.js);
one API route table serves both the SvelteKit server and an in-process phone transport.

- [ ] 1. Move queries/weather/seed/security to `src/lib/data`, add swappable `db`
- [ ] 2. Single API route table (`src/lib/data/api.ts`) + catch-all server route
- [ ] 3. `api()` client transport; `+page.server.ts` → `+page.ts` loads
- [ ] 4. On-device sql.js database with migrations, seed, persistence
- [ ] 5. Mobile build target (adapter-static, no PWA/service worker)
- [ ] 6. Capacitor Android project, icons, back button, safe areas
- [ ] 7. Release keystore + GitHub Actions release workflow
- [ ] 8. Docs (AGENTS.md)
- [ ] Verify: check/build, Docker API smoke test vs main, mobile build in headless Chromium, signed APK
- [ ] Publish: push + tag v0.1.0 (needs GitHub credentials + secrets from user)
