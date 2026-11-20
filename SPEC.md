# DONTSTOP — Product specification

Tagline: **Keep going. We’re watching.**

## Purpose and release path

An offline Android app that responds to excessive social-media use with escalating meme audio. Test on the owner's phone first, distribute an installable APK to testers, then prepare a Google Play release. Support Android 10 and newer across manufacturers; compatibility must be verified rather than assumed.

## Architecture

- React Native with TypeScript for onboarding, dashboard, settings, and history.
- A small Kotlin module owns Android usage tracking, short-video detection, counters, floating overlay, and audio playback. Tracking must work without a mounted React Native screen or a running JavaScript timer.
- Native-owned local settings and daily storage are the source of truth. Use DataStore for settings and SQLite for daily aggregates and delivered reminder milestones.
- Expose a versioned native interface for status, settings, usage snapshots, and enabling/disabling tracking. Send UI updates only when values change and a screen is subscribed.
- Later migration replaces React Native screens with Kotlin/Compose while retaining the native module and data. Migration is optional if performance targets are met.
- No backend, account, cloud analytics, advertising SDK, or network dependency in the MVP.

## Tracked activity

- Initially support Instagram, YouTube, and Facebook; users choose which installed apps to connect.
- Time counts all foreground use of a connected app, including feeds and short videos.
- Swipe counts apply only inside Instagram Reels, YouTube Shorts, and Facebook Reels.
- Counts are estimates derived from accessibility events; raw scroll callbacks are not guaranteed to equal finger swipes. Deduplicate and validate against manual counts.
- Filter accessibility events to supported packages and required event types. Throttle eligible scroll processing to approximately one accepted event per 300 ms.
- Inspect only the minimum transient accessibility information needed to identify short-video surfaces. Never persist screen content, node trees, screenshots, or event logs.
- Daily time and swipe totals reset at local midnight. Retain the last 30 calendar days of per-app aggregates.

## Allowances and reminders

- During setup, choose time limits, swipe limits, and a positive whole-number reminder count for each metric.
- Time and swipe allocation are independent: either one shared allowance across connected apps or separate allowances for each app.
- With N reminders, milestones divide the allowance into N equal portions. Example: a 3-hour limit and 3 reminders produces gentle at 1 hour, intermediate at 2 hours, and savage at 3 hours.
- Default stage mapping: reminder i uses gentle when i/N <= 1/3, intermediate when i/N <= 2/3, otherwise savage. The final milestone is always savage.
- Reminder counts must produce distinct, meaningful milestones. Swipe reminders cannot exceed the swipe allowance; round fractional swipe milestones upward. Time reminders must be at least one minute apart. Explain invalid input inline.
- Reaching either applicable limit starts continuous savage playback while the affected connected app is foreground. A shared exceeded limit applies to every connected app; an individual exceeded limit applies to that app.
- Play clips sequentially, without overlap or intentional quiet intervals. Leaving the tracked app stops playback immediately. Returning resumes enforcement until the next daily reset.
- Prevent adjacent reuse of the same clip when at least two eligible clips exist. If only one clip exists, reuse is unavoidable and must be disclosed during asset validation.
- Deliver each reminder milestone once per day and allowance scope. When reconciling multiple crossed milestones, play only the highest current stage and mark earlier milestones consumed. Continuous enforcement takes priority over reminders.
- Limit, reminder, allocation, and tracked-app configuration changes activate the next day. Display active and pending settings clearly. Initial setup applies immediately.
- Users can disable tracking in DONTSTOP; there is no quick pause or snooze while tracking is enabled.

## Audio

- Bundle short meme clips with the APK, categorized gentle, intermediate, and savage. User imports and internet downloads are outside the MVP.
- Prefer compressed OGG clips shorter than five seconds. Load one clip at a time and release the player after playback or interruption.
- Requested behavior: speaker output even with headphones connected; reminders at 75% volume and continuous warnings at 100%.
- Setup lets users choose whether to respect or attempt to override silent and Do Not Disturb modes. Respect mode is the proposed default.
- Suspend playback during phone calls; resume only if an affected app is still foreground afterward.
- Android audio routing, volume, DND access, and manufacturer behavior require an early feasibility test. Do not promise unsupported overrides or disguise failures. Restore app-adjusted system volume when playback ends where possible.
- Publicly distributed audio must have documented permission or a license allowing distribution. Internet availability alone is not distribution permission.

## Floating ball

- One small static icon, visible only over connected foreground apps.
- Drag anywhere; snap to the left or right edge after release. Remember position and keep it within usable screen bounds.
- Tap to reveal remaining time, remaining short-video swipes, and applicable limits. Show shared or per-app scope explicitly.
- Clamp remaining allowances to zero after exceeding a limit; show an exceeded state.
- Dismiss the panel by tapping outside. No pause action. Animated ball/GIF support is deferred.

## Dashboard and permissions

- Show today's per-app and shared totals, remaining allowances, enforcement state, and 30-day history.
- Explain accessibility, usage access, overlay access, and any required notification/DND access before opening Android settings.
- Show clear tracking status and links to fix missing permissions; never imply protection is active when required access is missing.
- Handle screen-off, lock, app switching, midnight, process restart, and reboot without duplicate reminders or background audio leakage.
- No tracking or audio on the lock screen. Offer deletion of usage history without silently disabling enforcement or replenishing today's allowance; full reset must be explicit.

## Performance targets

- Aim for 30–50 MB idle RAM and under 1–2% incremental battery per day during ordinary tracking. Treat these as measurement goals, not guarantees; measure continuous playback separately.
- Prefer event-driven tracking. Reconcile usage periodically rather than polling every second; schedule a one-shot threshold callback for timely active-session reminders.
- Update overlay content only on state changes or when opened. No permanent redraw timer.
- Start with a three-minute usage reconciliation interval while tracking needs it; adapt scheduling to Android lifecycle constraints and measurement results.
- Assess whether a foreground service is necessary before adding one. If required, use one lightweight service with the appropriate declared type and notification; do not duplicate the accessibility tracker.
- Use release shrinking, minimal dependencies, and compressed assets. Approximately 5 MB is an aspirational APK target, especially with React Native and bundled audio; report measured size.

## Unresolved decisions and release gates

- User explicitly deferred behavior when short-video detection breaks. Proposed safe development fallback: pause affected swipe counting, retain whole-app time tracking, and show degraded status. Confirm before finalizing production behavior.
- Exact first test phone, supported app versions, final visual assets, and audio pack remain to be supplied.
- Google Play permission/disclosure requirements and accessibility eligibility must be reviewed against current policy before publication.
- Prove screen detection, background independence, speaker routing, call interruption, and volume behavior before polishing the full UI.
