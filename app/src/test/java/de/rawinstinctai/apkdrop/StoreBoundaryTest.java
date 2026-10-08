package de.rawinstinctai.apkdrop;

import org.junit.Test;
import static org.junit.Assert.*;

public final class StoreBoundaryTest {
    @Test public void imagesCannotEscapeApkdropOrFetchArbitraryResources() {
        assertEquals("apkdrop.rawinstinctai.de",StoreClient.imageUri("/sample-app/icon?release=r1").getHost());
        assertEquals("/api/sample-app/screenshot/0",StoreClient.imageUri("/api/sample-app/screenshot/0").getPath());
        for(String value:new String[]{"https://evil.test/sample-app/icon","https://apkdrop.rawinstinctai.de.evil.test/sample-app/icon",
                "https://user@apkdrop.rawinstinctai.de/sample-app/icon","http://apkdrop.rawinstinctai.de/sample-app/icon",
                "https://apkdrop.rawinstinctai.de:444/sample-app/icon","/api/sample-app/install.json","/sample-app/releases/r1.apk",
                "/api/sample-app/screenshot/6","/sample-app/icon#fragment"}) {
            assertThrows(value,SecurityException.class,()->StoreClient.imageUri(value));
        }
    }
    @Test public void developerFollowsUseImmutableIdsAndStrictHandles() {
        assertTrue(DeveloperFollows.validId("42"));assertFalse(DeveloperFollows.validId("fixture-dev"));assertFalse(DeveloperFollows.validId("0"));
        assertEquals("fixture-dev",StoreClient.handle("fixture-dev"));
        for(String value:new String[]{"../api","-dev","dev-","dev?x=1",""})assertThrows(SecurityException.class,()->StoreClient.handle(value));
    }
}
