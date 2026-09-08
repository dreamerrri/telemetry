# Telemetry v0.9.0 — UI Overhaul

**Full talk-screen redesign and chat/fit polish.**

## Highlights
- **New talk surface** — shared radio UI for Direct, LAN rooms, and Cloud: header with CHANGE, calm speaking stage, docked chat, pinned PTT.
- **Chat dock** — collapsed peek with unread badge, tap to expand; auto-follows new messages only when you're at the bottom, with a "↓ N new" jump pill.
- **Small-screen fit** — chat feed auto-caps to window height so the PTT button is never pushed offscreen.
- **Live status line** — PTT line now shows real transport status (LAN talk/receive, cloud errors) instead of static text.
- **Streamlined flows** — lean Join screen, Direct setup → talk phases, settings overlay.
- **Cleanup** — removed dead screens, quality collection, and unused code; unified live-monitor state.

## Install
Grab `app-debug.apk` below, or build with `:app:assembleDebug`. Requires Android 8.0+ (minSdk 26).

**Full Changelog**: https://github.com/dreamerrri/telemetry/compare/v0.8.0...v0.9.0
