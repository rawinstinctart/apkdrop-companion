# Alpha 6 — Everyday Experience acceptance

## Scope
- Home uses only local, opt-in pinned apps and last-known release state; unknown/stale checks are not labeled as fresh verification.
- Five tabs: Home, Entdecken, Meine Apps, Updates, Mehr. The existing app detail flow and all security gates remain intact.
- Android text/plain share: exactly one APKDrop install link or plain slug, including a URL embedded in ordinary text. No auto-install or host bypass.
- Download cancel: stops the Future, invalidates stale UI callbacks and discards partial and late APKs. Cancel never advances the persisted update round.

## Validation (pending execution)
Run with JDK 17 / Gradle 8.13 / SDK 36, from a clean checkout:
```bash
python3 -m unittest discover -s scripts/tests -v
gradle --no-daemon -I scripts/unsigned-debug.init.gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

The tests cover share URL restrictions, initial navigation, Home shortcuts, update cancellation and the Alpha 6 release identity. They are code proposals until actually run: do **not** declare PASS from source inspection alone.

## Upgrade signing gate
The original Alpha 2–5 debug keystore is necessary to preserve on-device app data. Debug release identity is fixed:
- package: `de.rawinstinctai.apkdrop.debug`
- versionCode: `7`
- versionName: `0.1.0-alpha.6-debug`
- original signer SHA-256: `6cf70241a63498e5e9fce78bac9abec760364cf320928347114bf109abd81e2e`

Sign **only** in the previously verified key environment and check baseline before/after. Never generate a replacement signing key or log passwords. Verify a real signed candidate with:
```bash
python3 scripts/release-preflight.py /tmp/APKDrop-Companion-alpha6-debug.apk \
  --profile alpha6 --report /tmp/alpha6-release-check.json
```
Do not overwrite earlier reports. Use the existing guarded Gradle signing setup if the protected manual-signing variables are absent; reject any unpinned or newly generated signer.

## Device acceptance
Test on the user's authorized Android device using an **in-place upgrade**, preserving the saved list, follows and pending queue. Record the installed build and signer before/after and the APK hash. Verify:
1. Home empty/pinned/unknown/update states; five tabs and return navigation.
2. Android Sharesheet import of a valid link in plain text; malformed, foreign, ambiguous and parameterized links fail closed.
3. Cancel during a real slow download, retry, and confirm no installation occurs from cancelled bytes.
4. Full cryptographic verification, Android installer cancellation, fresh contract recheck, actual successful update and signer continuity.
5. Two-app update round, skip/stop, font scale 1.4, narrow devices, system bars/keyboard.
6. Notification opt-in/out, offline discover retries and existing DropID behavior.

If a real update fixture is unavailable, mark that case OPEN rather than PASS. FREY may be used as a read-only catalog fixture; do not change FREY releases.

## Production remains separately gated
A production APK needs the independently managed **production** key and a separately checked `--profile production` artifact. Device acceptance, hosted CI and a public download URL must succeed before any Cloudflare App Links bindings are activated. This PR does not deploy a Worker, alter billing or publish an APK.
