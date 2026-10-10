package de.rawinstinctai.apkdrop;

import org.json.JSONArray;
import org.junit.Test;
import static org.junit.Assert.*;

public final class Alpha20HomeContractTest {
    @Test public void unifiedHomeIsPinnedToPublicApkdropOrigin() {
        assertEquals("https://apkdrop.rawinstinctai.de/api/home",StoreClient.apiUri("/api/home").toString());
        for(String path:new String[]{"/api/home?debug=true","/api/home/other","https://evil.test/api/home",
                "/api/companion/home","/api/home#fragment"})
            assertThrows(SecurityException.class,()->StoreClient.apiUri(path));
    }
    @Test public void requestRejectsInvalidPrivateChoicesBeforeAnyNetworkOperation() {
        JSONArray many=new JSONArray();for(int i=0;i<51;i++)many.put("sample-app");
        assertThrows(SecurityException.class,()->StoreClient.home(new JSONArray(),many));
        assertThrows(SecurityException.class,()->StoreClient.home(new JSONArray().put("invalid-name"),new JSONArray()));
        assertThrows(SecurityException.class,()->StoreClient.home(new JSONArray().put("42"),new JSONArray().put("../secret")));
        JSONArray developers=new JSONArray();for(int i=0;i<21;i++)developers.put("42");
        assertThrows(SecurityException.class,()->StoreClient.home(developers,new JSONArray()));
    }
}