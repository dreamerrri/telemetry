# Telemetry v1.0.0 — official release

**Telemetry is a push-to-talk walkie-talkie for Android.** Two transports behind one TalkNet UI: **LAN Direct** for phone-to-phone voice over local WiFi (no internet, no account), and **Cloud Channels** for internet rooms via LiveKit. Everything audio runs in a foreground service, so it keeps talking with the screen off.

## Two ways to talk

* **LAN Direct** — 16 kHz PCM voice over UDP (`:50005`). Both phones on the same WiFi (or one phone's hotspot) and you're talking. Nothing leaves the local network.
* **Cloud Channels** — Opus voice over LiveKit WebRTC. Any word is a channel — type it, join it, no creation step. Room names allow letters, numbers, space, `_` or `-` (max 64).

## Pushing to talk

* Big round hold-to-talk button everywhere, plus volume-down PTT and a Live monitor mode where the mic stays open.
* On Cloud, the mic is muted by default until you hold to talk; on LAN you can also pin a peer and open a dedicated LAN Talk screen.
* Home lists pinned rooms (Office / Warehouse / Remote / Security), recent rooms, and JOIN BY WORD.

## Finding people (LAN)

* Phones broadcast presence beacons every 2s (`:50006`): name + IP + battery, expiring after 10s.
* NEARBY lists show avatars, battery %, and staleness — tap to dial, with manual IP fallback when broadcasts can't get through.
* Multi-homed and hotspot-aware: each interface beacons, and the app picks a sensible own-IP.

## Messaging & health

* Quick texts (presets, one per line, max 8): LiveKit data messages on Cloud, `TXT|` sidecar packets on LAN.
* Connection-quality dots on cloud avatars (mint / amber / red) so you can see a bad link before you talk over it.

## Built to survive the real world

* Foreground service (`microphone` type) with a sticky notification + Stop action. On Android 14+ it idles as a `mediaPlayback` service and escalates to the mic type only while transmitting, so a fresh install without mic permission can't crash it.
* First-launch onboarding: Welcome → Permissions (with rationale) → Battery exemption → Done. The service never starts before onboarding completes.
* Jetpack Compose + Material 3 Expressive dark theme, and the official app icon: mint mic + telemetry waves on `#14262A`, with a monochrome themed-icon variant.

## Get started

1. Grab `app-release.apk` below (release-signed) and install it. Requires Android 8.0+. (`app-release.aab` is the Play Store upload bundle — not for direct install.)
2. Grant **Microphone** (and Notifications on Android 13+) when asked, and accept the battery-optimization prompt so it works screen-off.
3. **LAN:** open the LAN tab, set your name, tap a NEARBY peer (or type an IP), open LAN Talk, hold to talk. Guest WiFis with client isolation block device-to-device traffic — use the main SSID or hotspot mode.
4. **Cloud:** open the CLOUD tab → `+ Join` → name + channel → Join. First run needs the LiveKit URL + token-server URL (Server settings) — the app never holds the LiveKit API secret; a tiny Cloudflare Worker mints short-lived join tokens (see `token-server/README.md`).

## Verify the download

SHA-256 (`app-release.apk` 61 MB, `app-release.aab` 36 MB):

```
EBC0C9AD55A58B23D85E20CA129075F7E69B8AA00D841C1B989DB40FFAA6A0B6  app-release.apk
EE77C8672BDD27BC749E117E2C7FD0CFF4CF7AD4D643F194B64D1704B8A3DE43  app-release.aab
```

Signed with the official Telemetry release key (4096-bit RSA, self-signed, valid to 2056):

```
Signer cert SHA-256: 20:74:63:B6:B5:8C:BC:3D:AD:B3:DD:B3:07:88:CC:73:F5:F1:AE:1A:E8:3B:04:3B:8F:0A:FE:A7:D8:D9:C7:38
```

Spot-check with `apksigner verify --verbose --print-certs app-release.apk` — expect `Verified using v2 scheme (APK Signature Scheme v2): true` and `Signer #1 certificate DN: CN=Telemetry, OU=Telemetry, O=Telemetry, C=US`.

## Known limits

* No end-to-end encryption (LiveKit transport security only; LAN is raw PCM).
* Guest/isolated WiFis block LAN entirely — hotspot mode is the fallback.
