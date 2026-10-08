package de.rawinstinctai.apkdrop;

/** Text is derived from the actual verification stage; no security score or malware verdict. */
final class TrustSummary {
    static String describe(InstallContract release,InstallPolicy.Result decision,boolean verified) {
        if(release==null || decision==null) return "Noch kein Release geprüft. Lokale APK-Prüfung ausstehend.";
        String permissions=decision.sensitiveAdded.isEmpty()?"Keine zusätzlich erkannten sensiblen Berechtigungen."
                :decision.sensitiveAdded.size()+" sensible Berechtigung(en) "+(decision.mode==InstallPolicy.Mode.INSTALL?"in dieser App":"neu gegenüber der installierten Version")+". Details unten ansehen.";
        String identity=verified?"✓ Heruntergeladene APK lokal geprüft: Hash, Signatur, Paket und Gerätekompatibilität stimmen mit dem Release-Vertrag überein."
                :"Release-Vertrag geladen. Die tatsächliche APK wird erst nach dem Download lokal geprüft.";
        return identity+"\n\n"+permissions+(decision.mode==InstallPolicy.Mode.BLOCKED?"\nInstallation blockiert: "+decision.reason:"")
                +"\n\nAndroid bestätigt die Installation. Diese Prüfungen sind kein Malware-Scan.";
    }
}
