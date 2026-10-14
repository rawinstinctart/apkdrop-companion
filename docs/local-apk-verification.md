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
