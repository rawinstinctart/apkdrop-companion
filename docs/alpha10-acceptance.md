# Alpha 10 — DropPilot & Mission Control

Identity: `0.1.0-alpha.10-preview`, versionCode 13, same original Alpha package and signer.

## User-visible features

- Home gives the active monitoring state and keeps the existing single update action primary.
- Settings → **DropPilot · Updates im WLAN vorbereiten** is explicitly opt-in, default OFF.
- DropPilot schedules network-unmetered, charging, battery-not-low jobs, at most one verified APK per run, maximum 64 MiB. The schedule is Android-controlled, not guaranteed at a particular time.
- DropPilot downloads only a current APKDrop release for an explicitly pinned, installed app with an update. Server install-contract identity is checked fresh before and after download; SHA-256, APK signature, package, version, ABI, SDK, permissions and existing signer are checked on-device. Failure deletes the candidate. App private preferences bind the candidate to its exact release and package.
- User opt-out cancels both jobs and removes the prepared APK. The cache is physically separate from currently verified installer handoffs.
- Opening an app with a prepared update shows **DropPilot · Update vorbereitet, lokal prüfen**. Only after explicit user action, fresh contract checks, a separate copy, and a second full verification is the normal Android installer button offered.
- No silent APK installation. No automatic app inventory scan, cloud account, analytics or extra permissions. Unknown/high-risk releases and changed signers fail closed.
- Human-readable permission changes for modern Android permissions.

## Gates before a signed preview is called distributable

- Tests for default-off, unmetered/charging constraints, local pin continuity, disabled/removal cases, release mismatch, original manifest allowlist and the unchanged installer.
- CI debug & preview unit tests, lint and builds + independent unsigned preview manifest inspection.
- Original Alpha keystore signing, APK cryptographic signature verification and release preflight.
- Real-device background execution, Android lifecycle, installer, rotation and OEM battery behavior remain explicitly OPEN pending user acceptance.

## Limits

DropPilot does not promise an update will be ready before the user opens the app. Background execution depends on Android's network/battery policy. A missing/evicted prepared APK falls back to the ordinary secure download path. No unsupported key rotations, arbitrary GitHub APKs or split APKs.

No Cloudflare or FREY changes are part of Alpha 10.
