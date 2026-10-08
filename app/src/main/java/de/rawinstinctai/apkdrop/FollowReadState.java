package de.rawinstinctai.apkdrop;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Explicitly seen feed items, stored only on this device. Does not imply install/verification. */
final class FollowReadState {
    private static final int MAX=600;
    private final SharedPreferences prefs;
    FollowReadState(Context context) {prefs=context.getSharedPreferences("following-read-v1",Context.MODE_PRIVATE);}
    static String key(JSONObject release) throws Exception {
        String slug=StoreClient.slug(release.getString("slug"));
        String value=slug+"\n"+release.optString("channel")+"\n"+release.optString("version")+"\n"+release.optString("publishedAt")+"\n"+release.optString("sha256");
        if(value.length()>2048) throw new SecurityException("Ungültiger Release-Eintrag.");
        byte[] hash=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder out=new StringBuilder();for(byte b:hash)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();
    }
    boolean unseen(JSONObject release) {try {return !prefs.contains(key(release));}catch(Exception bad){return false;}}
    void mark(JSONArray releases) throws Exception {
        Set<String> keys=new LinkedHashSet<>();
        for(int i=0;i<releases.length();i++) keys.add(key(releases.getJSONObject(i)));
        Map<String,?> old=prefs.getAll();List<String> prior=new ArrayList<>(old.keySet());
        prior.removeAll(keys);prior.sort(Comparator.comparingLong(k->old.get(k) instanceof Long?(Long)old.get(k):0L));
        SharedPreferences.Editor edit=prefs.edit();
        while(prior.size()+keys.size()>MAX && !prior.isEmpty()) edit.remove(prior.remove(0));
        if(keys.size()>MAX) throw new SecurityException("Release-Liste zu groß.");
        long now=System.currentTimeMillis();for(String key:keys)edit.putLong(key,now);
        if(!edit.commit()) throw new java.io.IOException("Lesestatus konnte nicht gespeichert werden.");
    }
}
