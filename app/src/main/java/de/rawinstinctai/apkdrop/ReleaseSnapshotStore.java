package de.rawinstinctai.apkdrop;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;

final class ReleaseSnapshotStore {
    // App, activity and JobService use one process and one lock. No stale job can resurrect a removed pin.
    static final Object LOCK=new Object();
    private final Context context;
    private final SharedPreferences prefs;
    ReleaseSnapshotStore(Context context) {
        this.context=context.getApplicationContext();
        prefs=context.getSharedPreferences("apkdrop-release-snapshots",Context.MODE_PRIVATE);
    }
    ReleaseSnapshot read(AppLibrary.Entry entry) throws Exception {
        synchronized(LOCK) { return ReleaseSnapshot.decode(prefs.getString(entry.slug,null),entry,System.currentTimeMillis()); }
    }
    void success(AppLibrary.Entry entry,InstallContract release,long at) throws Exception {
        synchronized(LOCK) {
            if(!stillTracked(entry)) return;
            entry.requireIdentity(release.slug,release.packageName,release.signers);
            ReleaseSnapshot old;
            try { old=read(entry); } catch(Exception bad) { old=null; }
            if(old!=null && old.checkedAt>at) return;
            ReleaseSnapshot next=(old==null?new ReleaseSnapshot(null,null,at,null,false):old).success(release,at);
            if(!prefs.edit().putString(entry.slug,next.encode()).commit()) throw new java.io.IOException("Prüfstand konnte nicht gespeichert werden.");
        }
    }
    void failure(AppLibrary.Entry entry,String error,boolean blocked,long at) throws Exception {
        synchronized(LOCK) {
            if(!stillTracked(entry)) return;
            ReleaseSnapshot old;
            try { old=read(entry); } catch(Exception bad) { old=null; }
            if(old!=null && old.checkedAt>at) return;
            String value=error==null?"Prüfung fehlgeschlagen":error.substring(0,Math.min(500,error.length()));
            ReleaseSnapshot next=(old==null?new ReleaseSnapshot(null,null,at,null,false):old).failed(value,blocked,at);
            if(!prefs.edit().putString(entry.slug,next.encode()).commit()) throw new java.io.IOException("Prüfstand konnte nicht gespeichert werden.");
        }
    }
    void prune(AppLibrary library) {
        synchronized(LOCK) {
            SharedPreferences.Editor edit=prefs.edit();
            for(String slug:prefs.getAll().keySet()) if(library.find(slug)==null) edit.remove(slug);
            edit.apply();
        }
    }
    private boolean stillTracked(AppLibrary.Entry entry) throws Exception {
        AppLibrary.Entry current=new AppLibraryStore(context).read().find(entry.slug);
        if(current==null) return false;
        current.requireIdentity(entry.slug,entry.packageName,entry.signers); return true;
    }
}
