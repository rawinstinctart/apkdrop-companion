# Alpha 9.1 — Android 14+ background update scheduling repair

Version `0.1.0-alpha.9.1-preview`, versionCode 12, original Alpha package and certificate unchanged.

## User report (Samsung screenshot, 2026-10-08)

Following a successful FREY takeover, the details showed the app as saved but automatic monitoring not scheduled. This is not an APK signing or install failure.

## Root cause and remedy

The job's builder specifies `setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)`. Starting with Android 14 (API 34), scheduling a network-constrained job requires the normal, install-time `android.permission.ACCESS_NETWORK_STATE` permission. The manifest did not declare it, and `UpdateScheduler.reconcile` catches rejected schedules and returns false without surfacing the underlying exception. This exactly matches the observed UI, but a device retest is required to confirm that the fix resolves this user's failure.

The manifest now declares `ACCESS_NETWORK_STATE` with no runtime prompt. No network state is read, logged or uploaded. On returning to the foreground, the app retries reconciliation; the settings text distinguishes planned monitoring from an unscheduled job. After takeover the inline monitoring status replaces a duplicate Android toast. For an already-current app, the details no longer repeat the same policy summary immediately before Release Radar.

The artifact preflight requires the new permission for versionCode >= 12, preserves the exact legacy permission allowlist for earlier Alpha packages, and blocks unexpected permissions. Production still requires an explicitly named production signer and separate deployment gate.

## Verification gates

1. CI: Python release-gate tests + Android debug/preview tests, lint and unsigned build, and manifest preflight with pinned alpha9.1 identity and permission set.
2. Original-key preview signing and independent release-preflight (no private key or password in GitHub).
3. **Physical Android 14+**: install Alpha 9.1 over Alpha 9 without uninstalling, verify saved FREY pin/follows, open its details, tap monitoring once if necessary, observe `Update-Überwachung aktiv`, verify a scheduled job is present, and confirm it eventually runs when Android permits.
4. Sharesheet, rotation, installer and notification behavior as per Alpha 9 remain device-acceptance gates; never imply that unit tests prove them.

No Cloudflare, FREY, HIOS production, Play Protect or production App Links changes.
