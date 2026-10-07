package de.rawinstinctai.apkdrop;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.IOException;

final class AppLibraryStore {
    private final SharedPreferences preferences;
    AppLibraryStore(Context context) { preferences=context.getSharedPreferences("apkdrop-library",Context.MODE_PRIVATE); }
    AppLibrary read() throws IOException { return AppLibrary.decode(preferences.getString("apps",null)); }
    void save(AppLibrary library) throws IOException { preferences.edit().putString("apps",library.encode()).apply(); }
    void pendingInstaller(String slug) { preferences.edit().putString("pending-installer",slug).apply(); }
    String pendingInstaller() {
        String slug=preferences.getString("pending-installer",null);
        return slug!=null&&slug.matches("[a-z0-9-]{3,40}")?slug:null;
    }
    void clearPendingInstaller() { preferences.edit().remove("pending-installer").apply(); }
}
