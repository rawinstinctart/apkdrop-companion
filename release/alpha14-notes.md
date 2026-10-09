Alpha 14 — APKDrop wird proaktiv 🤘

- **Smart Home:** Kontext für deine Apps und Updates, installierte Apps direkt öffnen und vorbereitete DropPilot-Updates mit einem Tipp prüfen.
- **Release Radar:** Ungesehene Releases hervorheben. Bereits installierte Versionen anhand von Paket, VersionCode und Signatur erkennen. Gelesene Releases bleiben lokal gespeichert.
- **GitHub Radar:** Entwicklerkonto einmal im Browser mit diesem Gerät verbinden. Neue Release-APKs aus autorisierten Repositories direkt in der Companion-App entdecken, inklusive künftig neuer Repos. „App hinzufügen“ öffnet den vorhandenen Import mit vorausgefülltem Repository und Beschreibung.
- Suche in „Meine Apps“ und hilfreiche letzte Prüfstände im Update-Überblick.

Bei GitHub „All repositories“ autorisieren, um spätere Repositories automatisch einzubeziehen. Bei ausgewählten Repositories neue Repos einzeln freigeben. Erkannt werden APK-Assets in GitHub Releases. Import und Veröffentlichung bestätigst du separat. Der Radar aktualisiert beim Öffnen oder ausdrücklich, ohne versteckte Hintergrundscans.

Originalsignierte, nicht-debuggbare Preview; Paket und Signatur passen zu Alpha 13. VersionCode 17. Vor Installation bleiben frische Release-Abfrage und lokale APK-Verifikation Pflicht. Physischer Android-Gerätetest bleibt offen.


## Release verification

Android CI: 145 tests passed for Preview and Debug; lint/build passed. Release gates: 37 tests passed. Backend: 136 tests passed, browser device-pairing test passed, migration and Cloudflare deployment completed.

The APK was signed locally with the original Alpha signer, then reconstructed byte for byte from the exact tested CI artifact. The keystore stays outside GitHub. Package `de.rawinstinctai.apkdrop.debug`, versionCode 17, version `0.1.0-alpha.14-preview`; Android 8+; not debuggable.

Physical device acceptance (in-place update, app launch and real-account GitHub connection) remains to be performed. Self-update and import of installed app inventory are planned for Alpha 15.

SHA-256: `c5d30661aba74148e89812ddc7821148bfb7c72af2ca71ad3adfe557e314e3a0`
