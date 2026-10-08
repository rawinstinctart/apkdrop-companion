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
    @Test public void githubAvatarRequiresExactProfileOwnerAndPinnedImageRoute() {
        String avatar="https://avatars.githubusercontent.com/u/203492359?v=4";
        assertEquals("avatars.githubusercontent.com",StoreClient.avatarUri(avatar,"203492359").getHost());
        for(String value:new String[]{
                "http://avatars.githubusercontent.com/u/203492359?v=4",
                "https://avatars.githubusercontent.com.evil.test/u/203492359?v=4",
                "https://user@avatars.githubusercontent.com/u/203492359?v=4",
                "https://avatars.githubusercontent.com:8443/u/203492359?v=4",
                "https://avatars.githubusercontent.com/u/42?v=4",
                "https://avatars.githubusercontent.com/u/203492359?size=4096",
                "https://avatars.githubusercontent.com/u/203492359/other?v=4",
                "https://avatars.githubusercontent.com/u/203492359#fragment"
        })assertThrows(value,SecurityException.class,()->StoreClient.avatarUri(value,"203492359"));
        for(String wrongId:new String[]{"owner","0","fixture-dev","42",""})
            assertThrows(SecurityException.class,()->StoreClient.avatarUri(avatar,wrongId));
        // External avatars never enter the APKDrop app icon/screenshot allowlist.
        assertThrows(SecurityException.class,()->StoreClient.imageUri(avatar));
    }
    @Test public void developerFollowsUseImmutableIdsAndStrictHandles() {
        assertTrue(DeveloperFollows.validId("42"));assertFalse(DeveloperFollows.validId("fixture-dev"));assertFalse(DeveloperFollows.validId("0"));
        assertEquals("fixture-dev",StoreClient.handle("fixture-dev"));
        for(String value:new String[]{"../api","-dev","dev-","dev?x=1",""})assertThrows(SecurityException.class,()->StoreClient.handle(value));
    }
}
