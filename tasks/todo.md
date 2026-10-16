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
- [x] R6. Analysis rework from the two reviews (statistics fixes, exact period, one timeline, "Alle" view, foods/pollen/medication as exposures)
- [x] R7. Report export: structure, columns, x/5, day grouping, type selection, CSV
- [ ] Then phase 8 (languages) and phase 9 (polish)

## Bug hunt (2026-10-14)

- [x] API: impossible dates (2026-02-31) refused; duplicate type names give 409; adding a trigger twice is harmless; unknown ids give 404 instead of 500; a log can't end before it starts
- [x] Weather: two requests storing the same day no longer fail on the unique date (upsert)
- [x] Triggers: typing a translated built-in name ("Koffein") picks the built-in one; a restored trigger is listed once; a double tap can't undo or repeat itself
- [x] Log form: "Until" starts after the start instead of at it (an equal end used to save 24 hours); an empty time can't be saved
- [x] Calendar months with fewer than 31 days, broken dates in day and week URLs
- [x] Offline queue gives up on server errors after 10 tries; unused date helpers removed
- [x] Checks: 95 unit tests, all browser suites plus a new regression suite (16 checks) on web and app

## Round 3: feedback from a Signal screenshot (2026-10-15)

- [x] F1. Introduction: Meal no longer in the list of types (meals get their own tab)
- [x] F2. "Nachfragen, wenn Schmerz noch anhält" instead of "wenn etwas noch anhält"
- [x] F3. Today: no "Wie geht es dir heute?" card (also not on the calendar day page; old ratings stay)
- [x] F4. Today: "Eintragen" becomes "Schmerzart", with the hint "Tippe auf eine Kachel, wenn du Schmerzen hast"
- [x] F5. Tiles all the same size; the Meal tile goes (F1)
- [x] F6. "+ Neu" everywhere: triggers ("+ Mehr"), own options ("+ Eigenes"), relief ("+ Andere")
- [x] F7. Weather: name the pollen even when all are low
- [x] F8. Today: no "Diese Woche" strip
- [x] F9. New drawn headache icon
- [x] F10. "Etwas dagegen genommen?" becomes "Schmerzmittel" for pain types; own entries are kept on the type
- [x] F11. Period: own symptoms with "+ Neu", bleeding as a 1 to 5 scale (light/medium/heavy upgraded), painkillers
- [x] F12. Meals tab: always opens on today, sections Frühstück, Mittagessen, Abendessen, Snacks, each with "+ Neu"
- [x] Checks: unit tests, svelte-check, both builds, browser run on web and app build, commit, container rebuild

## Review

- Meals stay entry type logs of `builtin-meal`, so insights (foods as exposures), the report and the calendar keep working. The tab ignores the type's hidden flag; the Me list shows Meal without a switch.
- Old period logs: light, medium, heavy become 2, 3, 4 on start (seed, tested). The report shows the scale as x/5.
- Own painkillers are stored in the type's extra options under `relief` and can be removed under Me, like other own options.
- Found in the browser run: the Me tab was also highlighted on /meals (`startsWith('/me')`); fixed.
- Checks: svelte-check 0 problems, 98 unit tests, web and mobile builds, 33 browser checks in German and 10 in English on the web build, 5 on the mobile build (introduction, meals on the device, reload).
- README screenshots still show the mood faces and the old headache icon.

## Privacy: nothing leaves the device unless turned on (2026-10-15)

- [x] P1. Profile setting "Wetter und Pollen" (`weather_enabled`), off on a fresh install. Existing installs that already have a place keep it on.
- [x] P2. Data layer refuses to contact Open-Meteo while it is off: no weather fetch, no place search (web server and app alike)
- [x] P3. UI: toggle under Me with what is sent; place search and automatic location only when it is on; Today shows a short "off" line; introduction asks first
- [x] P4. Android: replace @capacitor/geolocation (Google Play Services) with a small plugin on Android's LocationManager
- [x] P5. Android: drop the google-services Gradle plugin; WebView Safe Browsing and metrics off; diary not in Google cloud backup (device to device transfer stays)
- [x] P6. Web: CSP without Open-Meteo (the server fetches, the browser never does)
- [x] P7. README privacy section, AGENTS.md, tests
- [x] Also: npm overrides for cookie, uuid (Capacitor CLI) and esbuild (drizzle-kit), so the three Dependabot alerts are fixed; Referrer-Policy no-referrer

### Review

- Existing installs with a home place or a day elsewhere keep weather on (migration 0008); fresh installs start with it off. Old stored weather stays readable when it is turned off.
- Checked: unit test that no fetch happens while off and the place search answers 403; 19 browser checks on the web build and 19 on the app build (no request to any other host on six pages, switch, profile, introduction, turning it off again); the place, welcome, me and bug suites still pass where they test current screens.
- The debug APK builds with JDK 21 and SDK 36; it contains no com.google.android.gms or Firebase classes, and the manifest has the backup rules and the WebView meta-data. The location plugin was not tried on a phone (no emulator here).
- Old browser scripts today.mjs and calendar.mjs still look for the mood faces removed in round 3; they need updating separately.

## Bug sweep (2026-10-16)

- [x] B1. Today "Noch nichts eingetragen": with reduced motion on (Android "Remove animations"), the floating blobs restarted every 0.01ms and jittered. Endless animations now run once and stop.
- [x] B2. The app left open overnight still showed yesterday on Today, and new logs landed on yesterday. Coming back on a new day reloads the data and rebuilds the page.
- [x] B3. A "still going?" notification tapped while Today was open did not open the log's sheet (only checked on mount).
- [x] B4. The daily reminder opened the calendar day page instead of Today.
- [x] B5. Android app: weather fetched by a page load was never written to disk, so every start fetched it again.
- [x] B6. A past day keeps the forecast it was fetched with during that day; it is now fetched once more after the day is over.
- [x] B7. The place search asked Open-Meteo for German names in English too.
- [x] B8. vitest started directly (not via npm test) ran in UTC, so one report test failed; the time zone is now in the vitest config.
- [x] B9. Stale comment said Android backup includes the database.

### Review

- Browser checks on web and app builds: blob still with reduced motion and floating without, Today moves to the new day on return and logs go there, notification link opens the sheet (web), weather stored across a restart (app; the old code fetched 3 times). Privacy suite 19/19 on both builds.
- Unit test for B6 fails without the fix. svelte-check 0, 100 tests.
