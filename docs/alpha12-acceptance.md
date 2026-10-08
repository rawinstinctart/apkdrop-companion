# Alpha 12 — Premium Experience

Alpha 12 preserves Alpha 11's preview package, original signer and local data while increasing versionCode to 15. The preview remains non-debuggable. No signing key or password is stored in the repository or uploaded to GitHub.

## Changes

- App details put version, state and primary install/download action first. Package ID, full signer/hash/SDK evidence and permission details stay in the Trust Center. Versionsvergleich and observed history expand on demand.
- Release notes use native Android text spans for headings, lists, emphasis, code and human-readable links. The initial preview is bounded; More/Less retains full notes. HTML is literal text; images do not trigger requests; links do not dispatch URI schemes. This deliberately supports a Markdown subset, not arbitrary GitHub HTML.
- DropPilot reports actual job starts, stages/progress and completion outcomes. Scheduling is never represented as successful execution. Live connection/charging observations explain waiting; Android still controls execution. A completed cached download is shown only when its file and pinned installed identity still exist. Installation rechecks the current contract and APK through the existing verification flow.
- Interrupted runs do not remain falsely active after process death. Opt-out clears prepared downloads; stale job callbacks cannot record downloads into a later run.

## Automated validation

Android unit tests cover native formatting, untrusted content, More/Less, collapsed detail sections, job history, stale callbacks and missing-cache/process-death cases. Existing download, identity, installation, backup and scheduling suites remain required. Release policy pins the original certificate, package, versionCode 15, non-debuggable preview and permission set.

## Device acceptance (user)

Install over Alpha 11 without uninstalling. Confirm saved apps/follows survive, FREY notes render with More/Less, comparison/Trust Center expand, and DropPilot shows waiting/last-run status accurately. A real automatic prefetch followed by local verification/Android installation is still a separate physical-device check. Production signing and verified production App Links remain later work.
