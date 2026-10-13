# Background Location Tracker

Standalone Android assignment app. All implementation is in this folder; the parent Supplyit application is not changed. The supplied assignment prompt is the implementation specification; no separate assignment PDF was provided.

Preview (Android 15 emulator, simulated GPS coordinates): [tracker-preview.png](docs/tracker-preview.png)

## Setup and versions

Validated toolchain: Flutter 3.38.4 stable, Dart 3.10.3, Android Gradle Plugin 8.11.1, Gradle 8.14, Kotlin 2.2.20, JetBrains JDK 21.0.8 (Android Studio bundled runtime). Java/Kotlin bytecode target is 17. Android compile/target SDK 36, min SDK 24, NDK 28.2.13676358. GetX 4.7.3 and native Play Services Location 21.3.0. Requires a device with Google Play services. This project uses Flutter 3.32+ / Dart 3.8+ (the chosen lint tooling requires Dart 3.8), satisfying the assignment’s Flutter 3.22-or-later restriction. It was tested with Flutter 3.38.4.

```sh
cd background_location_tracker
flutter pub get
flutter analyze
flutter test
flutter run
flutter build apk --debug
flutter build apk --release
```

If running Gradle directly on macOS without a system JDK:

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
cd android
./gradlew connectedDebugAndroidTest
```

If Gradle's Unified Test Platform dependency cannot download, the compiled test APK can also be run directly with Android's instrumentation runner:

```sh
adb install -r build/app/outputs/flutter-apk/app-release.apk
adb install -r build/app/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.example.background_location_tracker.test/androidx.test.runner.AndroidJUnitRunner
```

The direct route uses the local debug-key-signed release APK with native shrinking disabled, so test references retain their names. Build the test APK first with `./gradlew assembleDebugAndroidTest`; private-key-signed releases require a correspondingly signed test APK.

The native tests use a separate temporary SQLite database, grant this app's foreground and notification permissions, and start/stop its service. Run them on a test emulator/device. They require device location to be enabled.

## Features

- GetX controller, route binding, GetMaterialApp navigation, reactive Obx UI; no setState.
- Current coordinates, nullable accuracy, local update time, actual Running/Stopped status, intermediate Starting state, busy indicators and errors.
- Native history capped to the newest 50 rows for display, sorted by timestamp then ID. The database retains all recorded fixes.
- Foreground Kotlin service using FusedLocationProviderClient; desired interval 10 seconds, minimum interval 5 seconds. Timing is subject to OS/provider scheduling.
- Every delivered location in a batch is inserted into native SQLite before notification/event delivery. No Flutter listener is required for storage.
- Notification shows coordinates and fix timestamp, opens the app, and has a Stop action.
- Explicit location, notification and separate background permission flows; Settings actions, approximate location, denied permissions, empty database, no signal and channel errors handled.
- Persisted native tracking intent; runtime service state is reported separately so a stale preference cannot claim that tracking is running.
- Foreground service is not bound to the activity and has stopWithTask=false. Flutter engine teardown disconnects events without stopping the service.

## Architecture and lifecycle

`MainActivity.kt` owns exactly one custom MethodChannel (`tracker/commands`) and one custom EventChannel (`tracker/locations`). It checks activity visibility before starting, validates permission/location availability, and reports PlatformException-compatible errors. It never retrieves location itself.

`LocationTrackingService.kt` owns the only Fused subscription. It promotes itself to a location foreground service before requesting updates. A subscription flag prevents repeated Start commands from duplicating requests. A generation counter rejects stale asynchronous startup completions and UI/notification updates after Stop. Stop removes updates and foreground notification and clears saved tracking intent. Already received fixes can finish their queued SQLite inserts after Stop.

`LocationDatabase.kt` uses SQLiteOpenHelper version 1. One shared executor serializes service inserts and channel reads off the main thread. The helper lives for the application process. Future schema versions must supply a migration; recorded routes are not silently dropped. History uses `timestamp DESC, id DESC` so equal timestamps have deterministic ordering; missing accuracy is SQL NULL.

`TrackingState.kt` stores native preferences and optional in-process event delivery. Events are dispatched on the main thread. An absent listener is a normal state. Service errors are included in status snapshots; persistence continues independently of Dart.

`TrackingController` fetches status, last fix and history in order, then subscribes and reloads to close the initial snapshot/listener gap. Each event refreshes authoritative native history; overlapping refreshes are coalesced, avoiding duplicate rows and response races. Resume reloads permissions and native state. Closing the controller cancels events but does not send Stop.

## Channel contract

No arguments are required. All timestamps are epoch milliseconds, IDs are integer SQLite row IDs, latitude/longitude are doubles, and accuracy is a nullable double in metres.

| Method | Result |
| --- | --- |
| `startTracking` | Status map; accepts startup, live status confirms success |
| `stopTracking` | Status map; live status confirms service cleanup |
| `getTrackingStatus` | Status map |
| `getLastLocation` | Location map or null |
| `getLocationHistory` | Up to 50 location maps, newest first |
| `requestForegroundPermission` | Status map or permission error |
| `requestNotificationPermission` | Status map or permission error; no-op below API 33 |
| `requestBackgroundPermission` | Separate runtime request on API 29; opens Settings on API 30+; no-op below API 29 |
| `openAppSettings` | null |
| `openLocationSettings` | null |

Location: `{id, latitude, longitude, accuracy, timestamp}`.

Status: `{running, starting, requested, foregroundGranted, precise, backgroundGranted, notificationsGranted, locationEnabled, error}`.

Events: `{type: "location", location: <location map>}` or `{type: "status", status: <status map>}`.

Error codes include `DATABASE_ERROR`, `LOCATION_PERMISSION_DENIED`, `LOCATION_DISABLED`, `APP_NOT_VISIBLE`, `PERMISSION_BUSY`, `PERMISSION_DENIED`, `PERMISSION_ERROR`, `ACTIVITY_DESTROYED`, `NATIVE_ERROR`. Denial details contain `permission` and `permanentlyDenied`; the UI always offers Settings rather than repeatedly prompting.

## Permission decisions and limitations

Fine and coarse permissions are requested together. Coarse-only access is supported with a balanced-power request and an approximate-location message. Background permission is never requested with foreground permission. On Android 11+, the separate background button explains the Settings path and the controller rechecks on resume.

A user-started location foreground service can continue while minimized with foreground permission. Background permission is optional for that uninterrupted session, and can permit location access on eligible restarts. It does not bypass Android's restrictions on starting services from the background. Start is only initiated while the activity is resumed, and Android 14+ location foreground-service type/permission are declared.

Notification denial does not prohibit starting a foreground service. The service still supplies its required notification, but Android may show the service only in its active-apps/task-manager UI. The Flutter screen explains the denied state.

START_STICKY requests recovery after eligible system termination; Android/OEM policy can prevent or delay it. Force-stop, Android's active-apps Stop action, reboot, and some OEM battery managers stop tracking. No boot receiver, exact alarm or misleading automatic force-stop workaround is included. On reopening after an interruption, the saved preference is shown separately and the user can tap Start. No location fix means waiting, not fabricated coordinates. Google Play services and GPS availability determine whether/when fixes arrive.

Official references:
- https://developer.android.com/develop/sensors-and-location/location/permissions/runtime
- https://developer.android.com/develop/sensors-and-location/location/permissions/background
- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- https://developer.android.com/about/versions/14/changes/fgs-types-required

## APK and signing

APK output: `build/app/outputs/flutter-apk/app-debug.apk` and `app-release.apk`.

This standalone project has no private release credentials. Its release-mode APK uses the local Android debug key for device testing; this must not be represented as a production-signed APK. No Supplyit signing keys were copied. To use your own release key, copy `android/key.properties.example` to `android/key.properties`, supply the four values, and rebuild. The Gradle configuration automatically selects that private release signing configuration when the file exists. Signing files are ignored by Git.

## Validation and submission

See `VALIDATION.md` for executed checks and results, and `DEVICE_CHECKLIST.md` for remaining real-device checks and recording steps.

Suggested commits (recommendations only; no commits made):
1. `chore: scaffold standalone Flutter tracker and Android configuration`
2. `feat: persist native location fixes with SQLite foreground service`
3. `feat: add native commands events and Android permissions`
4. `feat: add GetX tracking screen and history`
5. `test: verify tracker channels SQLite and service lifecycle`
6. `docs: add setup signing limitations and submission checklist`

For submission, push only this standalone project to the assignment repository, include the testing APK with its signing status, and record permission grant → Start → changing coordinates → notification → minimized/recent-task removal → reopen/history → Stop. Do not include private keys or local SDK paths in the repository.
