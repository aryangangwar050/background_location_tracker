# Validation report

Executed on 13 October 2026. Tool versions are in README. Device checks used a temporary read-only Android 15 / API 35 arm64 emulator session with simulated GPS fixes, not a physical device.

## Passed

| Check | Result |
| --- | --- |
| `flutter analyze` | No issues found |
| `flutter test` | 6 tests passed |
| `flutter build apk --debug` | Final debug APK generated successfully |
| `flutter build apk --release` | Final release-mode APK generated successfully, Flutter reported 46.0 MB |
| `apksigner verify --print-certs .../app-release.apk` | Signature verified; certificate CN=Android Debug |
| `aapt dump badging .../app-release.apk` | Minimum SDK 24, target/compile SDK 36; all six required permissions; arm64-v8a, armeabi-v7a, x86_64 |
| Direct Android instrumentation | `OK (2 tests)` against the installed release-mode testing APK |

Flutter tests verify ordered initial native snapshots, Start/Stop channel calls, permission request order, denied foreground permission preventing service startup, repeated live events without duplicate history rows, empty database UI, notification denial still allowing Start, and native database error ending loading.

Native SQLite instrumentation verifies empty reads, insertion of 55 fixes, latest-50 limit, descending timestamps/IDs including timestamp ties, nullable accuracy, last-location retrieval and data surviving helper close/reopen. Native service instrumentation verifies repeated Start, Running status, persisted requested preference, foreground notification, continuation with the activity stopped, repeated Stop, preference cleared, and notification removal.

## Additional emulator checks against release APK

- Visible Start created exactly one foreground location service; `dumpsys activity services` showed `isForeground=true`, notification ID 1 and service type `0x8` (location).
- Simulated GPS coordinates reached the Flutter UI and native history; notification displayed coordinates and fix time.
- Minimized the app and injected another fix; notification continued updating.
- Removed the tracker task using `am stack remove <tracker task ID>`. Activity dumps showed no tracker activity while the foreground service remained. A subsequent GPS fix updated the notification without a Flutter activity/engine listener. Reopening showed that fix in saved history and Running status (22 history records at that check).
- Reset this test app's permissions. Denied the actual Android foreground location dialog: no service started; UI showed permission guidance and Settings.
- Retried and granted Approximate / While using the app. The notification request appeared separately afterward. Denied notifications: the app still showed Running, the foreground service remained active, the UI showed both permission warnings and an approximately 2000 m accuracy fix.
- Background location remained ungranted during the minimized/task-removed session; the user-started foreground session continued.
- Disabled device location before Start: no service started; UI showed “Enable device location services, then retry” and the Enable action. Restored the setting afterward and restarted successfully.
- Force-stopped an active session and reopened: no service auto-started; UI showed Stopped and “Tracking was interrupted. Tap Start to resume.”
- Separate background-location button opened this app's Android App info Settings page (Android 11+ flow).
- Captured `docs/tracker-preview.png` from the running release APK with simulated GPS coordinates.

## Infrastructure issues encountered

`connectedDebugAndroidTest` compiled app and test APKs, but Google's Unified Test Platform core dependency download failed with a TLS handshake error. The compiled test APK was installed and run directly with `adb shell am instrument -w .../androidx.test.runner.AndroidJUnitRunner`; both native tests passed.

The existing emulator had limited free storage and rejected the universal debug APK with INSTALL_FAILED_INSUFFICIENT_STORAGE. The smaller release APK installed successfully and was used for the native and UI checks. Debug APK generation itself passed. A first direct instrumentation attempt against a shrunk release failed because test references expected unmodified Kotlin class names. Native shrinking was explicitly disabled for this interview/testing project, then the rebuilt release passed both native tests.

A swipe gesture on the headless emulator's recents screen did not reliably remove the task, so task/engine removal was verified with Android's task-manager command instead. Actual swipe behavior on physical devices remains on the checklist.

## APK signing status

The release APK is signed with the local testing debug certificate, not a private production key. SHA-256 certificate digest:

`F0:B4:B8:6B:87:ED:B0:51:48:72:B4:20:0A:43:A9:BA:97:1F:08:69:D6:20:F8:CA:6B:02:44:88:3F:4B:48:16`

Private signing configuration is supported through ignored `android/key.properties`; no Supplyit credentials were reused.

## Still requiring device testing

No physical device was connected. Android 7–14/16+, real GPS loss/recovery, real recents swipes, OEM battery/process termination, reboot behavior, notification Stop interaction, Android 10 background runtime dialog, granting “Allow all the time” through Settings, permanently denied permission recovery, and sustained rapid Start/Stop stress need physical-device or additional API-level verification. No claim is made that a foreground service survives force-stop, reboot, or every OEM termination.

See DEVICE_CHECKLIST.md for recording and submission steps. Repository upload, a physical-device recording, and production signing (if required by the reviewer) remain submission actions; no remote repository was created or pushed.
