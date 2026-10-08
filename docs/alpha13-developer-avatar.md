# APKDrop Companion Alpha 13 — GitHub-Avatare im Entwicklerprofil

Native DropID-Profile zeigen jetzt das GitHub-Profilbild direkt neben Name und Handle an. Die öffentliche DropID-API liefert `avatarUrl` bereits; keine API-Änderung und kein Cloudflare-Deploy sind erforderlich.

## Verhalten und Sicherheit
- Quadratischer Avatar mit weichen abgerundeten Ecken, dunklem Hintergrund und limettengrünem Monogramm als Fallback.
- Eingehende Avatar-URL muss exakt `https://avatars.githubusercontent.com/u/<verifizierte GitHub-ID>?v=4` sein; Schema, Host, Nutzerinfo, Port, Pfad, Fragment und Query werden geprüft.
- GitHub-ID wird aus dem bereits validierten veröffentlichten DropID-Profil übernommen. Der Avatar ist reine Anzeige und niemals Vertrauens- oder Installationsnachweis.
- Bilder: bestehender Android-Downloader, MIME-Allowlist, 4-MiB-Grenze, sichere Bitmap-Dekodierung, In-Memory-LRU, keine externe Bibliothek. Fehler/Offline: Monogramm.
- Bild-Callbacks bleiben an ihre konkrete View/URL gebunden; keine unpassenden Avatare beim Wechsel zwischen Profilen.
- Android-Preview bleibt `de.rawinstinctai.apkdrop.debug` mit ursprünglichem Zertifikat, nicht debuggbar. `0.1.0-alpha.13-preview`, VersionCode 16 für In-place-Upgrade über Alpha 12.

## Prüfpunkte
- `@rawinstinctart` öffnen: GitHub-Bild sichtbar, Bio und verifizierter Status unverändert.
- Profil ohne Bild, alter `owner`-Datensatz, Offline-Fall: Monogramm ohne leere Fläche.
- Zwischen Entwicklern wechseln und schnell zurück: kein Bildmix.
- Alpha 12 ohne Deinstallation upgraden: gespeicherte Apps, Follow-Liste und DropPilot bleiben erhalten.

Android-CI, originalsignierte APK und physischer Gerätetest sind separate Gates. Kein Schlüssel im Repository.
