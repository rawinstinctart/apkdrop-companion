package de.rawinstinctai.apkdrop;

import android.content.Context;
import org.json.*;
import java.util.*;

/** Display comparison only. Never authorizes a download or installation. */
final class ReleaseRadar {
    enum State { UNKNOWN, NOT_INSTALLED, UPDATE, INSTALLED, DIFFERENT_SIGNER }
    static State compare(JSONObject release,InstalledState installed) {
        String pkg=release.optString("packageName","");long version=release.optLong("versionCode",0);
        JSONArray values=release.optJSONArray("signers");Set<String> signers=new LinkedHashSet<>();
        if(!pkg.matches("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+")||version<=0||values==null||values.length()==0||values.length()>10)return State.UNKNOWN;
        for(int i=0;i<values.length();i++){String s=values.optString(i);if(!s.matches("[a-fA-F0-9]{64}"))return State.UNKNOWN;signers.add(s.toLowerCase(Locale.ROOT));}
        if(installed==null)return State.NOT_INSTALLED;
        if(!pkg.equals(installed.packageName)||!signers.equals(installed.signers))return State.DIFFERENT_SIGNER;
        return installed.versionCode>=version?State.INSTALLED:State.UPDATE;
    }
    static State state(Context context,JSONObject release) {
        String pkg=release.optString("packageName","");if(!pkg.matches("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+"))return State.UNKNOWN;
        try{return compare(release,InstalledState.read(context,pkg));}catch(Exception unavailable){return State.UNKNOWN;}
    }
    static String label(State state,boolean unseen){
        return switch(state){case INSTALLED->"Bereits installiert";case UPDATE->"Neuere Version verfügbar";case DIFFERENT_SIGNER->"Installiert · Signatur in Details prüfen";case NOT_INSTALLED->unseen?"Neu für dich":"Bereits angesehen";case UNKNOWN->unseen?"Neu für dich · Installationsstand in Details":"Bereits angesehen · Installationsstand in Details";};
    }
}
