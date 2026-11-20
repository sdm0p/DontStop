# DONTSTOP — Experience and visual design

**Keep going. We’re watching.**

## Direction

Playful, colorful, sarcastic, and meme-like. The interface should feel like a cheeky companion while keeping allowances and permission status easy to understand. Sarcasm belongs in headlines and feedback; permission explanations and errors stay literal.

Proposed visual defaults, pending user review:

- Warm cream canvas `#FFF8ED`, ink `#202020`, purple primary `#7047EB`, yellow highlight `#FFD84D`, coral warning `#F46B62`.
- Bold headings, readable Android system body typography, rounded cards, thick outlined illustrations, and restrained offset shadows.
- A simple illustrated watching-eye mascot for the app and floating ball. Use a static vector in the MVP.
- Use dark ink for text on light accents; verify contrast for every actual combination. Pair all stage colors with labels and icons.
- Short celebratory or sarcastic transitions only while the dashboard is visible. Honor reduced motion; no idle animation in the tracker or ball.

## Setup flow

1. Welcome: app name, tagline, short explanation, and “Set my limits.”
2. Connect installed Instagram, YouTube, and Facebook apps. Explain whole-app time versus Reels/Shorts-only swipes.
3. Time allowance: choose shared or per-app, enter limits, and select a positive reminder count.
4. Swipe allowance: independently choose shared or per-app, enter limits and reminder count.
5. Preview the resulting milestone schedule with gentle, intermediate, and savage labels. Example: 3 hours / 3 reminders → 1h, 2h, 3h.
6. Audio: explain bundled meme clips, proposed speaker-only 75% reminders and 100% continuous warnings, and show respect/override silent and DND choices. Explain any device limitations discovered during the spike.
7. Permissions: explain each required access, open its Android settings page, and refresh status on return.
8. Review: summarize active rules and daily midnight reset; enable tracking only when required access is available.

Do not show configuration defaults as user-approved choices. New users must choose their allowances during setup.

## Dashboard

- Header: DONTSTOP and a short rotating text line, e.g. “Your thumb has been busy.” No automatic audio on this screen.
- Status card: Tracking active / needs attention / disabled, with a concrete repair action when needed.
- Today's allowance cards: time and swipes, remaining values, limits, and shared/per-app labels.
- App cards: Instagram, YouTube, Facebook totals, remaining individual allowances where applicable, and short-video tracking health.
- Reminder preview: next milestone and current stage.
- History: compact 30-day charts, with metric toggle and per-app filtering; exact values available as accessible text.
- Settings entry and visible pending-changes indicator. Never mix tomorrow's limits with today's active limits.

## Floating ball and panel

- Proposed size: 48 dp visible icon/touch target, with adjustable placement. Keep screen obstruction low.
- A drag moves the ball; release snaps it to the nearest left/right edge. A tap opens a compact panel without changing limits.
- Panel shows current app, remaining time, remaining Reels/Shorts swipes, both limits, and each metric's scope.
- Exceeded values read “0 remaining · limit reached.” Do not use negative remaining counts.
- Close by tapping outside. Hide both ball and panel as soon as the foreground app is no longer connected or the screen locks.
- No snooze or quick pause. Provide a route to DONTSTOP settings for explicit tracking disable.
- Reposition within safe bounds after rotation or screen-size changes; avoid system gesture areas.

## Roast states

| State | Presentation | Example text |
| --- | --- | --- |
| Under allowance | Calm playful status | “Plenty of scrolling left. Allegedly.” |
| Gentle | Yellow label | “Just checking. Still here?” |
| Intermediate | Purple label | “Your thumb deserves overtime.” |
| Savage / exceeded | Coral label | “Limit reached. The memes have the microphone.” |
| Tracking unavailable | Neutral repair card | “Tracking paused. Accessibility access is off.” |

Roast examples are proposed interface copy, not approved audio assets. No shaming about appearance, identity, health, or personal worth.

## Settings and edge states

- Show active configuration and tomorrow's scheduled changes side by side with an effective-date label.
- Tracking disable is an explicit dashboard setting. Re-enabling preserves today's usage and resumes any applicable exceeded-limit behavior.
- Permission failure: say which access is missing and link to the appropriate system setting.
- Detection failure: show “Swipe tracking unavailable” for the affected app; final fallback policy remains undecided.
- Audio limitation: clearly identify unsupported routing or override behavior instead of claiming successful enforcement.
- Empty history: “Your scrolling record starts here.”
- Audio interruptions: no roast over phone calls; reflect suspension only if the user is looking at status.
- Destructive controls use clear wording and confirmation. History deletion must explain whether today's active enforcement counters remain; full reset is a separate action.

## Accessibility and performance

Use at least 48 dp interactive targets, meaningful labels, scalable text, and no color-only status. Avoid flashing effects. Keep all animations in visible UI, bundle lightweight vector assets, and render charts only when the history screen is open. Device permissions and performance facts take priority over sarcastic wording.
