# DONTSTOP — Implementation tasks

Reference: [SPEC.md](SPEC.md) and [DESIGN.md](DESIGN.md).

## 1. Prove the Android features

- [ ] Confirm available Android SDK, JDK, Node, and React Native tooling; establish a reproducible debug build on a physical Android 10+ phone.
- [ ] Build a small Kotlin spike for foreground-app detection and UsageStats reconciliation.
- [ ] Verify Reels/Shorts surface detection separately for Instagram, YouTube, and Facebook; compare counts with manually observed navigation and exclude comments, feeds, and ordinary scrolling.
- [ ] Test speaker routing with wired/Bluetooth headphones, requested volumes, silent/DND choices, and call interruption. Record feasible behavior by device.
- [ ] Prove the tracker and overlay work when the React Native dashboard is unmounted and JavaScript is not driving scheduling.
- [ ] Review current Android background-service constraints and Play accessibility policy; choose the minimum valid service model.
- [ ] Confirm the deferred detection-failure behavior and obtain a starter audio pack with distribution rights.

## 2. Native tracking and persistence

- [ ] Create native settings and SQLite daily tables with schema versioning, 30-day retention, and restart-safe reminder state.
- [ ] Implement independent shared/per-app scopes for time and swipes, initial setup, and next-day pending configuration.
- [ ] Implement package filtering, transient feed identification, scroll deduplication, and approximately 300 ms throttling.
- [ ] Implement foreground duration accumulation, periodic reconciliation, and one-shot milestone scheduling without per-second polling.
- [ ] Handle local-midnight rollover, time-zone changes, screen lock, process recovery, and reboot; prevent double-counting and duplicate reminders.
- [ ] Define the TypeScript/native contract for settings, snapshots, status, and tracking controls; keep storage and enforcement native-owned.

## 3. Reminders, audio, and overlay

- [ ] Implement evenly divided milestones, three-stage mapping, and input validation for reminder counts.
- [ ] Implement one-at-a-time clip selection, no adjacent duplicates, release-on-completion, and continuous savage enforcement.
- [ ] Stop immediately on app departure, screen lock, calls, or tracking disable; resume only when the correct foreground condition returns.
- [ ] Implement validated audio routing and volume behavior, DND choice, and restoration of modified volume.
- [ ] Build the draggable native ball, edge snapping, saved position, bounds handling, and tap-to-expand allowance panel.
- [ ] Surface permission loss and degraded tracking honestly.

## 4. React Native experience

- [ ] Build setup: connect apps, choose separate allocation scopes, set allowances and reminder counts, choose silent/DND behavior, explain permissions.
- [ ] Build today's dashboard, per-app details, 30-day history, settings, pending-change summary, and clear tracking status.
- [ ] Apply the playful design in DESIGN.md; verify screen-reader labels, contrast, large text, and reduced-motion behavior.
- [ ] Add explicit tracking disable and history deletion flows; avoid a hidden quick-pause feature.
- [ ] Provide friendly empty, loading, permission-missing, detection-degraded, and unsupported-audio states.

## 5. Validate and ship

- [ ] Unit-test allowance scopes, milestone mapping (including 1, 2, 3, and larger counts), rounding, duplicate suppression, and next-day settings activation.
- [ ] Test shared time + individual swipes, individual time + shared swipes, and simultaneous time/swipe thresholds.
- [ ] Device-test leaving/reopening apps, calls, headphones, DND, lock/unlock, midnight during playback, reboot, revoked permissions, and unsupported feed layouts.
- [ ] Compare swipe estimates against manual samples for each app; report accuracy and known failure cases.
- [ ] Measure release idle RAM, incremental tracking battery over 24 hours, separate continuous-audio battery, startup, and actual APK size.
- [ ] Test at least Android 10 and a current Android version, plus representative Samsung, Xiaomi, and Oppo/Vivo devices as available; document gaps.
- [ ] Produce a signed test APK; keep signing secrets outside source control. Fix field-test failures before release preparation.
- [ ] Prepare privacy disclosures, accessibility declaration, audio-license inventory, store listing, and signed Android App Bundle. Publish only with explicit authorization.

## 6. Optional Kotlin migration

- [ ] Migrate only after evaluating whether the React Native build misses measured targets or adds maintenance cost.
- [ ] Replace screens with Kotlin/Compose while preserving native contracts, storage schemas, counters, and pending settings.
- [ ] Verify an in-place upgrade keeps user data and enforcement behavior intact.

## MVP acceptance

An offline Android 10+ APK tracks whole-app time and supported short-video swipes, applies chosen scopes and reminders, continuously roasts after either limit only while the affected app is visible, stops during calls, shows the movable ball, and preserves daily state across restarts. Unsupported behavior and measurement gaps are documented. Resource targets and audio overrides are claimed only after successful testing.
