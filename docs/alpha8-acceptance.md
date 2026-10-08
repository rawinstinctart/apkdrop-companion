# Alpha 8 — compact everyday experience

Version: `0.1.0-alpha.8-preview`, versionCode `10`.
Original alpha package `de.rawinstinctai.apkdrop.debug`, non-debuggable preview,
original uploaded signer. No keystore or signing secret belongs in source.

Implemented:

- Home: native app cards with installed icons or initial placeholders, local version/status,
  real recent check results, public releases of explicitly followed developers.
- Discover: inline search, horizontal category chips, compact sort/filter controls,
  app cards with real supplied icons, honest catalog signature status and DropID links.
- My Apps: focused library; the global check/update queue actions live in Updates.
- Details: download/add actions beside app identity, enlarging supplied screenshots,
  developer attribution, current changelog, expandable locally observed version history.
- Link import: APKDrop links plus narrowly parsed GitHub release links. The GitHub
  owner must have a published DropID with fresh GitHub evidence; an app must have an
  exact repository match and fresh repository evidence. No match, expired/private or
  ambiguous mapping blocks import. The current APKDrop default release is opened;
  an old shared tag is never substituted for the current installation contract.
- Polish: compact ripple actions, consistent initial placeholders, inactive nav icons
  muted, keyboard dismissed on navigation, actual installed version shown in Settings.
- Resetting a candidate clears old publisher media, signer proof and version history,
  preventing another app's details being displayed while the next candidate loads.

Installation still requires a fresh contract, exact package/version/signer/hash checks,
private APK storage and an explicit Android installer handoff. No arbitrary GitHub
APK download path, fake rating or malware verdict was added.

Local native rendering uses synthetic display fixtures solely for visual QA. Physical
Android upgrade, screenshot enlargement, share sheet, background job and installer
acceptance remain device checks. Production App Links are not activated here.

UI tests disable only the simulated animation clock. State restoration tests exercise
save/destroy/create explicitly because Robolectric API 26's visible/recreate loop can
stall in this runtime. Real device rotation remains part of device acceptance.
