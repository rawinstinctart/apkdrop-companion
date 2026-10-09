# APKDrop Companion Alpha 15.2 — Entdecken aufgeräumt

Dieses gezielte Update entfernt die zwei überflüssigen Steuerelemente, die die Entdecken-Ansicht unruhig gemacht haben.

- **„Neueste ▾“ entfernt** – die drei sichtbaren Sammlungen „Frisch aktualisiert“, „Neu entdeckt“ und „Entwickler“ bleiben erhalten.
- **„↻“ entfernt** – der Katalog lädt wie bisher beim ersten Öffnen und über Suche/Kategorien; Fehler bieten „Erneut versuchen“ an.
- **„1 App“ bleibt** als dezente Trefferzahl, die Seitenzahl erscheint weiterhin nur bei mehreren Seiten.
- GitHub Radar, Release Radar, private Imports, App-Daten, Trust- und Installationsprüfungen bleiben unverändert.

## Release-Sicherheit
- Paket: `de.rawinstinctai.apkdrop.debug`, historisch unverändert
- Version: `0.1.0-alpha.15.2-preview`, **VersionCode 20**, Android 8.0+ (minSdk 26)
- Zertifikat SHA-256: `6cf70241a63498e5e9fce78bac9abec760364cf320928347114bf109abd81e2e`
- APK SHA-256: `482f05647140de4462186b74fa700d2639613d50e39be57acc219bb4893c9cf4`
- Mit dem ursprünglichen Schlüssel signierte, nicht-debuggbare Preview (v2 + v3).
- CI: Android-Tests, Lint, Debug- und Preview-Build erfolgreich (Run 37942638987). Der Release-Workflow prüft dieselben signierten Bytes, Signatur, Android-Manifest und Live-Backend erneut. Private Schlüssel werden nie zu GitHub übertragen.

Die vorhandene Installation kann aufgrund unverändertem Paket und Signierer ohne Löschen der App-Daten aktualisiert werden.
