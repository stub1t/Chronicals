# AGENTS.md

Primary agent instructions for this repository live here.

This file provides guidance to coding agents when working with code in this repository.

## Project

**Chronicals**: a personal health diary for Android. Logs symptoms (headache, stomachache, allergy, blood sugar, period) with start and end times, meals, triggers and medications, fetches weather, pollen and air pressure from Open-Meteo once the user turns that on, and shows patterns in an Analysis tab. All data stays on the phone; there is no server.

Until October 2026 it was a SvelteKit web app shipped as a Docker server and as a Capacitor Android app. The native app replaced both; it opens the Capacitor app's database file in place, so an update keeps the diary. The web code is in the git history before the switch.

## Tech Stack

- **Language and UI**: Kotlin, Jetpack Compose, Material 3 (dynamic colour on Android 12+, brand colours before)
- **Database**: SQLite through Android's own `SQLiteDatabase`, no ORM
- **Libraries**: AndroidX and Kotlin only (FOSS). No Google Play Services, no Firebase, no analytics
- **Build**: Gradle 8.14, Android Gradle plugin 8.13, compileSdk/targetSdk 36, minSdk 24. Newer Compose and Navigation releases need AGP 9.1 and SDK 37, so the versions in `android/app/build.gradle.kts` are the newest that build with these
- **Releases**: signed APK on GitHub Releases, installed with Obtainium or by hand

## Commands

```bash
npm test                                  # Kotlin unit tests (runs ./gradlew testDebugUnitTest in android/)
cd android && ./gradlew assembleDebug     # Debug APK (needs JDK 17+ and the Android SDK with platform 36)
cd android && ./gradlew assembleStaging   # Optimised like a release but signed with the debug key, for trying real speed
npm run release                           # Start a date-versioned Android release from main (needs gh)
```

`package.json` only exists so the CI and release workflows keep working without changes: `npm ci` installs nothing and `npm run android:sync` does nothing.

## Architecture

```
android/app/src/main/java/io/github/stub1t/chronicals/
├── ChronicalsApp.kt      ← Diary and Settings singletons; plans reminders again after changes; app-wide messages
├── MainActivity.kt       ← Start screen, app lock, permissions, backup files, notification taps
├── data/
│   ├── Diary.kt          ← The database (files/chronicals.db): reads, writes, backup, and `changes`, a counter every write bumps
│   ├── Schema.kt         ← The tables as the old migrations left them; adds missing columns; PRAGMA user_version
│   ├── Seed.kt           ← Built-in types and triggers (BuiltIns.kt), upgrades of old diaries, the profile row
│   ├── Insights.kt       ← The statistics behind Analysis (Fisher exact test, Benjamini-Hochberg, cycle phases, medications)
│   ├── Report.kt         ← Report data, CSV and the printable HTML
│   ├── Search.kt         ← Day search (accent- and case-insensitive, stored and translated names)
│   ├── Settings.kt       ← Phone-only settings (theme, time format, reminders, app lock, automatic location) in SharedPreferences
│   ├── Models.kt, Json.kt, Times.kt
├── platform/
│   ├── Reminders.kt      ← AlarmManager (inexact) + notifications; ReminderReceiver also runs after a restart
│   ├── AppLock.kt        ← Android's BiometricPrompt with the screen lock as fallback
│   ├── Location.kt       ← Rough position from LocationManager; today's place when away from home
│   └── Weather.kt        ← Open-Meteo weather, pollen and place search
└── ui/
    ├── AppScaffold.kt    ← The five tabs (Today, Meals, Calendar, Analysis, Me) with nested routes
    ├── day/              ← DayContent (Today and the calendar's day page), LogSheet, LogFields, WeatherCard
    ├── today/, meals/, calendar/, analysis/, me/
    ├── EntryIcon.kt, DuoIconData.kt, Icons.kt   ← Built-in type icons as SVG path data, custom type icons
    ├── BuiltInText.kt    ← Translations of built-in names and options, which are stored in English
    └── theme/Theme.kt
android/app/src/main/res/  ← Strings (values/, values-de/), launcher icon vectors, start screen colours
android/app/src/test/      ← JVM unit tests
.github/workflows/android-release.yml ← Manual run on main → date-versioned signed APK on GitHub Releases
```

