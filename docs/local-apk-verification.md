# Local APK verification — Companion (draft feature)

## UX

The APKDrop Companion **More** tab provides **APK-Datei überprüfen →**.
Users may choose an APK with Android's standard document picker. Compatible file managers can also share an APK with MIME `application/vnd.android.package-archive` into the Companion app.

Results appear in one local dialog, not on the Home screen. Existing link imports, Discover, App Library, DropTrust, DropUpdate, and the Android installer remain unchanged.

## What is checked

1. Read user-selected `content://` URI into a unique, temporary APK in the app-private cache; limit to **250 MiB** and delete after inspection.
2. Verify APK cryptographic signature using the **existing** `ApkSignatureChecks` / Android `apksig` implementation, using this device's SDK as the minimum checked platform.
3. Calculate the complete APK SHA-256 and extract package identity, version, Android SDK fields and declared permission count.
4. If that package is installed, compare the APK's verified signing certificates with the installed app and show a prominent difference warning. Count permission differences without claiming they are safe.
5. Clearly state that **provenance is unknown** for arbitrary files, and that DropPass was **not** checked. There is no malware scan, approval badge, installation bypass, background upload or automatic installer handoff.

A valid signature on an arbitrary APK proves only the bytes satisfy that signature, not that the publisher or source is trusted. Do **not** turn a successful manual verification into an APKDrop install authorization.

## Security requirements

- A shared APK must not be interpreted as a shared web link. `ACTION_SEND` with `text/plain` remains the existing link route; `ACTION_SEND` with the APK MIME is inspection-only.
- Reject invalid/unsigned/tampered APKs; never auto-install a user-picked file.
- Only accept `content://` picker/share URIs (the OS grants per-URI read permission).
- Never persist an external URI grant, private APK, content, or signing key.
- Temporary cache copy is deleted even after errors; deletion failures are reported.
- Already saved/installed APKDrop contracts still require their existing fresh local SHA-256, package, signer, version and permission verification, independently of this manual tool.

## Verification / release gates

New JVM fixture-based tests in `ApkSignatureChecksTest` cover signed, unsigned, tampered and truncated artifacts. `LocalApkInspectionUiTest` checks the **More**-only file picker path.

Before marking ready, run the full Gradle unit-test/lint/build suite on a supported Android SDK environment; verify signed APK fixture and UI tests. On a real phone, check Files picker, shares from file managers, 250-MiB cap, long file names, cancellation, invalid APK, valid signed APK, installed same-signer, installed different-signer, and no unintended installer. Test rotation and process-death cleanup.

Alpha 33 is the next upgrade candidate: package `de.rawinstinctai.apkdrop.debug`, versionCode `33`, versionName `0.1.0-alpha.33-preview`; the original alpha certificate remains pinned. Sign outside GitHub and verify with `alpha33-preview`. The physical-device upgrade gate remains mandatory. Do not publish or install a newly signed candidate on an owner's device automatically.

## Future DropPass integration

A later separate reviewed change can verify a signed `apkdrop.droppass.v1` proof against an independently trusted issuer key and the **same downloaded file SHA-256**. Until such integration ships, do not display a DropPass-verified result in this manual tool. FREY stays the private beta pilot; no private repository data is disclosed.

## Alpha 33 signed handoff (2026-10-14)

The PR's final app-source revision was tested with GitHub Actions run `38645017479` (tests, lint, unsigned debug/preview builds, and pinned unsigned manifest check PASS). This workflow has **no original signing key** and does **not** publish a release.

- Version: `0.1.0-alpha.33-preview`, versionCode **33** (Alpha 32: 32)
- Historical package: `de.rawinstinctai.apkdrop.debug` (unchanged)
- Certificate SHA-256: `6cf70241a63498e5e9fce78bac9abec760364cf320928347114bf109abd81e2e` (original Alpha signer)
- The **exact** unsigned preview artifact from this successful CI run was signed offline with the owner-provided original keystore. No private material or passwords were sent to GitHub or published.
- Signing and subsequent verification used the pinned Apache-2.0 Android `apksig 8.13.2` implementation. Full cryptographic verification for API 26–36: PASS; v2 and v3: PASS; one original signer: PASS.
- Verified the signed APK contains precisely the same **21 ZIP file entries and bytes** as the tested unsigned candidate; the APK Signing Block was added without changing app content.
- Final **signed APK SHA-256**: `adfc39d756a23527161bcca05b53530648209257af4d0bb576eee0b1fd35084b`.
- Final signed size: **341,256 bytes**.
- An altered copy of the signed APK was rejected by `apksig` as invalid.
- The original keystore remained local; a temporary Apache-licensed signing library export was removed from CI afterwards.

**Physical device gate remains OPEN:** install over the already installed Alpha 32 without uninstalling, verify local app state retained, browse **Mehr → APK-Datei überprüfen** with a valid APK, test a malformed APK, confirm a file-manager share opens inspection rather than installation, and validate a same-signer/different-signer comparison. No public release or production rollout is authorized by this note.


## Alpha 33 owner phone acceptance (2026-10-15)

The owner confirms updating the existing Companion app in place and **retaining saved apps**. A screenshot from the actual installed Companion shows a successful local check of the signed Alpha 33 preview: versionCode `33`, matching the expected signed APK SHA-256 `adfc39d756a23527161bcca05b53530648209257af4d0bb576eee0b1fd35084b`, original signer fingerprint `6cf70241a63498e5e9fce78bac9abec760364cf320928347114bf109abd81e2e`, agreement with the installed app's signer and zero newly requested permissions.

This is **positive evidence** for the update/data-retention flow and a valid signed-file check, not complete device acceptance. Malformed APK handling, file-manager share, different signer and edge-case behavior still require device testing. No malware safety or publisher-origin assurance was tested.

## Alpha 34 source candidate (awaiting CI & original signing)

- Scope: concise verdict first; separate technical APK details on request; remove the stale start-of-inspection Toast.
- Identity: `de.rawinstinctai.apkdrop.debug`, versionCode `34`, preview versionName `0.1.0-alpha.34-preview`.
- Original signer pin and existing device/local integrity checks remain intact. No additional Android permissions or network-based verification are introduced.
- Requires full Debug/Preview CI and manifest gate, a new locally original-signed preview with verified APK SHA and signing certificate, and on-device update check **before** distribution.
- PR #62 is stacked against the unmerged Alpha 33 PR #60. Keep PR #60's already issued signed APK and evidence immutable. Do not merge/ship Alpha 34 or claim a DropPass-issued credential prematurely.
