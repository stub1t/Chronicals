<p align="center">
  <img src="docs/assets/banner.png" alt="Chronicals banner with the app icon and two screenshots" width="100%">
</p>

# Chronicals

Chronicals is a symptom diary for Android. You log pain such as headaches or stomach aches, tap what happened that day, for example poor sleep, stress or caffeine, and write down what you ate. Over time the app shows which of those things go along with your symptoms, when the symptoms tend to come, and whether a medication change made a difference.

All data stays on the phone. The app is written in Kotlin with Jetpack Compose and takes its colours from your wallpaper on Android 12 and newer.

<table>
  <tr>
    <td><img src="docs/screenshots/today-light.png" alt="Today with the symptom tiles, a headache that is still going and the day's triggers"></td>
    <td><img src="docs/screenshots/log-light.png" alt="Logging a headache: start time, how long, intensity and where it hurts"></td>
    <td><img src="docs/screenshots/calendar-light.png" alt="Month view with days tinted by symptom and the summary of a selected day"></td>
    <td><img src="docs/screenshots/insights-light.png" alt="Analysis for headaches: 22 of 83 recorded days, 8 per 30 days, fewer than before, and a grid of every day"></td>
  </tr>
  <tr>
    <td><img src="docs/screenshots/triggers-dark.png" alt="What goes along with headaches: poor sleep as a likely link, weather change and dairy worth watching, in dark mode"></td>
    <td><img src="docs/screenshots/day-dark.png" alt="A past day with the symptom tiles, two entries and its triggers, in dark mode"></td>
    <td><img src="docs/screenshots/medications-dark.png" alt="Medications with their doses and the change history, in dark mode"></td>
    <td><img src="docs/screenshots/me-dark.png" alt="The Me tab with profile, health settings and the weather switch, in dark mode"></td>
  </tr>
</table>

## Features

