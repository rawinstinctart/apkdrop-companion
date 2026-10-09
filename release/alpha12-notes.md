# APKDrop Companion Alpha 12 Preview — Premium Experience

**Version:** `0.1.0-alpha.12-preview` · **versionCode:** `15` · **Android:** 8.0+

Diese Alpha ist ein Upgrade der bestehenden Companion-Preview. Sie verwendet dieselbe Paket-ID `de.rawinstinctai.apkdrop.debug` und exakt dasselbe ursprüngliche Signaturzertifikat wie Alpha 11. Die APK ist **nicht debuggbar**; der historische Paket-Suffix `.debug` bleibt bewusst für In-place-Upgrades erhalten.

## Neu in Alpha 12
- **App-Details:** Version, Status und Hauptaktion direkt sichtbar; Vergleich, Historie, Berechtigungen und Vertrauensnachweise bei Bedarf aufklappbar.
- **Release Notes:** native Darstellung von Überschriften, Listen, Betonungen und Code mit Mehr/Weniger. Unvertrauenswürdiges HTML und externe Bilder werden nicht ausgeführt.
- **DropPilot:** echte Prüflauf-Historie, Fortschritt und Bedingungen wie Verbindung/Ladestand; unterbrochene Jobs werden nicht irrtümlich als erfolgreich angezeigt.
- **Sauberer App-Wechsel:** geöffnete Nachweise und Trust Center schließen bei jeder neuen Prüfung, auch wenn die nächste Abfrage fehlschlägt.

## Herkunft und Sicherheitsprüfung
- Getestete Android-Quelle: `8cec93c8acfb5cc26cc74fa2c22794ebec48b411`, Release-Quellstand: `6ba9880cfe1a7dedc71856194afa182b56a8cd32` (keine Android-Quelländerungen).
- GitHub Actions: 35 Release-Gate-Tests PASS; Android Debug + Preview Builds, Tests, Lint und Metadatenprüfung erfolgreich.
- Originalsignierung erfolgte außerhalb GitHubs. Auf GitHub liegt nur ein öffentlicher komprimierter Binärpatch, **kein privater Schlüssel und kein Passwort**. Der Release-Workflow rekonstruiert ausschließlich die exakt geprüfte APK und überprüft Signatur, Paket, Version, Hash und den tatsächlich herunterladbaren Asset.
- **Signatur-Zertifikat SHA-256:** `6cf70241a63498e5e9fce78bac9abec760364cf320928347114bf109abd81e2e`
- **APK SHA-256:** `2673a83cd65a1badb3e9f6a12a7720122c0a8879155863d7ba9ef9e1993ba504`
- **APK-Größe:** 296200 Bytes.
