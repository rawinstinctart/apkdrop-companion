# Alpha 9 — share a link, take over an app

Version: `0.1.0-alpha.9-preview`, versionCode `11`. Same alpha package and original signer.

1. Share one APKDrop app/install link or supported GitHub release URL to APKDrop.
2. APKDrop opens the current app's reviewed details. GitHub links must resolve through
   a published DropID with current verified GitHub and exact repository evidence.
3. Tap **Übernehmen & Updates überwachen +**. This saves the app's slug, package and
   signer pin locally, enables background checks, and schedules a persisted metadata job.
4. Details show **App übernommen ✓** and the actual scheduled monitoring state.
   Android controls execution, approximately every six hours with network and adequate battery.

No additional installation or notification permission is needed to save a pin. Adding
does not download an APK or start the Android installer. Denied notifications do not
disable monitoring; results remain available in the app. Automatic APK installation
is never enabled. Monitoring can be disabled in Settings and re-enabled explicitly
from the saved app's details. A failed scheduler is shown honestly and can be retried.

Pending shared links survive saved-instance-state restoration. The top activity
accepts new shares through `singleTop`. A new share takes precedence over an old
update queue when no installer handoff is pending. Existing pending-installer recovery
continues to take precedence during a cold launch.

Tests cover APKDrop and verified GitHub shares through the real parser/resolver,
details, one-tap pinning, scheduler and storage flow using fixture network responses.
They cover expired mapping rejection, cold update queues, new intents, saved state,
explicit opt-out and reactivation. Native preview images use synthetic demo content.
Real-device sharesheet, upgrade, rotation, job execution and installer acceptance
remain physical device checks.
