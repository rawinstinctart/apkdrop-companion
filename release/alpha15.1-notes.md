# APKDrop Companion Alpha 15.1 — Entdecken-Oberfläche repariert

Kleines, gezieltes Update auf Basis von Alpha 15: gleicher Paketname und ursprünglicher Signierschlüssel, **VersionCode 19** für ein normales Update.

## Was wurde verbessert?
- Statt „1 App · Seite 1 / 1“ wird **„1 App“** angezeigt. Bei mehreren Seiten bleiben die Seiteninformationen erhalten.
- Die Sortierung ist ein kompakter Button **„Neueste ▾“** mit zwei nachvollziehbaren Optionen: „Neueste zuerst“ und „Name A–Z“.
- Trefferanzahl links, Sortierung und Aktualisieren rechts: kein unnötig leerer Bereich mehr.
- Offline-Katalog-Hinweise bleiben sichtbar; keine Änderung an den geprüften Installations- und Update-Sicherheitsregeln.

## Integrität
- Paket: `de.rawinstinctai.apkdrop.debug` (historischer Paketname, unverändert)
- Version: `0.1.0-alpha.15.1-preview`, VersionCode `19`, Android 8.0+ (minSdk 26), targetSdk 36
- Originales Zertifikat SHA-256: `6cf70241a63498e5e9fce78bac9abec760364cf320928347114bf109abd81e2e`
- Originalsignierte APK SHA-256: `b4800e348cd86d18683bdab0e9c33cfcccb01cd7387425d54470b75adf6039f3`
- v2/v3-APK-Signatur gültig, Debuggable deaktiviert; keine private Signaturdatei in GitHub
- Android-CI für VersionCode 19 erfolgreich: Run 37940170751. Der endgültige GitHub-Publisher prüft APK-Bytes, Signatur, Manifest, Backend und GitHub-Download erneut.

**Gerätetest:** Noch offen. Beim Update sollte Android die bestehenden App-Daten erhalten, weil Paket und Zertifikat gleich bleiben. Für neue private Importe kann eine einmalige erneute GitHub-Verbindung erforderlich sein.
