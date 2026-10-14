package de.rawinstinctai.apkdrop;

import android.content.Intent;
import android.view.View;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Manual security inspection remains an opt-in secondary action, not a download/install shortcut. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=26,qualifiers="w360dp-h800dp-xhdpi")
public final class LocalApkInspectionUiTest {
    @Test public void moreOffersOfflineFileSelectionWithoutStartingInstaller() {
        var controller=Robolectric.buildActivity(MainActivity.class).create();
        try {
            MainActivity activity=controller.get();
            assertEquals(View.GONE,activity.findViewById(R.id.settingsPanel).getVisibility());
            activity.findViewById(R.id.navSettings).performClick();
            assertEquals(View.VISIBLE,activity.findViewById(R.id.verifyLocalApk).getVisibility());
            activity.findViewById(R.id.verifyLocalApk).performClick();
            var started=Shadows.shadowOf(activity).getNextStartedActivityForResult();
            assertNotNull("File picker must open",started);
            assertEquals(Intent.ACTION_OPEN_DOCUMENT,started.intent.getAction());
            assertEquals("*/*",started.intent.getType());
            assertTrue(started.intent.hasCategory(Intent.CATEGORY_OPENABLE));
            assertNull("No APK installer may launch",Shadows.shadowOf(activity).getNextStartedActivity());
        } finally {controller.destroy();}
    }
}
