# Friend-minder

Friend-minder is an Android app that combats relationship drift: once a day
it surfaces one person from a friend list you curate and gives you a
one-tap link to text them. It avoids re-suggesting anyone you've been
reminded about recently. See the full PRD for product details.

This repository currently contains the **architecture scaffold** (Jira
FRM-3): project structure, Gradle configuration, core data-model interfaces,
and a MainActivity/WorkManager skeleton. UI (FRM-4/FRM-5) and the actual
suggestion/notification logic (FRM-6 through FRM-13) are not yet implemented.

## Tech stack

- Kotlin, native Android (no Play Services, no Firebase)
- **Scheduling:** [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager) (Jetpack)
- **Notifications:** `NotificationManager` + `NotificationCompat`
- **Contacts:** `ContentResolver` + `ContactsContract`
- **Storage:** SharedPreferences + JSON (Gson) — see `data/storage/`
- **Messaging:** SMS `Intent` (`ACTION_SENDTO`, `sms:` URI) — no in-app SMS sending

## Project structure

```
app/src/main/java/com/example/friendminder/
├── MainActivity.kt              # Navigation shell only, no business logic
├── FriendMinderApplication.kt   # Wires up ServiceLocator on process start
├── data/
│   ├── models/Contact.kt
│   └── storage/                 # Repository interfaces + SharedPreferences impls
├── work/SuggestionWorker.kt     # WorkManager CoroutineWorker (stubbed doWork)
├── ui/                          # Fragments/Compose land here (FRM-4/FRM-5)
└── utils/ServiceLocator.kt      # Minimal manual DI — see file for rationale
```

## Building

```
./gradlew build
./gradlew installDebug
```

**Requirements:** JDK 17, Android SDK with `compileSdk`/`targetSdk` 34
installed, `minSdk` 26 (Android 8.0+).

## F-Droid compliance

- No Google Play Services, Firebase, Crashlytics, or proprietary/ad SDKs.
- Dependencies are all AndroidX/Jetpack, Kotlin stdlib, and Gson — all
  available via Maven Central / F-Droid's build tooling.
- `gradle.properties` sets `android.useNewApkStructure=true`.
- All data is local to the device; no backend, no accounts, no analytics.

## Deployment / secrets

No hardcoded hostnames, IPs, or secrets belong in this repo. There is
currently no backend and no CI secrets are required; if any are ever added,
follow the `.env` + `${VAR:-default}` convention rather than committing
real values.
