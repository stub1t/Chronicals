<p align="center">
  <img src="docs/assets/banner.png" alt="Chronicals banner with the app icon and two screenshots" width="100%">
</p>

# Chronicals

Chronicals is a symptom diary built around a calendar. You record symptoms such as headaches or stomach aches and tag each day with possible triggers, for example poor sleep, stress or caffeine. Over time the app compares the two and shows which triggers appear more often on days with symptoms.

It runs as an Android app that keeps all data on the phone, or as a web app that you host yourself with Docker.

<table>
  <tr>
    <td><img src="docs/screenshots/calendar-light.png" alt="Month view with coloured dots for logged symptoms"></td>
    <td><img src="docs/screenshots/drawer-light.png" alt="Day details with weather and three logged entries"></td>
    <td><img src="docs/screenshots/analysis-light.png" alt="Analysis overview for headaches over three months"></td>
    <td><img src="docs/screenshots/analysis-3-light.png" alt="Air pressure chart and medication changes in the analysis"></td>
  </tr>
  <tr>
    <td><img src="docs/screenshots/calendar-dark.png" alt="Month view in dark mode"></td>
    <td><img src="docs/screenshots/week-dark.png" alt="Week view in dark mode"></td>
    <td><img src="docs/screenshots/log-editor-dark.png" alt="Form for a headache entry with intensity, location and symptoms"></td>
    <td><img src="docs/screenshots/medications-dark.png" alt="Medication list with dosage history"></td>
  </tr>
</table>

## Features

- Month, week and day views. Each entry type has its own colour, and the intensity (1 to 5) sets how strong the colour is.
- Seven built-in entry types: headache, stomachache, allergy or hay fever, hypo, medication change, period and general note. Each has its own form. A headache entry, for example, asks for intensity, location and accompanying symptoms.
- Custom entry types with your own fields (slider, dropdown, checklist or text).
- Triggers in four groups: food, environment, activity and custom. A day can have any number of them.
- Daily weather from [Open-Meteo](https://open-meteo.com): temperature, mean air pressure, the change in pressure since the previous day, UV index and conditions. This needs a location in the profile settings.
- A medication list with a history of dosage changes.
- English and German, light and dark theme.

## How the analysis works

You pick an entry type and a period of 1, 3 or 12 months. For every trigger, Chronicals sorts the days that have any entry into four groups: symptom and trigger, only the symptom, only the trigger, or neither. From those counts it calculates the [phi coefficient](https://en.wikipedia.org/wiki/Phi_coefficient). A value above 0.2 is shown as a possible association and above 0.4 as a strong one. The table also lists the average intensity on days with and without the trigger.

Air pressure change and UV index go through the same calculation after the days are split at the median. For pressure, the days with a bigger drop than the median count as the "high" group.

The results only describe your own records and are not medical advice. With few logged days the numbers can change a lot from one week to the next.

## Installation

### Android

1. Download `chronicals-<version>.apk` from the [latest release](../../releases/latest).
2. Open the file on the phone. Android asks once for permission to install apps from your browser or file manager.

Android 7.0 or newer is required. A new version installs over the old one and keeps your entries.

The diary is stored in the app's private storage. Android's device backup includes it if backup is turned on. There is no export yet, so uninstalling the app deletes the data.

The app only contacts Open-Meteo: for the place search in the profile settings, and for weather data once a location is saved.

### Self-hosted web app

```bash
git clone https://github.com/stub1t/Chronicals.git
cd Chronicals
docker compose up -d
```

The app is then available at <http://localhost:3000>. The SQLite database is stored in the Docker volume `chronicals_data`. Browsers can install the web app as a PWA. Note edits, trigger changes and deletions made while offline are queued and sent when the connection is back.

| Variable | Default | Description |
|---|---|---|
| `ORIGIN` | `http://localhost:3000` | Public URL of the app. Set it when running behind a reverse proxy, otherwise changes are rejected by the CSRF check. |
| `DATABASE_PATH` | `/data/chronicals.db` | Location of the SQLite file inside the container |
| `PORT` | `3000` | Port the server listens on |

## Development

```bash
npm install
npm run dev     # development server on http://localhost:5173
npm run check   # type check
npm run build   # production build of the web app
```

Building the Android app needs JDK 21 and the Android SDK with platform 36:

```bash
npm run android:sync                    # builds the web assets and copies them into android/
cd android && ./gradlew assembleDebug   # APK in android/app/build/outputs/apk/debug/
```

Both versions share the same code. The database queries live in `src/lib/data` and every `/api` route is defined once in `src/lib/data/api.ts`. The web server runs them against SQLite through `better-sqlite3`. The Android app is a static build that answers the same routes inside the app with `sql.js`, a WebAssembly build of SQLite, and saves the database file after each change. [`AGENTS.md`](AGENTS.md) describes the conventions in more detail.

## Releases

Release versions are dates in UTC, for example `v2026.10.13`. A second release on the same day becomes `v2026.10.13.2`. To publish a release from `main`:

```bash
npm run release   # starts the "Android release" workflow
gh run watch      # optional: follow the build, which takes about four minutes
```

The workflow can also be started from the Actions tab. It builds and signs the APK, creates the tag and publishes the release with notes generated from the commits. To build the APK of an existing release again, run `gh workflow run android-release.yml -f rebuild_tag=v2026.10.13`.

### Signing key

The APK is signed with a release key that is not part of the repository. Keep a backup of it, because Android only installs an update if it is signed with the same key as the installed app. The workflow reads the key from four repository secrets. With the keystore in `.signing/`, they can be set with this command:

```bash
P=$(awk '/Store password/{print $3}' .signing/credentials.txt) && base64 < .signing/chronicals-release.jks | tr -d '\n' | gh secret set ANDROID_KEYSTORE_BASE64 && gh secret set ANDROID_KEYSTORE_PASSWORD --body "$P" && gh secret set ANDROID_KEY_PASSWORD --body "$P" && gh secret set ANDROID_KEY_ALIAS --body chronicals
```

The same values can be entered under Settings, Secrets and variables, Actions.
