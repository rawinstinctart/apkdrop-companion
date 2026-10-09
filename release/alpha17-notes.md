# APKDrop Companion Alpha 17 — Onboarding und Discovery

- **GitHub verbinden:** verständliche Erklärung vor dem Browser-Schritt. Private Imports sind eine eigene, standardmäßig nicht ausgewählte Freigabe. Neue Repositories erscheinen entsprechend deiner GitHub-App-Auswahl.
- **Freigabe abbrechen:** Beim Erweitern einer bestehenden Verbindung bleiben die bisherigen Zugangsdaten verschlüsselt erhalten. Abbruch oder Ablauf stellt die alte Verbindung wieder her, auch nach einem App-Neustart.
- **Privat hinzufügen:** Frische Vorschau mit Version, APK-Datei, Größe und Repository-Sichtbarkeit. Jeder Import braucht eine Bestätigung und bleibt ein privater Entwurf. Veröffentlichung und Installation sind separate Entscheidungen.
- **App-Details:** aufklappbare Beschreibung mit Entwickler-Zuordnung, verständlicher Zustand bei fehlender Beschreibung. Versionscode und Signaturnachweise bleiben getrennt von der Anzeige-Version sichtbar.
- **Entdecken und DropID:** hilfreiche leere öffentliche Kataloge und Entwicklerprofile; Profilzahlen beziehen sich auf die aktuelle App-Seite. Kategorien bleiben kontrolliert.
- **GitHub-Links:** Auch Repository-Links öffnen über die bestätigte öffentliche DropID-Zuordnung den aktuellen APKDrop-App-Standard. Private, abgelaufene und mehrdeutige Zuordnungen werden abgewiesen.

## Verifikation

- Paket `de.rawinstinctai.apkdrop.debug`, Version `0.1.0-alpha.17-preview`, VersionCode **23**, Android 8.0+.
- Nicht debuggable; originale v2/v3-Signatur. Zertifikat SHA-256: `6cf70241a63498e5e9fce78bac9abec760364cf320928347114bf109abd81e2e`.
- **177 Android-Tests je Variante**, **39 Release-Prüfungen**, Lint: **0 Fehler / 111 Warnungen**.
- Geprüfter Quellstand: `9eb39210defb8b27ff5b5f7f04e9e8d689ff19dd`, Android-CI `37963866576`.
- APK: **333263 Bytes**, SHA-256 `5424c8092a3ee365efd46559ecf9289db67921832a03cbdb849c03923bd4926d`.
- GitHub rekonstruiert und prüft die exakt lokal signierten Bytes unabhängig, prüft die Live-Backend-Grenzen und lädt den veröffentlichten Download erneut zur Hash-Prüfung herunter. Der private Keystore bleibt lokal.
- Native 320dp-/Großschrift-Vorschauen geprüft. Abbruch, Ablauf, Lesezugriff, Importbestätigung und Repository-Share sind durch Android-Tests abgedeckt.

**Noch offen:** Upgrade/Installation und visuelle Abnahme auf echten Android-Geräten sowie ein Durchlauf mit deinem realen GitHub-Konto, neu autorisierten Repositories und einem privaten Import. Fixture-Tests ersetzen diese Abnahme nicht. Alpha 18 behandelt Zuverlässigkeit und Updates; das 1.0-Gate bleibt in #33.