- Today as the home screen: log a symptom in two taps, end one that is still going, tap the day's triggers, see the weather and write a note.
- Meals in their own tab, sorted into breakfast, lunch, dinner and snacks. It opens on today, and the arrows go back to earlier days.
- Calendar with a month view tinted by your symptoms, a week list and a page for each day. Search finds days by note, symptom, form value or trigger.
- Six built-in entry types: headache, stomachache, allergy or hay fever, blood sugar, period and note. Each has its own form. A headache entry, for example, asks for intensity, location, accompanying symptoms and painkillers. Options and painkillers of your own can be added with "+ New".
- Your own entry types with a name, icon and colour, recording intensity (1 to 5) and a note. Any type you don't use can be hidden from Today.
- Triggers in five groups: food and drink, environment, activity, medication and your own.
- Daily weather from [Open-Meteo](https://open-meteo.com): temperature, air pressure and its change since the day before, UV index, conditions and pollen by plant. It is off until you turn it on under Me, and it needs a place.
- Analysis: how you are doing compared with the period before, which triggers, foods, weather and pollen go along with a symptom, when it happens, and how the weeks around a medication change compare. See below.
- A medication list with dose changes and their history.
- A report for your doctor for any period, to print or save as a PDF from the print dialog, and every entry as a CSV spreadsheet.
- Optional reminders (a daily one, one per medication time, and a question when pain you logged is still going) and an optional app lock.
- English and German, light and dark theme, themed launcher icon and start screen.

## How Analysis works

Analysis looks at one entry type, or all of them side by side, over 3, 6 or 12 months or everything. Only days with some record count: a log, a trigger or a note. A day you didn't open the app is left out instead of being counted as a day without symptoms.

The first card shows the days with the symptom, the average intensity and how often you took something against it. It compares the days per 30 recorded days with the period of the same length just before and says "fewer" or "more" when the rate changed by at least a fifth, by at least one day a month, and a one-sided [Fisher exact test](https://en.wikipedia.org/wiki/Fisher%27s_exact_test) makes chance unlikely. Up to half a year shows every day in a grid, longer periods a bar per month.

The second card compares, for each trigger, each food eaten on at least 5 days, air pressure drops of 5 hPa or more, days of 28 °C or more, a UV index of 6 or more and high pollen, the share of days with the symptom when it was there with the share on all other days. Each is checked for the same day and for the day after, and the stronger of the two is shown. Because many things are checked at once, the p-values are corrected (Benjamini-Hochberg). "Likely link" needs p below 0.05 (0.01 for weather and pollen, which come in seasonal streaks), at least 1.5 times as many symptom days and at least 3 such days. "Worth watching" marks weaker signs, and "fewer symptoms" what goes along with clearly fewer symptom days. With fewer than 5 days on either side, it is listed as not enough days yet.

The third card names a weekday or a time of day only when one clearly stands out, and, with at least two logged periods, shows the phase of the cycle: the 3 days before, period days 1 to 3, later period days and other days.

The last card puts the 30 days before each medication start or dose change next to the days after, once both sides have at least 10 recorded days.

These are patterns in your own diary, not proof of a cause and not medical advice.

## Privacy

The diary stays on the phone. There are no accounts, ads, analytics or crash reports.

The app connects to one outside service, [Open-Meteo](https://open-meteo.com), and only after you turn on "Weather and pollen" under Me or in the introduction. It then sends the coordinates of your home place, or of the place you were on a given day, and the dates it needs weather for. The place search sends the name you type and the app language. Entries, notes, names and medications are never sent. While the switch is off, the app makes no connections.

- Automatic location for days away from home belongs to the same switch. It asks Android for the approximate location only, through Android's own location service.
- The app contains no Google Play Services or Firebase, and every library in it is open source (AndroidX and Kotlin).
- The report is laid out by Android's WebView only to print it; it loads nothing from the internet.
- The source code link under Me opens GitHub in the browser when you tap it.

## Installation

Android 7.0 or newer is required. There are two ways to install the app.

#### With Obtainium

[Obtainium](https://obtainium.imranr.dev) installs the app from the GitHub releases and updates it when a new release comes out. Open this link on the phone:

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22io.github.stub1t.chronicals%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fstub1t%2FChronicals%22%2C%22author%22%3A%22stub1t%22%2C%22name%22%3A%22Chronicals%22%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Afalse%2C%5C%22versionDetection%5C%22%3Atrue%2C%5C%22allowedSigningCertHashes%5C%22%3A%5C%2204%3A3F%3AE1%3AE2%3A27%3AFE%3A49%3A75%3A30%3A8F%3A2B%3AE3%3A64%3A89%3AA8%3A0D%3AFF%3A3F%3AD8%3A6A%3A47%3AB3%3A04%3A9F%3AC5%3A88%3A05%3A22%3A20%3ACA%3ABD%3A89%5C%22%7D%22%7D"><img src="docs/assets/badge-obtainium.png" alt="Get it on Obtainium" height="48"></a>

It opens Obtainium with the app already filled in. It also sets the expected signing certificate, so Obtainium refuses an APK that was signed with a different key. Instead of the link you can tap Add App in Obtainium and enter `https://github.com/stub1t/Chronicals`.

#### Manual download

Download `chronicals-<version>.apk` from the [latest release](../../releases/latest) and open it on the phone. Android asks once for permission to install apps from your browser or file manager. Repeat this for each new version.

Either way, a new version installs over the old one and keeps your entries.

The diary is stored in the app's private storage. It is left out of Google's cloud backup. On Android 12 and newer, moving your data to a new phone by cable or Wi-Fi still includes it. Uninstalling the app deletes the data, so export a backup first (see [Backups](#backups)).

## Reminders and app lock

Both are off until you turn them on under Me.

- The daily reminder asks you to log your day at a time you choose. Each medication can have reminder times; they stop when the medication is stopped. If pain you logged is still going after a few hours, the app asks whether it is over. Android may deliver a reminder a few minutes late, because the app does not use exact alarms.
- The app lock asks for your fingerprint, face or the phone's screen lock when the app opens and after more than a minute in the background. While it is on, the app is hidden in the recent apps view and screenshots are blocked. It keeps someone holding your unlocked phone out of the diary. It does not encrypt the data.

## Backups

The Data section under Me has two buttons:

- Export backup saves the whole database as one SQLite file, `chronicals-YYYY-MM-DD.db`, wherever you choose: the phone's storage, a USB stick or a cloud folder.
- Import backup replaces all data with the contents of such a file, after a confirmation.

Backups from earlier versions of Chronicals, including the web app, can be imported and are upgraded on the way. Files from a newer version of Chronicals, damaged files and other SQLite databases are refused.

## Development

The app is in `android/`. Building it needs JDK 17 or newer and the Android SDK with platform 36.

```bash
cd android
./gradlew testDebugUnitTest   # unit tests (or `npm test` from the top folder)
./gradlew assembleDebug       # APK in android/app/build/outputs/apk/debug/
```

GitHub Actions runs the tests and a debug build on every push. [`AGENTS.md`](AGENTS.md) describes the structure and conventions in more detail.

## License

Copyright (C) 2026 stub1t

Chronicals is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. See [`LICENSE`](LICENSE).

The About section under Me links to the source of this repository; point it at your fork if you change the code.
