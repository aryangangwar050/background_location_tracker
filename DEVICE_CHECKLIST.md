# Device testing and recording checklist

Use a Google Play services device. Capture Android version/model and app build mode. Keep test results for debug and release distinct. Emulator checks do not establish OEM or real GPS reliability.

- [ ] Fresh install: empty state, no fabricated fix, loading finishes.
- [ ] Start: foreground permission dialog appears before notification request (API 33+).
- [ ] Deny foreground permission: service does not start; readable message and Settings button.
- [ ] Permanently deny: Settings grant then resume; retry succeeds.
- [ ] Approximate-only: service runs, approximate warning, reduced-accuracy fixes.
- [ ] Android 10: background permission requested separately.
- [ ] Android 11+: background explanation and Settings → Permissions → Location → Allow all the time; resume refreshes state.
- [ ] Deny background permission: user-started foreground session still tracks while minimized; warning remains.
- [ ] Deny notifications: no crash; hidden-notification warning; check Android active-apps panel.
- [ ] GPS disabled before Start: readable error and Enable action; enable then retry.
- [ ] GPS disabled/lost signal during tracking: no fake fixes or crash; waiting message; recovers when signal returns.
- [ ] Repeated Start: one foreground service/subscription; no duplicated location history IDs.
- [ ] Repeated Stop and rapid Start/Stop: no stuck Starting state, orphan service or notification.
- [ ] Every delivered fix: latest coordinates, accuracy and time; notification shows same fix.
- [ ] More than 50 fixes: newest 50 displayed in descending time/ID order.
- [ ] Minimize: moving device produces saved fixes and notification changes.
- [ ] Swipe app away: notification/service continue where platform allows; reopen displays saved fixes.
- [ ] Destroy Flutter activity/engine while service remains: SQLite still receives fixes with no event listener.
- [ ] Reopen while running: current status and persisted history are loaded without re-subscribing native location twice.
- [ ] Stop from notification: service cleans up and open Flutter UI shows Stopped.
- [ ] Force-stop: tracking stops; reopening does not falsely show Running; Start recovers.
- [ ] Reboot/OEM battery termination: document actual limitation; user restart may be required.
- [ ] Android 14+ physical device: start while visible, then minimize; no foreground-service security exception.
- [ ] Release-mode APK: repeat Start, minimize, reopen and Stop with real permissions.

## Short screen recording

1. Show device version and fresh app state.
2. Grant permissions, show separate background Settings flow.
3. Tap Start and walk/move until several fixes arrive.
4. Expand notification to show coordinates and timestamp.
5. Minimize/remove recent task, wait for another fix, reopen to show persisted history.
6. Stop and show notification removal; briefly show a denied/GPS-disabled error flow.

## Submission

- [ ] README and exact tested tool versions included.
- [ ] Source, pubspec.lock and Gradle wrapper included; build caches and local.properties excluded.
- [ ] APK labelled release-mode/debug-key testing build or private-key signed build accurately.
- [ ] Real-device test results filled in; untested cases identified.
- [ ] Repository URL and recording URL ready.
