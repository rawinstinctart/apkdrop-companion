package de.rawinstinctai.apkdrop;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.IOException;

final class AppLibraryStore {
    private final SharedPreferences preferences;
    AppLibraryStore(Context context) { preferences=context.getSharedPreferences("apkdrop-library",Context.MODE_PRIVATE); }
    AppLibrary read() throws IOException { return AppLibrary.decode(preferences.getString("apps",null)); }
    void save(AppLibrary library) throws IOException {
        synchronized(ReleaseSnapshotStore.LOCK) {
            if(!preferences.edit().putString("apps",library.encode()).commit()) throw new IOException("App-Liste konnte nicht gespeichert werden.");
        }
    }
    UpdateQueue queue() {
        try { return UpdateQueue.decode(preferences.getString("update-queue",null)); }
        catch(Exception bad) { return new UpdateQueue(java.util.Collections.emptyList()); }
    }
    void queue(UpdateQueue queue) { preferences.edit().putString("update-queue",queue.encode()).apply(); }
    void pendingInstaller(String slug) { preferences.edit().putString("pending-installer",slug).apply(); }
    boolean pendingLaunched() { return preferences.getBoolean("pending-launched",false); }
    void pendingLaunched(boolean value) { preferences.edit().putBoolean("pending-launched",value).apply(); }
    String pendingInstaller() {
        String slug=preferences.getString("pending-installer",null);
        return slug!=null&&slug.matches("[a-z0-9-]{3,40}")?slug:null;
    }
    void clearPendingInstaller() { preferences.edit().remove("pending-installer").remove("pending-launched").apply(); }
}
