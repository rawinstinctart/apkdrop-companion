# APKDrop Companion

Native Android companion for verified APKDrop installs and updates.

## Alpha 1 flow

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

Alpha 1 does not silently install or silently update apps.

After local verification, APKDrop hands the read-only verified APK to Android's system installer. Android remains authoritative and the user may need to allow APKDrop as an installation source once.

The verified APK is exposed only through a private, non-exported, read-only ContentProvider.

## Build

Requirements:

- JDK 17
- Gradle 8.13
- Android SDK 36

```bash
gradle :app:testDebugUnitTest :app:assembleDebug
```

GitHub Actions performs the same test/build and publishes the debug APK only as a short-lived workflow artifact.

## Release signing

Release signing is intentionally not configured in source.

Never commit keystore, private signing key, passwords or signing properties.

When the first production APK is externally signed, its certificate SHA-256 fingerprint becomes the public value used by APKDrop's gated `/.well-known/assetlinks.json`.

## App Links

The app declares `https://apkdrop.rawinstinctai.de/install/<slug>` with Android App Links verification. APKDrop keeps `assetlinks.json` disabled until the real production signing certificate and signed Companion APK exist.

## Privacy

Alpha 1 has no account requirement, analytics identifier, advertising SDK, device fingerprinting or hidden telemetry. It declares `QUERY_ALL_PACKAGES` only because Android 11+ otherwise hides arbitrary target packages from `getPackageInfo()`; APKDrop queries only the package named by the active install contract and does not enumerate or upload the installed-app inventory.
