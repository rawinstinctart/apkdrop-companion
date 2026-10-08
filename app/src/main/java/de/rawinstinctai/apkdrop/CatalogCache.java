package de.rawinstinctai.apkdrop;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.util.*;

/** Bounded public display metadata only. Never read by ContractClient or the installer. */
final class CatalogCache {
    static final long MAX_AGE=7L*24*60*60*1000;
    static final int MAX_ENTRY=128*1024,MAX_ENTRIES=4;
    private final SharedPreferences prefs;
    CatalogCache(Context context) {prefs=context.getSharedPreferences("discover-cache-v1",Context.MODE_PRIVATE);}
    static final class Entry {
        final JSONObject data; final long at;
        Entry(JSONObject data,long at) {this.data=data;this.at=at;}
    }
    static void validate(JSONObject data) throws Exception {
        JSONArray apps=data.getJSONArray("apps");
        if(!"apkdrop.discover.v1".equals(data.optString("schema")) || apps.length()>24
                || data.optInt("page",1)<1 || data.optInt("pages",1)<1
                || data.optInt("page",1)>data.optInt("pages",1) || data.optInt("pages",1)>100000
                || data.optInt("total",0)<0) throw new SecurityException("Ungültiger App-Katalog.");
        for(int i=0;i<apps.length();i++) StoreClient.slug(apps.getJSONObject(i).getString("slug"));
    }
    static Entry decode(String raw,String path,long now) throws Exception {
        if(raw==null || raw.length()>MAX_ENTRY) return null;
        JSONObject envelope=new JSONObject(raw);
        long at=envelope.getLong("at");
        if(!path.equals(envelope.getString("path")) || at<=0 || at>now || now-at>MAX_AGE) return null;
        JSONObject data=envelope.getJSONObject("data");validate(data);return new Entry(data,at);
    }
    synchronized Entry read(String path) {
        try {return decode(prefs.getString(path,null),path,System.currentTimeMillis());}
        catch(Exception corrupt) {prefs.edit().remove(path).apply();return null;}
    }
    synchronized void remove(String path) {prefs.edit().remove(path).apply();}
    synchronized void save(String path,JSONObject data) throws Exception {
        validate(data);
        String raw=new JSONObject().put("path",path).put("at",System.currentTimeMillis()).put("data",data).toString();
        if(raw.length()>MAX_ENTRY) return;
        SharedPreferences.Editor edit=prefs.edit();
        List<String> keys=new ArrayList<>(prefs.getAll().keySet());keys.remove(path);
        keys.sort(Comparator.comparingLong(key->{try{return new JSONObject(prefs.getString(key,"{}")).optLong("at");}catch(Exception bad){return 0L;}}));
        while(keys.size()>=MAX_ENTRIES) edit.remove(keys.remove(0));
        edit.putString(path,raw).apply();
    }
}
