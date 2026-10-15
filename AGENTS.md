# AGENTS.md

Primary agent instructions for this repository live here.

This file provides guidance to coding agents when working with code in this repository.

## Project

**Chronicals**: self-hosted personal health diary with a calendar UI. Logs symptoms (headache, stomachache, allergy, hypo, medication changes) with start/stop times, fetches weather/pollen/pressure from Open-Meteo once the user turns that on, and surfaces correlations in an Analysis tab.

Ships two ways from one codebase: the self-hosted web app (Docker) and a standalone Android app (Capacitor) that keeps all data on the phone, with no server.

## Tech Stack

- **Framework**: SvelteKit (TypeScript, Svelte 5 runes)
- **Database**: SQLite via Drizzle ORM: `better-sqlite3` on the server, `sql.js` (WASM) in the Android app
- **Styling**: TailwindCSS v4 (`@tailwindcss/vite` plugin)
- **Deployment**: Docker + docker-compose (web); Capacitor 8 Android APK via GitHub Releases

## Commands

```bash
npm run dev          # Start dev server (http://localhost:5173)
npm run build        # Production build
npm run preview      # Preview production build

npx drizzle-kit generate   # Generate migration after schema changes
npx drizzle-kit migrate    # Apply migrations (also runs on startup automatically)
npx svelte-kit sync        # Regenerate $types after adding routes

docker compose up -d       # Start self-hosted (port 3000, SQLite in volume)
docker compose up --build  # Rebuild and start

npm test                   # vitest unit tests (TZ=Pacific/Auckland)
npm run build:mobile       # Static SPA for the Android app → build-mobile/
npm run android:sync       # build:mobile + copy into android/ (run before any Gradle build)
npm run android:open       # Open android/ in Android Studio
npm run release            # Start a date-versioned Android release from main (needs gh)
cd android && ./gradlew assembleDebug   # Debug APK (needs JDK 21 + Android SDK 36)
```

> **Build tip**: If `npm run build` fails with `ENOENT: service-worker.js not found`, the real cause is usually a Svelte compile error in a component. The PWA plugin's `closeBundle` hook masks the original error. Check for `{@const}` in invalid positions (must be an immediate child of `{#each}`, `{#if}`, `{:else}`, etc. - not inside HTML elements).

## Architecture

