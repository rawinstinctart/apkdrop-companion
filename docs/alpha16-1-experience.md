# Alpha 16.1 — Professional Experience

Continues #30 after PRs #34–#36. Ships all three earlier UI phases and this final consistency pass in an original-signed versionCode 22 preview.

- Home: one Discover action on first use, no empty counters/history sections, one update CTA. App names and the launch action have separate space.
- Library: useful titles and next steps. A search miss clears the search and restores the library; it does not unexpectedly open the catalog.
- Updates: current saved apps remain out of the queue. With no pending updates, return to the library or request fresh metadata. Link import belongs in My Apps.
- Details/settings: shared primary/secondary action spacing, selectable trust facts, announced release status, flexible vertical sizing.
- Accessibility: original TalkBack navigation fixes included, per-app menu names, decorative icons excluded, 48dp touch targets, 320dp / 1.5× font rendering.

Validation uses Robolectric fixtures (including large-font native renders), release-gate unit tests, Android lint, unsigned preview build, original certificate verification and byte-for-byte signed-artifact reconstruction. Fixture screenshots are not physical device evidence.

Physical acceptance remains open: upgrade Alpha 16 in place, check first-use/saved/current/update/blocked states, long app names, larger system text, Android installer return/cancel and offline/cold-start. Alpha 17 onboarding/discovery and Alpha 18 reliability are separate roadmap stages. This is not a 1.0 production-readiness claim.
