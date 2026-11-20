# Native tracking privacy boundary

Immediate exit detection and an accessibility `packageNames` allowlist cannot be used together. Android does not send an accessibility service an event when the foreground changes to a package excluded by that allowlist. That could leave the floating ball or continuous warning audio active after the user exits a tracked app.

The service therefore subscribes globally to only window-state transitions and scroll events. Its Kotlin handler applies these boundaries immediately:

- Window transitions read only the foreground package identifier. A transition to any untracked package stops audio and removes the overlay immediately. Window text and node content are never inspected or stored.
- Scroll events are rejected unless their package is Instagram, YouTube, or Facebook. Only accepted scrolls can inspect a small, transient node sample to identify a short-video surface.
- Persistence contains daily duration and swipe counters only. It never contains package-transition logs, screen text, node trees, or screenshots.

This slightly broadens which transition events reach the process, while retaining early filtering before content inspection. It is the minimum event visibility needed to meet the safety requirement that enforcement stops as soon as the tracked app loses the foreground.