```
src/
├── lib/
│   ├── api.ts               ← api()/getJson(): client transport for /api/* (fetch on web, in-process on Android)
│   ├── db/
│   │   └── schema.ts        ← All Drizzle table definitions (9 tables)
│   ├── server/
│   │   └── db.ts            ← better-sqlite3 connection (WAL + FK), auto-migrate, seed; loaded by hooks.server.ts
│   ├── mobile/
│   │   ├── database.ts      ← sql.js connection, bundled migrations, seed, persistence via @capacitor/filesystem
│   │   └── local-api.ts     ← localFetch(): answers /api/* in-process on Android
│   ├── data/                ← Platform-neutral (runs on the server and in the Android WebView, no Node APIs)
│   │   ├── db.ts            ← `db` live binding + useDatabase(); AppDatabase = any sync Drizzle SQLite DB
│   │   ├── api.ts           ← The single /api/* route table + handleApiRequest()
│   │   ├── security.ts      ← Request validation helpers (+ CSRF check used by hooks.server.ts)
│   │   ├── seed.ts          ← Built-in entry types (headache/stomachache/allergy/hypo/med-change/note) + triggers
│   │   ├── weather.ts       ← Open-Meteo fetch (weather + pollen + pressure delta)
│   │   └── queries/         ← DB query functions (synchronous Drizzle)
│   │       ├── entries.ts         ← Entry CRUD + dot data for calendar
│   │       ├── entryTypeLogs.ts   ← Per-log CRUD
│   │       ├── entryTypes.ts      ← Entry type CRUD
│   │       ├── triggers.ts        ← Trigger CRUD
│   │       ├── profile.ts         ← User profile + medications + medication changes
│   │       └── analysis.ts        ← Phi coefficient correlations + stats + pressure series
│   ├── types/index.ts       ← All shared TypeScript interfaces
│   ├── stores/theme.ts      ← System/light/dark theme (localStorage)
│   ├── utils/date.ts        ← Date helpers + intensityColor()
│   └── components/
│       ├── calendar/        ← CalendarMonth, CalendarWeek, CalendarDay, CalendarCell, DayDot, MonthNav
│       ├── entry/           ← EntryTypePicker, EntryDrawer, EnvironmentalCard
│       ├── symptom/         ← MetricForm (dynamic form from JSON schema)
│       └── trigger/         ← TriggerLogger
├── routes/
│   ├── calendar/            ← Month view (+page.svelte) + week/ + day/[date]/
│   ├── meals/               ← Meals tab: a day's meal logs by kind, opens on today
│   ├── analysis/            ← Correlation analysis tab
│   ├── settings/            ← profile/, medications/, entry-types/, triggers/
│   └── api/[...path]/       ← Catch-all that mounts $lib/data/api.ts on the server
migrations/                  ← Drizzle-generated SQL (auto-applied on startup, also bundled into the app)
Dockerfile                   ← Multi-stage Node 22 Alpine, 256MB mem limit
docker-compose.yml           ← Port 3000, named volume for SQLite
capacitor.config.ts          ← Android app id io.github.stub1t.chronicals, webDir build-mobile
android/                     ← Capacitor Android project (committed; web assets are copied in by cap sync)
assets/                      ← Icon sources for `npx @capacitor/assets generate --android`
.github/workflows/android-release.yml ← Manual run on main → date-versioned signed APK on GitHub Releases
```

## Key Design Decisions

- **Flexible metric schema**: Each entry type stores a `metric_schema` JSON column defining what fields to collect. `MetricForm.svelte` renders these dynamically.
- **Pain colors by type, not severity**: Each entry type has a fixed hex color; intensity (1–5) controls opacity via `intensityColor(color, intensity)`.
- **Pressure delta**: `environmental_data.pressure_delta_hpa` = today's pressure minus yesterday's. Computed on fetch.
- **Special event markers**: Hypo (⚡) and Medication Change (💊) rendered as icon markers on calendar cells, not dots.
- **Analysis**: Phi coefficient (φ) for binary correlations. φ > 0.2 = "possible", φ > 0.4 = "strong". Environmental variables binned at median before Phi calculation.

## Web + Android from one codebase

