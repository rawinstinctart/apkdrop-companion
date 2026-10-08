package de.rawinstinctai.apkdrop;

import android.content.Intent;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=26)
public final class ShareImportTest {
    @Test public void sharedLinkInNormalMessageIsExtracted() {
        assertEquals("pocket-notes",SlugParser.parseShared("Schau dir diese App an: https://apkdrop.rawinstinctai.de/install/pocket-notes !"));
        assertEquals("pocket-notes",SlugParser.parseShared("https://apkdrop.rawinstinctai.de/install/pocket-notes"));
        assertEquals("pocket-notes",SlugParser.parseShared("pocket-notes"));
    }
    @Test public void foreignHostAndDangerousSuffixAreRejected() {
        for(String input:new String[]{
                "https://another.example/install/pocket-notes",
                "https://apkdrop.rawinstinctai.de/install/pocket-notes?override=1",
                "https://apkdrop.rawinstinctai.de/install/pocket-notes/extra",
                "https://apkdrop.rawinstinctai.de.evil.example/install/pocket-notes",
                "https://apkdrop.rawinstinctai.de/install/pocket-notes#fragment",
                "x".repeat(4097),
                null
        }) assertThrows(IllegalArgumentException.class,()->SlugParser.parseShared(input));
    }
    @Test public void ambiguousSharedTextIsRejected() {
        assertThrows(IllegalArgumentException.class,()->SlugParser.parseShared(
            "https://apkdrop.rawinstinctai.de/install/app-one and https://apkdrop.rawinstinctai.de/install/app-two"));
    }
    @Test public void sendIntentRoutesToInstallDetailsWithoutStartingInstaller() {
        Intent intent=new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT,"https://apkdrop.rawinstinctai.de/install/pocket-notes");
        MainActivity activity=Robolectric.buildActivity(MainActivity.class,intent).create().get();
        assertEquals("pocket-notes",((TextView)activity.findViewById(R.id.urlInput)).getText().toString());
        activity.onDestroy();
    }
}
