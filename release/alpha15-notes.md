Alpha 15 — alle sieben Verbesserungen in einem Update

- **Für dich entdeckt:** Home bündelt verfügbare Updates, eigene GitHub-APK-Vorschläge und neue Releases.
- **Nativer GitHub-Import:** Vorschlag in der App öffnen, aktuelle APK-Auswahl ansehen und den privaten Import bestätigen. Fortschritt und eigene Apps bleiben im Radar sichtbar.
- **Release Radar:** Zeitlich sortierte Releases mit App-Icon, Version, Datum, Änderungsnotizen, Entwickler und lokalem Gelesen-Status.
- **DropTrust:** Verständliche Angaben zur geprüften APK-Signatur, zum Vergleich mit der installierten App und zur lokalen Dateiprüfung vor der Installation.
- **Installierte Apps erkennen:** Erst nach deiner Zustimmung wird der öffentliche Katalog lokal mit Paket und Signatur abgeglichen. Passende Updatequellen bestätigst du einzeln. Die Liste deiner installierten Apps wird nicht hochgeladen.
- **Entdecken:** Echte Sammlungen für frisch aktualisierte Apps, neue Veröffentlichungen und Entwickler.
- **Aufgeräumte Oberfläche:** Doppelte Links entfernt, Suchsteuerung passend zur Ansicht, korrigierte Versionsanzeige, Singular bei einem APK-Vorschlag und verständlicher letzter Prüfstand.

Für den privaten Import eine bestehende GitHub-Verbindung einmal trennen und erneut verbinden. Im Browser wird die neue Berechtigung ausdrücklich angezeigt. Ein Import erstellt einen privaten Entwurf; öffentliche Veröffentlichung bestätigst du weiterhin separat im Browser.

## Prüfung

150 Android-Tests erfolgreich; Debug und Preview in GitHub CI geprüft, Lint und Builds erfolgreich. 37 Release-Prüfungen erfolgreich. 138 Backend-Tests lokal und in Cloudflare Builds erfolgreich. Datenbankmigration und produktives Deployment abgeschlossen; Live-Check für Katalog, Geräteautorisierung und private Import-Endpunkte erfolgreich.

Die APK stammt aus dem erfolgreich geprüften GitHub-CI-Build und wurde mit dem ursprünglichen Schlüssel signiert. Der Schlüssel bleibt außerhalb von GitHub. Originalsignierte, nicht-debuggbare Preview; Paket `de.rawinstinctai.apkdrop.debug`, VersionCode 18, Version `0.1.0-alpha.15-preview`, Android 8 oder neuer. Ein Update über Alpha 14 erhält die App-Daten.

SHA-256: `a0bb5148b0fb79bddcbf4592f5d5e27caf19d619a4f011a8968446433eb54286`