## Key Design Decisions

- **Tabs keep their state**: each tab has its own view model and the navigation saves and restores state, with no transition animation, so switching is instant. Screens reload off the main thread when `Diary.changes` moves.
- **Flexible metric schema**: each entry type stores its form in `entry_types.metric_schema` (JSON). `LogFields` renders it: 1 to 5 scales as five buttons, up to 12 options as chips, more as a menu.
- **Stored formats are the web app's**: times are UTC ISO strings with milliseconds (`Times.iso`), ids UUID v4, JSON columns as before. Built-in names and options are stored in English and translated when shown. Keep it that way so old backups and the diary of the Capacitor app stay readable.
- **One diary file**: `files/chronicals.db`, the path the Capacitor app used. `Schema.apply` creates missing tables and columns on every start, then `Seed.run` refreshes the built-ins. Schema changes go into `Schema.kt` as new columns (and a higher `Schema.VERSION` when an old app could not read the result).
- **Backups** are the database file. Import checks the SQLite header, the Chronicals tables, no triggers or views, integrity and foreign keys, and refuses files with a newer `user_version`. A schema change that adds triggers or views must update `Diary.restoreFrom`.
- **Report**: `Report.kt` builds the data; the PDF is the HTML printed through the system print dialog (a WebView lays it out and loads nothing from the internet); the CSV neutralises cells a spreadsheet would run as formulas.
- **Pain colours by type, not severity**: each entry type has a fixed colour; intensity changes how strong it is drawn.
- **Pressure delta**: `environmental_data.pressure_delta_hpa` is today's pressure minus yesterday's, computed when fetched.
- **Privacy, no outside connections by default**: `user_profile.weather_enabled` (off on a fresh install) gates every Open-Meteo request in `platform/Weather.kt`, and the place search returns nothing while it is off. Any new outside request needs the same switch or a new opt-in. Location comes from Android's LocationManager; don't add dependencies that pull in `com.google.android.gms` or Firebase. The diary is excluded from Google cloud backup (`res/xml/data_extraction_rules.xml`, `backup_rules.xml`).
- **Reminders** use inexact alarms (`setAndAllowWhileIdle`); don't add `SCHEDULE_EXACT_ALARM`. `ChronicalsApp` plans them again half a second after any diary or reminder setting change.
- **App lock**: on start and after more than a minute in the background; while on, `FLAG_SECURE` hides the app in recent apps and blocks screenshots.
- **Icons**: the built-in type icons are SVG path data in `DuoIconData.kt` (soft fill, 2 px strokes, solid details). Parse each path with its own `addPathNodes` call: `PathParser.toNodes()` hands out the parser's own list.
- **Launcher icon and start screen**: vector layers in `res/drawable` (`ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml`, `splash_icon.xml`); the start screen takes wallpaper colours on Android 12+ (`values-v31/splash.xml`).
- **Version and license**: the version shown under Me is `BuildConfig.VERSION_NAME`, from `CHRONICALS_VERSION_NAME` in CI. The project is AGPL-3.0-or-later; keep new files compatible.

## Tests and CI

- `android/app/src/test` holds JVM tests for the stored formats, search, the insights engine and the report; the insights and report cases are the ones the web app had, so the statistics did not change in the port. Keep logic that can be tested free of Android classes.
- `.github/workflows/ci.yml` runs `npm test` (the unit tests) and `assembleDebug` on every push. The workflow files can't be pushed with the stored token (no `workflow` scope).
- Android has no emulator with acceleration here: an unaccelerated one works for checking screens (the accessibility tree via `uiautomator dump` when frames are slow), but its WebView crashes, so printing can only be checked on a phone.

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
5. Local signed build: export `CHRONICALS_KEYSTORE_FILE`, `CHRONICALS_KEYSTORE_PASSWORD`, `CHRONICALS_KEY_ALIAS`, `CHRONICALS_KEY_PASSWORD` (and optionally `CHRONICALS_VERSION_NAME`/`_CODE`) before `./gradlew assembleRelease`.

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
- After committing, run the unit tests and build the debug APK (`npm test`, `cd android && ./gradlew assembleDebug`). There is no web app or container any more.
