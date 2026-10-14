package de.rawinstinctai.apkdrop;

import android.app.AlertDialog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** A valid file signature must never accidentally become a DropPass/malware endorsement. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=26,qualifiers="w320dp-h640dp-xhdpi")
public final class LocalApkReportDialogTest {
    @Test public void conciseResultDefaultsClosedAndExpandsRealEvidence() {
        var controller=Robolectric.buildActivity(MainActivity.class).create();
        AlertDialog dialog=null;
        try {
            var report=new LocalApkInspector.Report(
                    "SHA-256: abcdef\nZertifikat: 123456",
                    "de.rawinstinctai.apkdrop.debug","0.1.0-alpha.33-preview",
                    LocalApkInspector.SignerComparison.MATCH,8,0);
            dialog=LocalApkReportDialog.create(controller.get(),report);
            dialog.show();
            TextView details=dialog.findViewById(R.id.localApkDetails);
            Button toggle=dialog.findViewById(R.id.localApkDetailsToggle);
            assertNotNull(details);
            assertNotNull(toggle);
            assertEquals(View.GONE,details.getVisibility());
            assertTrue(allText(dialog.getWindow().getDecorView()).contains("DropPass-Herkunft"));
            assertTrue(allText(dialog.getWindow().getDecorView()).contains("Nicht geprüft"));
            assertTrue(allText(dialog.getWindow().getDecorView()).contains("0"));
            toggle.performClick();
            assertEquals(View.VISIBLE,details.getVisibility());
            assertTrue(details.getText().toString().contains("SHA-256: abcdef"));
            toggle.performClick();
            assertEquals(View.GONE,details.getVisibility());
            assertNull(dialog.findViewById(R.id.localApkWarning));
        } finally {
            if(dialog!=null)dialog.dismiss();
            controller.destroy();
        }
    }

    @Test public void signerMismatchIsProminentAndNeverMarkedAsUpdateSafe() {
        var controller=Robolectric.buildActivity(MainActivity.class).create();
        AlertDialog dialog=null;
        try {
            var report=new LocalApkInspector.Report("Signatur abweichend","app.test","2.0",
                    LocalApkInspector.SignerComparison.MISMATCH,4,2);
            dialog=LocalApkReportDialog.create(controller.get(),report);
            dialog.show();
            TextView warning=dialog.findViewById(R.id.localApkWarning);
            assertNotNull(warning);
            assertTrue(warning.getText().toString().contains("Nicht als normales Update"));
            String visible=allText(dialog.getWindow().getDecorView());
            assertTrue(visible.contains("Abweichung"));
            assertTrue(visible.contains("Nicht geprüft"));
        } finally {
            if(dialog!=null)dialog.dismiss();
            controller.destroy();
        }
    }

    @Test public void noInstalledAppDoesNotPretendSignerContinuity() {
        var controller=Robolectric.buildActivity(MainActivity.class).create();
        AlertDialog dialog=null;
        try {
            var report=new LocalApkInspector.Report("Einzeln geprüft","app.test","1",
                    LocalApkInspector.SignerComparison.NOT_INSTALLED,1,-1);
            dialog=LocalApkReportDialog.create(controller.get(),report);
            dialog.show();
            String visible=allText(dialog.getWindow().getDecorView());
            assertTrue(visible.contains("Nicht vergleichbar"));
            assertTrue(visible.contains("Kein Malware-Scan"));
        } finally {
            if(dialog!=null)dialog.dismiss();
            controller.destroy();
        }
    }
    private static String allText(View v) {
        if(v instanceof TextView)return ((TextView)v).getText().toString();
        StringBuilder b=new StringBuilder();
        if(v instanceof ViewGroup) {
            ViewGroup parent=(ViewGroup)v;
            for(int i=0;i<parent.getChildCount();i++) b.append(allText(parent.getChildAt(i))).append(' ');
        }
        return b.toString();
    }
}
