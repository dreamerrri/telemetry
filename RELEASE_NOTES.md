# Telemetry v1.0.0 — Material 3 Expressive

**Full migration from the custom TalkNet UI to Material 3 Expressive, plus first-launch onboarding and an Android 14+ foreground-service crash fix.**

## Highlights
- **Material 3 Expressive redesign** — expressive motion (`MotionScheme.expressive()`), expressive shapes, and M3 components throughout, keeping the TalkNet dark brand: tonal primary/secondary buttons, `NavigationBar`, segmented transport toggle, `Card` rows with `RadioButton` selection, `AssistChip` quick texts, themed text fields and switches, expressive `LoadingIndicator`, chat `ToggleButton`, Material icons everywhere (no more hand-drawn Canvas glyphs).
- **Talk surface kept stage-calm** — PTT stays a custom press-and-hold surface for timing, now animated with the expressive motion scheme (spring scale, halo, transmit wave).
- **First-launch onboarding** — Welcome → Permissions (with rationale) → Battery exemption → Done, with an expressive progress bar; the foreground service no longer starts before onboarding on fresh installs.
- **Android 14+ crash fix** — the service idles as a `mediaPlayback` foreground service (no permission needed) and escalates to the `microphone` type only while transmitting, so fresh installs without `RECORD_AUDIO` no longer die with `SecurityException`.
- **Chat dock polish** — expressive send button, unread `Badge`, "N new" jump pill, themed message input.

## Install
Grab `app-debug.apk` below (debug-signed), or build with `:app:assembleDebug`. Requires Android 8.0+ (minSdk 26).

Grant **Microphone** (and Notifications on Android 13+) when asked, and accept the battery-optimization prompt so it keeps working with the screen off.

## Known limits
* Debug-signed APK only (no release signing yet).
* No end-to-end encryption (LiveKit transport security only; LAN is raw PCM).
* Guest/isolated WiFis block LAN entirely — hotspot mode is the fallback.

**Full Changelog**: https://github.com/dreamerrri/telemetry/compare/v0.11.0...v1.0.0
