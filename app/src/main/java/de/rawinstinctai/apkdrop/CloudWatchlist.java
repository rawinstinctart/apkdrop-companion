package de.rawinstinctai.apkdrop;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import java.io.IOException;
import java.util.LinkedHashSet;

/** Local, display-only cross-device bookmarks; never creates trusted AppLibrary identities. */
final class CloudWatchlist {
    private final SharedPreferences prefs;
    private static final int MAX=50;
    CloudWatchlist(Context context){
        prefs=context.getSharedPreferences("apkdrop-cloud-bookmarks-v1",Context.MODE_PRIVATE);
    }
    static JSONArray merge(JSONArray... lists) throws Exception {
        LinkedHashSet<String> slugs=new LinkedHashSet<>();
        for(JSONArray list:lists){
            if(list==null)continue;
            if(list.length()>MAX)throw new SecurityException("Zu viele Merkliste-Einträge.");
            for(int i=0;i<list.length();i++){
                String slug=StoreClient.slug(list.getString(i));
                slugs.add(slug);
                if(slugs.size()>MAX)throw new SecurityException("Merkliste ist voll (maximal 50 Apps).");
            }
        }
        JSONArray result=new JSONArray();
        for(String slug:slugs)result.put(slug);
        return result;
    }
    JSONArray read() throws Exception {
        String raw=prefs.getString("slugs","[]");
        if(raw.length()>2500)throw new SecurityException("Merkliste ist beschädigt.");
        return merge(new JSONArray(raw));
    }
    void replace(JSONArray list) throws Exception {
        JSONArray checked=merge(list);
        if(!prefs.edit().putString("slugs",checked.toString()).commit())
            throw new IOException("Merkliste konnte nicht gespeichert werden.");
    }
}
