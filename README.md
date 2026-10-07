# APKDrop Companion

Native Android companion for verified APKDrop installs and updates.

## Alpha 4: Smart Update Center

Alpha 4 adds local release explanations, a last-known release cache, metadata-only background checks and an explicit update round. No new runtime dependency, account, Google service, analytics or AI request is used.

- Saved apps sort by new sensitive permissions, ordinary update, blocked/failed check, not installed, unknown, current. Every restored result is labeled as the last known release with a date and time; failed checks cannot masquerade as successful updates.
- Release Radar compares permissions and SDK facts against the actual installed package. Size and ABI changes are compared separately against a previously checked release only when that baseline exists. Developer notes remain attributed to the developer. Contract signer continuity is not presented as completed APK verification.
- Android JobScheduler checks saved pins approximately every six hours when network and battery conditions permit; Android may defer execution. The setting is visible and can be disabled. The job never downloads APKs or enumerates the installed-app inventory. Permission-controlled local notifications deduplicate by versionCode and SHA-256 and open the exact app through an explicit intent.
- An update overview starts a persisted round. Each release is fetched freshly, each download and Android installation remains explicit. Cancelled/failed installs retain the same item; only confirmed PackageManager version and signer continuity advance the round automatically. Skip and stop remain available.
- Before download and again before installer handoff, a fresh contract must still describe the same artifact. The existing full APK cryptographic gate remains mandatory. Cached contracts never authorize installation.
- Saved snapshots are bounded, identity-checked and display-only. Removed entries cannot be recreated by an older job response. Snapshots, queue and pins stay in private preferences with backups disabled.
- The release receipt can be opened from app details. HTTPS install links and notification links open the matching slug; automatic verified web-to-app handling still needs production signing and the existing Worker release gate.

Native validation and remaining signing/device requirements are recorded in [Alpha 4 verification](docs/alpha4-verification.md). Alpha 3 device validation remains explicitly open, independently of the Alpha 4 JVM tests.

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
