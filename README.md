# APKDrop Companion

Native Android companion for verified APKDrop installs and updates.

## Alpha 3: Meine Apps

Apps are added explicitly after checking an APKDrop link. The local list stores only the slug, display name, package and pinned signer fingerprints; it survives app restarts. It contains up to 50 apps, with no account or cloud sync.

“Alle auf Updates prüfen” checks the saved apps in sequence without downloading APKs. Each row shows its actual installed version and the available release, with separate current, update, not-installed, blocked and failed states. A failed check does not discard other results. Checks can be cancelled.

Details show release notes and list added and removed permission declarations. RECORD_AUDIO is displayed as “Mikrofonzugriff (Audio aufnehmen)”, attributed to the target app. Download confirmation does not grant an Android permission.

Returning from the system installer refreshes the actual installed package state. A cancelled installation remains an install/update candidate; success is not inferred from opening the installer. Package changes also refresh tracked apps while the Companion is visible. The install policy and full APK verification run again before installer handoff.

The list menu can open an installed app or remove it from the list. Removing a saved entry does not uninstall the app. Background checks and update notifications are not part of Alpha 3.

## Verified installation flow

`APKDrop link → release facts → permission delta → explicit download → local verification → Android system installer`

The Companion consumes APKDrop's public `apkdrop.install.v1` contract and verifies the actual downloaded APK before Android is allowed to open it.

Local verification covers:

- exact byte size and SHA-256
- APK cryptographic signature through Google's `apksig`
- exact signer-certificate fingerprint set
- package name and numeric versionCode
- minSdk / targetSdk
- native ABIs parsed from the downloaded APK
- declared permissions
- installed signer continuity for updates
- no downgrade/current-version installation

Signer mismatch, hash mismatch, package mismatch, SDK/ABI mismatch and downgrades fail closed.

Sensitive new permissions are shown before the APK is downloaded. APKDrop does **not** invent a security score.

## Network boundary

The app accepts only APKDrop links and talks only to:

`https://apkdrop.rawinstinctai.de`

Redirects are not followed for the install contract or APK download. The APK URL must be the immutable APKDrop release URL from the verified contract.

## Installation boundary

APKDrop does not silently install or silently update apps.

After local verification, APKDrop hands the read-only verified APK to Android's system installer. Android remains authoritative and the user may need to allow APKDrop as an installation source once.

The verified APK is exposed only through a private, non-exported, read-only ContentProvider. Every completed download has a distinct filename and installer URI, so a subsequent download cannot replace another app's verified bytes. The private cache keeps at most two completed downloads; old candidates may need to be downloaded again.

## Build

Requirements:

- JDK 17
- Gradle 8.13
- Android SDK 36

```bash
gradle :app:testDebugUnitTest :app:assembleDebug
```

The GitHub Actions workflow performs the same test/build and publishes the debug APK only as a short-lived workflow artifact when runner capacity is available. Current Actions runs are blocked by exhausted included minutes; billing is unchanged. Alpha 3 requires the isolated native build described in [the verification record](docs/alpha3-verification.md).

## Release signing

Release signing is intentionally not configured in source.

Never commit keystore, private signing key, passwords or signing properties.

When the first production APK is externally signed, its certificate SHA-256 fingerprint becomes the public value used by APKDrop's gated `/.well-known/assetlinks.json`.

## App Links

The app declares `https://apkdrop.rawinstinctai.de/install/<slug>` with Android App Links verification. APKDrop keeps `assetlinks.json` disabled until the real production signing certificate and signed Companion APK exist.

## Privacy

APKDrop has no account requirement, analytics identifier, advertising SDK, device fingerprinting or hidden telemetry. It declares `QUERY_ALL_PACKAGES` to compare arbitrary target packages. Package queries are limited to the active install contract and explicitly saved entries; APKDrop does not enumerate or upload the installed-app inventory. The saved list stays in private preferences and backups remain disabled.
