package de.rawinstinctai.apkdrop;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;

/** Follows are keyed by immutable GitHub ID and stay in private, backup-disabled storage. */
final class DeveloperFollows {
    private final SharedPreferences prefs;
    DeveloperFollows(Context context) {prefs=context.getSharedPreferences("developer-follows-v1",Context.MODE_PRIVATE);}
    JSONObject read() throws Exception {
        String raw=prefs.getString("follows","{}");
        if(raw.length()>32768)throw new SecurityException("Gespeicherte Entwicklerliste ungültig.");
        JSONObject data=new JSONObject(raw);
        if(data.length()>20)throw new SecurityException("Zu viele gespeicherte Entwickler.");
        for(java.util.Iterator<String> it=data.keys();it.hasNext();) {
            String id=it.next();if(!validId(id))throw new SecurityException("Ungültige Entwickler-ID.");
            JSONObject item=data.getJSONObject(id);StoreClient.handle(item.getString("handle"));
            if(item.getString("name").length()>160)throw new SecurityException("Ungültiger Entwicklername.");
        }return data;
    }
    static boolean validId(String id) {return id!=null && id.matches("(?:[1-9][0-9]{0,19}|owner)");}
    boolean contains(String id) throws Exception {return read().has(id);}
    void toggle(String id,String handle,String name) throws Exception {
        if(!validId(id) || name==null || name.length()>160)throw new SecurityException("Ungültige Entwicklerdaten.");
        StoreClient.handle(handle);JSONObject data=read();
        if(data.has(id))data.remove(id);
        else {if(data.length()>=20)throw new IllegalStateException("Du folgst bereits 20 Entwicklern.");data.put(id,new JSONObject().put("handle",handle).put("name",name));}
        if(!prefs.edit().putString("follows",data.toString()).commit())throw new java.io.IOException("Folgen konnte nicht gespeichert werden.");
    }
    void remove(String id) throws Exception {
        JSONObject data=read();data.remove(id);
        if(!prefs.edit().putString("follows",data.toString()).commit())throw new java.io.IOException("Entfernen fehlgeschlagen.");
    }
    JSONArray ids() throws Exception {JSONObject data=read();JSONArray ids=new JSONArray();data.keys().forEachRemaining(ids::put);return ids;}
}
