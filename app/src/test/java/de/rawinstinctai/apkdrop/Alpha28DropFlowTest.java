package de.rawinstinctai.apkdrop;

import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=26,qualifiers="w320dp-h640dp-xhdpi")
public class Alpha28DropFlowTest {
    @Test public void sharedAppSaveIsFirstAndPrimaryBeforeInstallation()throws Exception {
        var controller=Robolectric.buildActivity(MainActivity.class).create();
        try {
            MainActivity activity=controller.get();
            Button save=activity.findViewById(R.id.addButton);
            Button install=activity.findViewById(R.id.actionButton);
            LinearLayout parent=(LinearLayout)save.getParent();
            assertTrue("The one-tap monitoring action must appear before manual installation",
                    parent.indexOfChild(save)<parent.indexOfChild(install));
            var current=MainActivity.class.getDeclaredField("currentRelease");
            current.setAccessible(true);
            current.set(activity,Alpha18ReliabilityTest.release("a".repeat(64)));
            var update=MainActivity.class.getDeclaredMethod("updateSaveButton");
            update.setAccessible(true);
            update.invoke(activity);
            assertEquals(View.VISIBLE,save.getVisibility());
            assertEquals(activity.getColor(R.color.lime_dark),save.getCurrentTextColor());
            assertEquals(activity.getColor(R.color.text),install.getCurrentTextColor());
            assertTrue(save.getText().toString().contains("Übernehmen & Updates überwachen"));
            TextView status=activity.findViewById(R.id.monitoringStatus);
            assertTrue(status.getText().toString().contains("automatische Update-Prüfungen"));
        } finally {controller.destroy();}
    }
}
