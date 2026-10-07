# APKDrop Companion

Native Android companion for verified APKDrop installs and updates.

## Alpha 1

The Companion consumes APKDrop's public `apkdrop.install.v1` contract and shows factual release identity before installation:

- immutable APK URL
- SHA-256
- exact signing-certificate fingerprints
- package name and numeric versionCode
- minSdk / targetSdk and native ABI compatibility
- permission changes against an already installed version

It does **not** invent a security score.

## Trust boundary

The app accepts only APKDrop links and talks only to:

`https://apkdrop.rawinstinctai.de`

The release contract is fail-closed. Signer mismatch, package mismatch, incompatible SDK/ABI and downgrades are blocked before the install flow is offered.

The local verifier already checks byte size, SHA-256, APK cryptographic signature via Google's `apksig`, package/version/SDK facts and permissions. The next slice adds the explicit user-controlled download and Android installer handoff.

## Build

Requirements:

- JDK 17
- Gradle 8.13
- Android SDK 36

```bash
gradle :app:testDebugUnitTest :app:assembleDebug
```

## Release signing

Release signing is intentionally not configured in source. Never commit keystore, private signing key, passwords or signing properties.

When the first production APK is externally signed, its certificate SHA-256 fingerprint becomes the public value used by APKDrop's gated `/.well-known/assetlinks.json`.

## App Links

The app declares `https://apkdrop.rawinstinctai.de/install/<slug>` with Android App Links verification. APKDrop keeps `assetlinks.json` disabled until the real production signing certificate and signed Companion APK exist.

## Privacy

Alpha 1 has no account requirement, analytics identifier, advertising SDK, device fingerprinting or hidden telemetry.
