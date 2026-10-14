# Local APK verification — compact experience (follow-up)

This UX change is **stacked on top of Alpha 33 / PR #60**. It does **not** alter the signed Alpha 33 APK, Android package identity, original signing key, versionCode or the existing install/update authorization. Do not publish an APK without a later independently signed/build-verified candidate.

## User-reported physical acceptance (2026-10-15)

The owner confirmed that a direct upgrade to original-signed Alpha 33 preserved their saved apps. A screenshot from a physical Android device confirms the local file checker ran successfully against the signed Alpha 33 APK: cryptographic APK signature valid, installed signer matches, versionCode 33, APK SHA-256 matches the offline-signed artifact `adfc39d756a23527161bcca05b53530648209257af4d0bb576eee0b1fd35084b`, and 0 additionally requested permissions.

**Not yet confirmed:** file-manager share, unknown/incorrect signer, malicious/truncated APK behavior on a physical device, device process recreation, and DropPass issuer verification. These remain separate gates. User screenshot remains in the conversation and is **not** checked into GitHub.

## UX improvement

- Prominent signed APK result, installed-signer continuity, newly requested permissions, and **DropPass: Nicht geprüft**. No security scores or falsely green malware badge.
- Signer mismatch is a persistent warning visible before expansion.
- SHA-256, X.509 signer fingerprints, SDK and all other technical evidence are hidden initially and exposed by an accessible, minimum-48dp disclosure control.
- The long-running operation shows a delayed (450ms), dismissible-by-completion progress dialog instead of leaving a long Toast under the result. The activity removes callbacks and dismisses the dialog on destruction.
- Avoid adding another Home button, backend request, notification, telemetry or permission.

## Build acceptance

- Three Robolectric tests cover closed/expanded evidence, explicit signer mismatch, unknown installed app, and provenance/malware disclaimers.
- Full CI native tests, lint, debug and preview build must pass on this *new* branch.
- This follow-up PR stays a draft and targets PR #60's feature branch until Alpha 33 is merged. It is **not** part of the already signed and device-tested Alpha 33 APK.
