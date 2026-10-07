package de.rawinstinctai.apkdrop;

import java.util.*;

/** Explanations from declared facts, never a security score or APK verification claim. */
final class ReleaseIntelligence {
    private ReleaseIntelligence() {}

    static int priority(InstallPolicy.Result decision,String error,boolean blocked) {
        if(error!=null || blocked) return 2;
        if(decision==null) return 4;
        return switch(decision.mode) {
            case UPDATE -> decision.sensitiveAdded.isEmpty()?1:0;
            case BLOCKED -> 2;
            case INSTALL -> 3;
            case CURRENT -> 5;
        };
    }

    static String summary(InstallContract release,InstalledState installed,InstallPolicy.Result decision) {
        if(decision.mode==InstallPolicy.Mode.BLOCKED) return "Blockiert: "+decision.reason;
        if(decision.mode==InstallPolicy.Mode.CURRENT) return decision.reason;
        if(installed==null) return "Neue App. "+release.permissions.size()+" deklarierte Berechtigungen. Die APK wird vor der Installation lokal geprüft.";
        String result=decision.sensitiveAdded.isEmpty()?"Update ohne neue sensible Berechtigungen.":
                "Aufmerksamkeit: neu "+names(decision.sensitiveAdded)+".";
        result+=" Gleicher Signierer laut Release-Vertrag.";
        if(decision.addedPermissions.isEmpty()) result+=" Keine neuen Berechtigungen.";
        if(installed.minSdk>0 && installed.minSdk==release.minSdk) result+=" Android-Mindestanforderung unverändert.";
        return result;
    }

    static String radar(InstallContract release,InstalledState installed,InstallContract previous) {
        StringBuilder out=new StringBuilder("Release Radar\n");
        if(installed!=null) {
            out.append("Installiert: ").append(installed.versionName==null?"Build "+installed.versionCode:installed.versionName)
                    .append(" → ").append(release.version).append("\n");
            out.append(installed.signers.equals(release.signers)?"✓ Gleicher Signierer laut Vertrag":"⚠ Signierer weicht ab").append("\n");
            Set<String> added=new TreeSet<>(release.permissions); added.removeAll(installed.permissions);
            Set<String> removed=new TreeSet<>(installed.permissions); removed.removeAll(release.permissions);
            Set<String> sensitive=new TreeSet<>(added); sensitive.retainAll(InstallPolicy.SENSITIVE);
            out.append(sensitive.isEmpty()?"✓ Keine neuen sensiblen Berechtigungen":"⚠ Neu: "+names(sensitive)).append("\n");
            out.append("Berechtigungen: +").append(added.size()).append(" / −").append(removed.size()).append("\n");
            sdk(out,"minSdk",installed.minSdk,release.minSdk);
            sdk(out,"targetSdk",installed.targetSdk,release.targetSdk);
        } else out.append("Noch nicht installiert. Kein Vergleich mit einer installierten Version.\n");
        if(previous!=null && previous.versionCode<release.versionCode && previous.packageName.equals(release.packageName)
                && previous.signers.equals(release.signers)) {
            out.append("\nGegenüber zuletzt geprüftem Release ").append(previous.version).append(":\n");
            sdk(out,"minSdk",previous.minSdk,release.minSdk); sdk(out,"targetSdk",previous.targetSdk,release.targetSdk);
            if(previous.size>0) {
                double change=100.0*(release.size-previous.size)/previous.size;
                out.append(String.format(Locale.GERMANY,"APK-Größe: %+.1f %%\n",change));
            }
            if(!new HashSet<>(previous.abis).equals(new HashSet<>(release.abis)))
                out.append("Architekturen: ").append(abis(previous.abis)).append(" → ").append(abis(release.abis)).append("\n");
        }
        out.append("\nRelease-Angaben. Hash, Signatur und Identität der APK werden nach dem Download lokal verifiziert.");
        return out.toString();
    }

    private static void sdk(StringBuilder out,String label,int before,int after) {
        if(before>0 && after>0) out.append(label).append(": ").append(before==after?"unverändert ("+after+")":before+" → "+after).append("\n");
    }
    private static String abis(List<String> values) { return values.isEmpty()?"keine nativen Bibliotheken":String.join(", ",values); }
    static String human(String permission) {
        return switch(permission) {
            case "android.permission.RECORD_AUDIO" -> "Mikrofonzugriff (Audio aufnehmen)";
            case "android.permission.CAMERA" -> "Kamerazugriff";
            case "android.permission.ACCESS_FINE_LOCATION" -> "genauer Standort";
            case "android.permission.ACCESS_COARSE_LOCATION" -> "ungefährer Standort";
            case "android.permission.ACCESS_BACKGROUND_LOCATION" -> "Standort im Hintergrund";
            case "android.permission.READ_CONTACTS" -> "Kontakte lesen";
            default -> permission.substring(permission.lastIndexOf('.')+1).replace('_',' ');
        };
    }
    private static String names(Set<String> permissions) {
        List<String> out=new ArrayList<>(); for(String p:new TreeSet<>(permissions)) out.add(human(p));
        return String.join(", ",out);
    }
}
