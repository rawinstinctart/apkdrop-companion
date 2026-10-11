package de.rawinstinctai.apkdrop;

import android.view.View;
import android.widget.Button;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** First-launch usability must not weaken the existing link/install contract. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=26,qualifiers="w320dp-h640dp-xhdpi")
public final class Alpha31SimpleExperienceTest {
    @Test public void firstRunShowsOnePrimaryActionAndKeepsManualLinkAvailable() {
        var controller=Robolectric.buildActivity(MainActivity.class).create();
        try {
            MainActivity a=controller.get();
            Button primary=a.findViewById(R.id.homeUpdates);
            assertTrue(primary.getText().toString().contains("Apps entdecken"));
            assertEquals(View.GONE,a.findViewById(R.id.homeDiscover).getVisibility());
            assertEquals(View.VISIBLE,a.findViewById(R.id.homeAddLink).getVisibility());
            assertEquals("Empty recommendations must not waste screen space",
                    View.GONE,a.findViewById(R.id.homeActionTitle).getVisibility());
            a.findViewById(R.id.homeAddLink).performClick();
            assertEquals(View.VISIBLE,a.findViewById(R.id.linkPanel).getVisibility());
            assertTrue(a.findViewById(R.id.urlInput).hasFocus());
        } finally {controller.destroy();}
    }
}
