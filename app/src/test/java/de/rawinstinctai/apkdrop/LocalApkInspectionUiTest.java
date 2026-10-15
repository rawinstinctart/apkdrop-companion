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
    @Test public void reportExpandsInPlaceAndOffersOptionalPassPicker() {
        var controller=Robolectric.buildActivity(MainActivity.class).create();
        try {
            MainActivity activity=controller.get();
            var report=new LocalApkInspector.Report(DropPassVerifier.verify(null,null,null,null,0,null,false).badge(),"APK SHA-256: synthetic");
            activity.showLocalApkReport(android.net.Uri.parse("content://synthetic/apk"),report,null);
            android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            assertNotNull(dialog);
            android.widget.Button expand=findButton(dialog.getWindow().getDecorView(),"Technische Details anzeigen");
            assertNotNull(expand);
            expand.performClick();
            assertEquals("Technische Details ausblenden",expand.getText().toString());
            assertSame(dialog,org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog());
            assertNull(Shadows.shadowOf(activity).getNextStartedActivityForResult());
            dialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL).performClick();
            var started=Shadows.shadowOf(activity).getNextStartedActivityForResult();
            // Process the dialog Handler callback without draining recurring TextView animation tasks.
            for(int i=0;started==null && i<20;i++) {
                Shadows.shadowOf(android.os.Looper.getMainLooper()).runOneTask();
                started=Shadows.shadowOf(activity).getNextStartedActivityForResult();
            }
            assertNotNull(started);
            assertEquals(Intent.ACTION_OPEN_DOCUMENT,started.intent.getAction());
            assertTrue(started.intent.hasCategory(Intent.CATEGORY_OPENABLE));
        } finally {controller.destroy();}
    }
    private android.widget.Button findButton(View view,String text) {
        if(view instanceof android.widget.Button && text.contentEquals(((android.widget.Button)view).getText()))return (android.widget.Button)view;
        if(view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group=(android.view.ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++) {
                android.widget.Button button=findButton(group.getChildAt(i),text);
                if(button!=null)return button;
            }
        }
        return null;
    }

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
            assertNotNull(started);
            assertEquals(Intent.ACTION_OPEN_DOCUMENT,started.intent.getAction());
            assertEquals("*/*",started.intent.getType());
            assertTrue(started.intent.hasCategory(Intent.CATEGORY_OPENABLE));
            // The started activity is the Android picker, not an APK installer.
        } finally {controller.destroy();}
    }
}
