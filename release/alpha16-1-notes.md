# APKDrop Companion Alpha 16.1 — Professional Experience

Die UI-Runde aus Alpha 16 ist jetzt gemeinsam in einer neuen installierbaren Preview enthalten.

- **Entdecken:** Sammlungschips statt zusätzlicher Sortierung und Refresh-Schaltfläche; kein „Seite 1 / 1“ bei einer einzelnen Seite. Klarere App- und Entwicklerkarten, hilfreiche leere Ergebnisse.
- **Home:** ein klarer erster Schritt, keine leeren Zähler oder doppelten Update-Aktionen. Lange App-Namen und die Startaktion bekommen eigenen Platz.
- **Meine Apps:** verständliche Statusinformationen und benannte App-Menüs. „Suche zurücksetzen“ stellt die gespeicherten Apps wieder her.
- **Updates:** „Update ansehen & prüfen“ führt direkt zur richtigen Prüfung. Ohne offene Updates geht es zurück zu den gespeicherten Apps.
- **Details und Einstellungen:** einheitliche Aktionsabstände, mitwachsende Schaltflächen und kopierbare Trust-Nachweise.
- **Barrierefreiheit:** ausgewählte Navigation für TalkBack, ausreichend Platz für Navigationstexte und Icons bei großer Systemschrift, 48dp Touch-Ziele.

## Verifikation

- Paket `de.rawinstinctai.apkdrop.debug`, Version `0.1.0-alpha.16.1-preview`, VersionCode **22**, Android 8.0+.
- Nicht debuggable; originale Signatur mit v2/v3. Zertifikat SHA-256: `6cf70241a63498e5e9fce78bac9abec760364cf320928347114bf109abd81e2e`.
- **164 Android-Tests je Buildvariante** und **38 Release-Prüfungen** bestanden. Lint: 0 Fehler, 107 Warnungen; Details sind im CI-Bericht enthalten.
- Geprüfter Quellstand: `d303e5959631a312cc3189190f7cc786f1d01820`, Android-CI `37957655229`.
- APK SHA-256: `c89676f8416c8e49d4b7c623de5007d853c70daaea9e6db80f7138999549c9a8`.
- GitHub prüft unabhängig Version, Signatur, Berechtigungen und die exakten signierten Bytes; der Download wird erneut heruntergeladen und gehasht. Der private Keystore bleibt lokal.
- Live-Backend: öffentlicher Katalog, autorisierte private Imports und Pairing-Grenzen geprüft.

Installations- und Signaturregeln sowie private Importgrenzen bleiben erhalten. Für private Imports muss eine ältere GitHub-Verbindung einmal neu verbunden werden.

**Noch offen:** Installation und visuelle Abnahme auf echten Geräten. Die 320dp-/Großschrift-Ansichten wurden mit Android/Robolectric gerendert; das ersetzt keinen Gerätetest. Alpha 17 (Onboarding und Katalog), Alpha 18 (Zuverlässigkeit) und das 1.0-Freigabegate bleiben eigene Arbeitspakete in #31–#33.
