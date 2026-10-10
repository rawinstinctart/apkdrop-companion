package de.rawinstinctai.apkdrop;

import org.json.JSONArray;
import org.junit.Test;
import static org.junit.Assert.*;

public final class Alpha21BookmarkSyncTest {
    @Test public void cloudBookmarksCannotBecomeTrustedInstalledApps() throws Exception {
        JSONArray local=new JSONArray().put("sample-app"),remote=new JSONArray().put("sample-app").put("another-app");
        JSONArray merged=CloudWatchlist.merge(local,remote);
        assertEquals(2,merged.length());
        assertEquals("sample-app",merged.getString(0));
        assertEquals("another-app",merged.getString(1));
        assertEquals(0,new AppLibrary().entries().size());
    }
    @Test public void invalidOrOversizedCloudDataIsRejected() throws Exception {
        for(String bad:new String[]{"../private","ABC","api/x","invalid\nslug"}){
            try{CloudWatchlist.merge(new JSONArray().put(bad));fail("accepted "+bad);}
            catch(SecurityException expected){}
        }
        JSONArray tooMany=new JSONArray();
        for(int i=0;i<51;i++)tooMany.put("app-"+i);
        try{CloudWatchlist.merge(tooMany);fail("oversized");}
        catch(SecurityException expected){}
    }
}
