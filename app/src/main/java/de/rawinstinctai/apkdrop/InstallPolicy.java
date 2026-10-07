package de.rawinstinctai.apkdrop;

import java.util.*;

final class InstallPolicy {
    enum Mode { INSTALL, UPDATE, CURRENT, BLOCKED }

    static final Set<String> SENSITIVE=Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            "android.permission.CAMERA","android.permission.RECORD_AUDIO",
            "android.permission.ACCESS_FINE_LOCATION","android.permission.ACCESS_COARSE_LOCATION","android.permission.ACCESS_BACKGROUND_LOCATION",
            "android.permission.READ_CONTACTS","android.permission.WRITE_CONTACTS",
            "android.permission.READ_SMS","android.permission.SEND_SMS","android.permission.RECEIVE_SMS",
            "android.permission.READ_CALL_LOG","android.permission.WRITE_CALL_LOG","android.permission.READ_PHONE_STATE","android.permission.CALL_PHONE",
            "android.permission.BODY_SENSORS","android.permission.BODY_SENSORS_BACKGROUND",
            "android.permission.READ_MEDIA_IMAGES","android.permission.READ_MEDIA_VIDEO","android.permission.READ_MEDIA_AUDIO"
    )));

    static final class Result {
        final Mode mode;
        final String reason;
        final Set<String> addedPermissions, sensitiveAdded;
        Result(Mode mode,String reason,Set<String> added,Set<String> sensitive) {
            this.mode=mode; this.reason=reason; this.addedPermissions=added; this.sensitiveAdded=sensitive;
        }
    }

    private InstallPolicy() {}

    static Result evaluate(InstallContract release,InstalledState current,int sdk,String[] deviceAbis) {
        if(release.minSdk>sdk) return blocked("Diese Version benötigt ein neueres Android.");
        if(!release.abis.isEmpty() && release.abis.stream().noneMatch(a->Arrays.asList(deviceAbis).contains(a)))
            return blocked("Diese APK passt nicht zu deiner CPU-Architektur.");
        if(current!=null && !current.packageName.equals(release.packageName))
            return blocked("Package-ID stimmt nicht überein.");
        if(current!=null && !current.signers.equals(release.signers))
            return blocked("Signierschlüssel stimmt nicht mit der installierten App überein.");
        if(current!=null && release.versionCode<=current.versionCode)
            return new Result(Mode.CURRENT,release.versionCode==current.versionCode
                    ? "Diese Version ist bereits installiert." : "Auf deinem Gerät ist bereits eine neuere Version installiert.",
                    Collections.emptySet(),Collections.emptySet());

        Set<String> added=new LinkedHashSet<>(release.permissions);
        if(current!=null) added.removeAll(current.permissions);
        Set<String> sensitive=new LinkedHashSet<>(added); sensitive.retainAll(SENSITIVE);

        return new Result(current==null?Mode.INSTALL:Mode.UPDATE,
                current==null?"Neue App · Identität geprüft":"Update · Signer-Kontinuität geprüft",
                Collections.unmodifiableSet(added),Collections.unmodifiableSet(sensitive));
    }

    private static Result blocked(String reason) {
        return new Result(Mode.BLOCKED,reason,Collections.emptySet(),Collections.emptySet());
    }
}