- **One API surface**: every `/api/*` route lives in `src/lib/data/api.ts`. Add or change endpoints there, never as new `+server.ts` files, because the Android app would not see them.
- **Always call the API via `api()` / `getJson()` from `$lib/api.ts`**, never bare `fetch('/api/…')`. On Android there is no server; `api()` routes to the on-device database.
- **Loads are universal (`+page.ts`)** and read through `getJson(fetch, '/api/…')`. Don't add `+page.server.ts` / form actions: the Android build is a static SPA (`ssr = false` via `__NATIVE_APP__`).
- **`src/lib/data` must stay platform-neutral**: synchronous Drizzle queries on the shared `db`, no `node:*`/`better-sqlite3` imports.
- **`__NATIVE_APP__`** (vite `define`, true when `CHRONICALS_TARGET=mobile`) gates Android-only code so it is tree-shaken out of the web bundle. Import Capacitor/sql.js only behind it.
- **Migrations** are applied on Android from the bundled `migrations/` folder on app start, exactly like the server. `npx drizzle-kit generate` covers both.
- **Persistence on Android**: sql.js runs in memory; `localFetch` awaits `persist()` after every successful mutation (atomic tmp-file swap in the app's private files dir, included in Android auto-backup).
- **Backups**: `GET/POST /api/backup` export or replace the whole database as an SQLite file. Each platform registers `useBackup({ exportBytes, restore })` next to `useDatabase` (`src/lib/server/db.ts`, `src/lib/mobile/database.ts`). Uploads go through `assertSqliteFile` and `assertRestorable` in `src/lib/data/backup.ts` (Chronicals tables, no triggers or views, integrity and foreign key checks, not from a newer migration), then are migrated and seeded before they replace the live database. A schema change that adds triggers or views must update `assertRestorable`. The service worker must never cache `/api/backup`.
- **Android-only features** (reminders, app lock) live in `src/lib/mobile/*` and are only imported behind `__NATIVE_APP__`, so their plugins stay out of the web bundle. Their settings are device-only (localStorage via `src/lib/reminders.ts` and `src/lib/app-lock.ts`). `localFetch` calls `syncReminders()` after any successful change to `/api/medications`, `/api/medication-changes` or `/api/backup`. Reminders use `isExactNotification: false`; do not reintroduce exact alarms (the manifest removes `SCHEDULE_EXACT_ALARM`).
- **Report**: `src/lib/report/summary.ts` builds the report (pure), `csv.ts` and `pdf.ts` render it. jsPDF's built-in font is Latin-1 only, so PDF text goes through `latin1()`. Files are saved with `saveFile()` in `src/lib/files.ts` (download on web, share sheet on Android).
- **Search** filters on the device (`src/lib/search.ts`) over entries from `GET /api/entries?from=&to=`, matching stored and translated names.
- **Version and license**: `__APP_VERSION__` (vite `define`) is the release version from `CHRONICALS_VERSION_NAME` in CI, otherwise `dev-<commit>`; the Android app shows `App.getInfo().version` instead. Settings → About shows it with the AGPL-3.0 notice and the source link (AGPL §13). The project is AGPL-3.0-or-later; keep new files compatible.
- **Privacy, no outside connections by default**: `user_profile.weather_enabled` (off on a fresh install) gates every Open-Meteo call in the data layer: `ensureEnvData` returns early and `/api/profile/location-search` answers 403 while it is off. Any new outside request must go through the same switch or a new opt-in. The web CSP allows `connect-src 'self'` only (the server fetches). Location on Android comes from the in-app `LocationPlugin.java` (Android's LocationManager, registered in `MainActivity`), not a Play Services plugin; don't add dependencies that pull in `com.google.android.gms` or Firebase. The diary is excluded from Google cloud backup (`res/xml/data_extraction_rules.xml`, `backup_rules.xml`) and the WebView has Safe Browsing and metrics off (manifest meta-data).
- **Host allowlist**: `hooks.server.ts` returns 421 unless the Host header is the `ORIGIN` host name, `localhost`, an IP address or listed in `ALLOWED_HOSTS` (DNS rebinding protection). It is skipped in dev and at build time.

## Tests and CI

- `npm test` runs vitest over `src/**/*.test.ts` with `TZ=Pacific/Auckland`.
- Data-layer tests call `useTestDatabase()` from `src/lib/server/testing.ts`: a fresh in-memory, migrated and seeded database registered with `useDatabase()`. Then call query functions or `handleApiRequest()` directly.
- Keep logic that can be tested pure and separate from plugins and Svelte (see `src/lib/reminders.ts` vs `src/lib/mobile/reminders.ts`).
- `.github/workflows/ci.yml` runs check, tests, both builds and `assembleDebug` on every push.

## Android releases

1. Releases are date-versioned (UTC) and cut from `main`: `npm run release` (or Actions → Android release → Run workflow).
   The workflow picks `vYYYY.MM.DD` (`.2`, `.3`, … for further releases that day), builds, signs, creates the tag and publishes `chronicals-YYYY.MM.DD.apk`.
   Don't push release tags by hand. `package.json` `version` is not used for releases.
2. versionCode = `YYYYMMDD * 100 + n` (n ≤ 99), always above the old semver release v0.1.0 (100).
   Rebuild an existing release's APK: `gh workflow run android-release.yml -f rebuild_tag=vYYYY.MM.DD`.
3. Signing secrets: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`.
   The keystore lives only outside git (`.signing/`, gitignored). Losing it means existing installs can't be updated.
4. Obtainium: `docs/obtainium.json` is the app config behind the "Get it on Obtainium" link in the README
   (it pins the signing certificate hash). If the repo URL, app id or signing key changes, update the JSON and
   regenerate the link: `https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/` + `encodeURIComponent(JSON.stringify(config))`.
   Obtainium reads the version from the release tag (`vYYYY.MM.DD` without the `v`), so versionName must stay equal to the tag.
4. Local signed build: export `CHRONICALS_KEYSTORE_FILE`, `CHRONICALS_KEYSTORE_PASSWORD`, `CHRONICALS_KEY_ALIAS`, `CHRONICALS_KEY_PASSWORD` (and optionally `CHRONICALS_VERSION_NAME`/`_CODE`) before `./gradlew assembleRelease`.

## Environment Variables

```
DATABASE_PATH   Path to SQLite file (default: ./chronicals.db)
ORIGIN          Public URL for CSRF protection (default: http://localhost:3000)
PORT            Server port (default: 3000)
ALLOWED_HOSTS   Extra host names the server answers to, comma-separated (default: none)
BODY_SIZE_LIMIT Largest request body; the Docker image sets 50M for backup uploads
CHRONICALS_TARGET=mobile   Build-time: produce the Android SPA instead of the Node server
```

---

### 1. Plan Node Default
- Enter plan mode for ANY non-trivial task (3+ steps or architectural decisions)
- If something goes sideways, STOP and re-plan immediately - don't keep pushing
- Use plan mode for verification steps, not just building
- Write detailed specs upfront to reduce ambiguity

---

### 2. Subagent Strategy
- Use subagents liberally to keep main context window clean
- Offload research, exploration, and parallel analysis to subagents
- For complex problems, throw more compute at it via subagents
- One task per subagent for focused execution

---

### 3. Self-Improvement Loop
- After ANY correction from the user: update `tasks/lessons.md` with the pattern
- Write rules for yourself that prevent the same mistake
- Ruthlessly iterate on these lessons until mistake rate drops
- Review lessons at session start for relevant project

---

### 4. Verification Before Done
- Never mark a task complete without proving it works
- Diff behavior between main and your changes when relevant
- Ask yourself: "Would a staff engineer approve this?"
- Run tests, check logs, demonstrate correctness

---

### 5. Demand Elegance (Balanced)
- For non-trivial changes: pause and ask "is there a more elegant way?"
- If a fix feels hacky: "Knowing everything I know now, implement the elegant solution"
- Skip this for simple, obvious fixes - don't over-engineer
- Challenge your own work before presenting it

---

### 6. Autonomous Bug Fixing
- When given a bug report: just fix it. Don't ask for hand-holding
- Point at logs, errors, failing tests - then resolve them
- Zero context switching required from the user
- Go fix failing CI tests without being told how

---

## Task Management
1. **Plan First**: Write plan to `tasks/todo.md` with checkable items
2. **Verify Plan**: Check in before starting implementation
3. **Track Progress**: Mark items complete as you go
4. **Explain Changes**: High-level summary at each step
5. **Document Results**: Add review section to `tasks/todo.md`
6. **Capture Lessons**: Update `tasks/lessons.md` after corrections

---

## Core Principles
- **Simplicity First**: Make every change as simple as possible. Impact minimal code
- **No Laziness**: Find root causes. No temporary fixes. Senior developer standards

---

## Local Workflow Rules
- Always commit repository changes after completing a change set.
- After committing, always rebuild and restart the `chronicals` web UI container so the latest changes are visible locally.
