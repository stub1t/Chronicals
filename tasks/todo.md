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
- [x] Docs (README, AGENTS.md), verify, commit per item, container rebuild, push

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

# CI and tests, search, report, reminders, app lock (2026-10-14)

- [x] locale.ts type errors fixed; vitest suite (backup, API, analysis, dates, search, report, reminders): 42 tests
- [x] CI workflow: check, tests, web + mobile builds, Android debug build
- [x] Search: from/to range API, on-device matching incl. translations, /search page
- [x] Doctor report: summary builder, PDF (jsPDF), CSV (BOM, ; or ,, formula guard), /analysis/report
- [x] Reminders (Android): daily + per medication, inexact, re-planned on changes
- [x] App lock (Android): biometric or screen lock, relock after 1 min, privacy screen
- [ ] Release (user's call)

## Review

- Found by tests: withoutWalFlag mutated Buffers (Buffer#slice is a view); latin1() split emoji
  surrogate pairs; the lock screen stayed German (not inside the language key block).
- Browser checks: search 17/17 web and 17/17 mobile, report 10/10 web and 6/6 mobile (PDF
  rendered and inspected, layout fixed twice), reminders 10/10 (recorder for the browser
  stub), app lock 11/11 (controllable biometric stub).
- Not verifiable here: real notifications, biometric prompt, privacy screen on a phone.

# Dependencies, edit screens, About, cleanup, license (2026-10-14)

- [x] Dependencies updated within majors; npm audit 25 (12 high) to 12 (0 high, all dev tooling or unused); Dependabot
- [x] svelte-check 0 warnings; em dashes out of the UI; README custom-type claim corrected
- [x] Edit custom entry types; rename and recategorise custom triggers
- [x] About section: version (APK version on Android), AGPL notice, source link
- [x] LICENSE (AGPL-3.0-or-later)
- [x] History scan (gitleaks + manual: no secrets, only stub1t@github.com), repo made public after the user's OK; Obtainium works without a token

# Redesign: soft UI, Today/Calendar/Insights/Me, 15 languages, new insights (2026-10-14)

- [x] 1. Foundation: tokens, Nunito, base components, icons, haptics
- [x] 2. i18n infrastructure (en/de), language picker, RTL (+ no SSR, moved here from 3)
- [x] 3. Data: wellbeing column + API, weather range API, route moves + redirects (Today tab comes with 4)
- [x] 4. Today screen + log sheet
- [x] 5. Calendar: month grid + day summary, week agenda, day page (shared DayView), search; old calendar/entry components and the offline write queue removed
- [x] 6. Me: hub with profile header, Health and App groups, language sheet; entry types with icon picker and a show-on-Today switch (migration 0003 `hidden`); triggers incl. the environment group; profile; medications with a dose-change sheet that updates the dose; in-use entry types answer 409 instead of a foreign key error
- [x] 7. Insights: pure engine in src/lib/insights (tracked days, Fisher test, same-day and day-before triggers, weather, weekday, time of day, cycle phases, medication before/after, trend), screen with four cards, printable HTML report via @capgo/capacitor-printer, CSV separator by locale; old analysis code, routes and jsPDF removed
- [ ] 8. 13 more languages + completeness tests
- [ ] 9. Polish, a11y, dark/RTL passes, screenshots, README, AGENTS.md

## Round 2: feedback on the redesign (plan: ~/.claude/plans/enchanted-sprouting-castle.md)

- [x] R1. Names (Eintragen, Trigger heute, Wo es weh tut, Auswertung, Bericht exportieren, Alle Schmerztypen, Stärke), time format setting, word scale for well-being, intensity 1-5 everywhere with data upgrade, "x/5"
- [x] R2. Built-in triggers editable and deletable, Medikamente group, medication change becomes a trigger, dose changes count, "Etwas dagegen genommen?"
- [x] R3. Own options in forms (extra_options), "+ Neu" tile, Blutzucker, meal type with foods
- [x] R4. Reminder for ongoing logs (Android) and web banner, onboarding
- [x] R5. Place per day, automatic location (Android), weather backfill, pollen
- [ ] R6. Analysis rework from the two reviews (statistics fixes, exact period, one timeline, "Alle" view, foods/pollen/medication as exposures)
- [ ] R7. Report export: structure, columns, x/5, day grouping, type selection, CSV
- [ ] Then phase 8 (languages) and phase 9 (polish)
