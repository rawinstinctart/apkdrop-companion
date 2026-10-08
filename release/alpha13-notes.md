# APKDrop Companion v0.1.0-alpha.13-preview — GitHub-Avatare

Alpha 13 macht das Entwicklerprofil persönlicher: Die Companion-App zeigt das GitHub-Profilbild direkt neben dem DropID-Namen an.

**Neu**
- GitHub-Profilbild aus dem bereits öffentlichen DropID-Datensatz, keine neue Kontoverknüpfung.
- Abgerundeter dunkler Avatar im APKDrop-Stil, mit limettengrünem Anfangsbuchstaben als Fallback.
- Exakte Prüfung auf `avatars.githubusercontent.com/u/<dieser GitHub-ID>` und erlaubte HTTPS-Parameter.
- Vorhandener begrenzter Bitmap-Downloader und Cache; kein Tracker, keine neue SDK-Abhängigkeit.
- Entwickler-Bio, Verifizierungsstatus, Follows und übriger Store bleiben unverändert.

**Technische Identität**
- Paket: `de.rawinstinctai.apkdrop.debug` (historischer Paketname für nahtlose Updates)
- Version: `0.1.0-alpha.13-preview`, versionCode `16`, nicht debuggbar
- Ursprüngliches Signaturzertifikat SHA-256: `6cf70241a63498e5e9fce78bac9abec760364cf320928347114bf109abd81e2e`
- Veröffentlichtes APK SHA-256: `2794815ae0b58180e9cedc53cd0f450d1c2946c371b40df67797a9c3447789d9`, 300495 Bytes
- Android-Build geprüft mit GitHub Actions Run `37850625603`, Source `74954e3f23dbe73fdf6a3e0cac6e093f6c21f4cd`.
- Die Originalsignierung fand außerhalb von GitHub statt; nur ein öffentlicher Binärpatch wird für die byte-identische Rekonstruktion veröffentlicht. Kein privater Signierschlüssel in GitHub.

**Geräteabnahme bleibt offen:** Über Alpha 12 installieren (nicht deinstallieren), DropID `@rawinstinctart` öffnen und Avatar, Offline-Fallback, gespeicherte Apps und Follows auf dem Smartphone prüfen. Dies ist ein Preview-Release.
