package de.rawinstinctai.apkdrop;

/** Explain existing policy decisions; never authorizes installation or bypasses verification. */
final class DropTrustGuidance {
    private DropTrustGuidance() {}
    static String nextStep(InstallPolicy.Result result, boolean stale) {
        if(result==null) return "Prüfung ausstehend · Erneut prüfen. Es wird nichts installiert.";
        if(result.mode==InstallPolicy.Mode.BLOCKED)
            return "Installation gesperrt · "+result.reason+" Bestehende App bleibt unverändert.";
        if(stale) return "Letzter Prüfstand · Vor dem Herunterladen erneut prüfen.";
        if(result.mode==InstallPolicy.Mode.CURRENT)
            return "Aktuell · "+result.reason;
        if(!result.sensitiveAdded.isEmpty())
            return "Neue sensible Berechtigungen · Details vor der Android-Bestätigung ansehen.";
        return result.mode==InstallPolicy.Mode.UPDATE
            ? "Update verfügbar · Identität prüfen, dann Installation in Android bestätigen."
            : "Bereit zum Installieren · APK wird vor der Android-Bestätigung vollständig geprüft.";
    }
    static String recovery(Exception error) {
        if(error instanceof SecurityException)
            return "Sicherheitsprüfung fehlgeschlagen. Keine Installation. App-Identität und Signatur prüfen.";
        if(UpdateFailure.retryable(error))
            return "Prüfung unterbrochen. Gespeicherter Stand bleibt erhalten; bei Verbindung erneut prüfen.";
        return "Prüfung nicht abgeschlossen. Bestehende Installation bleibt unverändert.";
    }
}
