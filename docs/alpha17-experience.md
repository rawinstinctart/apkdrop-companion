# Alpha 17 — GitHub onboarding, details and public catalog

## Result

- GitHub access starts with a cancellable native explanation. Private-import capability is an unchecked, explicit option; the browser still approves the device. New repositories appear only within the GitHub App permissions selected by the owner.
- Read-only connections can discover suggestions and request a new consent flow for imports. Every private import shows fresh version, APK filename, size and repository visibility. Its frozen selection is sent only after confirmation, without a publication flag. A changed connection or subsequent request invalidates an old preview confirmation.
- While requesting new consent, the previous working device credentials remain in encrypted storage. Canceling or expiring the new pairing restores that connection, including after process restart. Successful approval replaces it.
- App descriptions are bounded display metadata, expandable and attributed to the developer. Missing descriptions and empty public developer profiles have specific explanations. Descriptions and images cannot authorize an install.
- Public catalog empty states explain where private drafts remain. Developer counts describe profiles on the current catalog page. Categories remain a controlled list; unknown metadata is shown as unassigned.
- Shared GitHub repository roots now use the existing verified public DropID mapping and fresh install contract. Private, expired, ambiguous or missing public mappings fail closed. Release links continue to choose the current APKDrop app standard, not an unverified historical GitHub asset.
- Version display avoids duplicate `v` prefixes. Numeric versionCode is explicit in APK evidence.

## Verification

Android CI exercises both debug and non-debuggable preview, including consent cancellation, private-import capability, fresh confirmation, read-only import refusal, incomplete APK previews, bounded/expandable descriptions, public mapping boundaries and actual repository-share takeover. Native 320 dp / 1.5 font previews cover consent and app description layout. Python tests pin Alpha 17 to versionCode 23, the existing alpha package and original certificate.

## Physical acceptance remains open

On the original Android installation, verify upgrade from Alpha 16.1 without uninstalling, consent cancellation and browser handoff, read-only access, an explicitly allowed private import, a newly authorized repository, revoked GitHub access, long descriptions and TalkBack. A real connected GitHub account and Android installer are not replaced by fixture tests. Publication remains a separate dashboard decision. Reliability and update recovery continue in Alpha 18.
