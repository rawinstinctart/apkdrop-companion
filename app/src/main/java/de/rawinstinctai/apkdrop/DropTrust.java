package de.rawinstinctai.apkdrop;

import org.json.JSONObject;

/** Explain evidence and unknowns. Never a safety score or installer authorization. */
final class DropTrust {
    static String catalog(JSONObject release,ReleaseRadar.State state){
        if(state==ReleaseRadar.State.DIFFERENT_SIGNER)return "DropTrust · Abweichende Signatur – Details prüfen";
        JSONObject evidence=release.optJSONObject("trust");
        if(evidence!=null&&"apkdrop.trust.v1".equals(evidence.optString("schema"))){
            if(!"documented".equals(evidence.optString("status")))return "DropTrust · Sicherheitsnachweise unvollständig";
            if(evidence.optBoolean("reviewRecommended")){
                if("changed".equals(evidence.optString("continuity")))return "DropTrust · Signatur seit dem letzten Release geändert";
                return "DropTrust · Neue sensible Berechtigungen prüfen";
            }
            if(state!=ReleaseRadar.State.INSTALLED&&state!=ReleaseRadar.State.UPDATE)
                return "DropTrust · Release geprüft · Geräteprüfung vor Installation";
        }
        if(!release.optBoolean("signatureVerified"))return "DropTrust · Signaturstatus noch offen";
        if(state==ReleaseRadar.State.INSTALLED||state==ReleaseRadar.State.UPDATE)return "DropTrust · Signatur passt zur installierten App";
        return "DropTrust · APK-Signatur beim Import geprüft";
    }
    static String describe(InstallContract release,InstalledState installed,InstallContract previous,boolean verified){
        if(release==null)return "DropTrust · Prüfung ausstehend";
        String source="Quelle: APKDrop-Release-Vertrag geladen";
        String signer=installed!=null?release.signers.equals(installed.signers)?"Signatur passt zur installierten App":"Abweichende Signatur – Installation blockiert":previous!=null?release.signers.equals(previous.signers)?"Signatur wie beim zuvor beobachteten Release":"Neue Signatur – bitte prüfen":"Erste Signatur · noch kein Vergleichsrelease";
        return "DropTrust\n"+source+"\n"+signer+"\n"+(verified?"Datei, Hash und APK-Signatur lokal bestätigt":"Lokale Datei- und Signaturprüfung nach dem Download")+"\nDiese Nachweise sind kein Malware-Scan.";
    }
    private DropTrust(){}
}
