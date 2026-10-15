package de.rawinstinctai.apkdrop;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import java.io.File;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Rendering guarantees from PR #61, using the current summary/details/DropPass report. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=26,qualifiers="w320dp-h640dp-xhdpi")
public final class LocalApkReportDialogTest {
    @Test public void conciseResultDefaultsClosedAndExpandsRealEvidence() throws Exception {
        var controller=Robolectric.buildActivity(MainActivity.class).create();
        AlertDialog dialog=null;
        try {
            File fixture=new File(getClass().getResource("/droppass-vectors/apk-fixture.bin").toURI());
            String sha256=DropPassVerifier.hashFile(fixture);
            var report=report("✓ Signatur stimmt mit installierter App überein",
                    "SHA-256 der APK:\n"+sha256);
            dialog=controller.get().showLocalApkReport(Uri.parse("content://fixture/apk"),report,null);
            TextView details=dialog.findViewById(R.id.localApkDetails);
            Button toggle=dialog.findViewById(R.id.localApkDetailsToggle);
            assertEquals(View.GONE,details.getVisibility());
            assertFalse(visibleText(dialog.getWindow().getDecorView()).contains(sha256));
            assertEquals("Schließen",dialog.getButton(DialogInterface.BUTTON_POSITIVE).getText().toString());
            assertEquals("DropPass-Datei wählen",dialog.getButton(DialogInterface.BUTTON_NEUTRAL).getText().toString());
            assertEquals(View.GONE,dialog.getButton(DialogInterface.BUTTON_NEGATIVE).getVisibility());
            assertTrue(visibleText(dialog.getWindow().getDecorView()).contains(report.dropPass.badge()));
            assertFalse(((TextView)dialog.findViewById(R.id.localApkSummary)).getText().toString().contains("DropPass:"));
            toggle.performClick();
            assertEquals(View.VISIBLE,details.getVisibility());
            assertTrue(visibleText(dialog.getWindow().getDecorView()).contains("SHA-256 der APK:\n"+sha256));
            assertEquals("Technische Details ausblenden",toggle.getText().toString());
            toggle.performClick();
            assertEquals(View.GONE,details.getVisibility());
            assertFalse(visibleText(dialog.getWindow().getDecorView()).contains(sha256));
            assertEquals("Technische Details anzeigen",toggle.getText().toString());
        } finally {
            if(dialog!=null)dialog.dismiss();
            controller.destroy();
        }
    }

    @Test public void signerMismatchIsVisibleWithUpdateInstructionWhileDetailsAreClosed() {
        var controller=Robolectric.buildActivity(MainActivity.class).create();
        AlertDialog dialog=null;
        try {
            var report=report("⚠ Signatur weicht von installierter App ab. Nicht als normales Update verwenden",
                    "⚠ Signatur weicht von der installierten App ab. Nicht als normales Update verwenden.");
            dialog=controller.get().showLocalApkReport(Uri.parse("content://fixture/apk"),report,null);
            assertEquals(View.GONE,dialog.findViewById(R.id.localApkDetails).getVisibility());
            String visible=visibleText(dialog.getWindow().getDecorView());
            assertTrue(visible.contains("Signatur weicht von installierter App ab"));
            assertTrue(visible.contains("Nicht als normales Update verwenden"));
            assertFalse(visible.contains("stimmt mit installierter App überein"));
        } finally {
            if(dialog!=null)dialog.dismiss();
            controller.destroy();
        }
    }

    @Test public void noInstalledAppDoesNotPretendSignerContinuity() {
        var controller=Robolectric.buildActivity(MainActivity.class).create();
        AlertDialog dialog=null;
        try {
            var report=report("○ Kein installierter Vergleich verfügbar\nNeue Berechtigungen: Noch nicht vergleichbar",
                    "Noch kein Vergleich mit einer installierten Version möglich.");
            dialog=controller.get().showLocalApkReport(Uri.parse("content://fixture/apk"),report,null);
            assertEquals(View.GONE,dialog.findViewById(R.id.localApkDetails).getVisibility());
            String visible=visibleText(dialog.getWindow().getDecorView());
            assertTrue(visible.contains("Kein installierter Vergleich"));
            assertTrue(visible.contains("Noch nicht vergleichbar"));
            assertFalse(visible.contains("stimmt mit installierter App überein"));
        } finally {
            if(dialog!=null)dialog.dismiss();
            controller.destroy();
        }
    }

    private static LocalApkInspector.Report report(String identity,String details) {
        var pass=DropPassVerifier.verify(null,null,null,null,0,null,false);
        return new LocalApkInspector.Report("✓ APK-Signatur kryptografisch gültig\n"+identity+"\n"
                +pass.badge()+"\n\nDies bestätigt keine Malware-Freiheit und erteilt keine Installationsfreigabe.",
                details,pass);
    }

    private static String visibleText(View view) {
        if(view.getVisibility()!=View.VISIBLE)return "";
        if(view instanceof TextView)return ((TextView)view).getText().toString();
        StringBuilder text=new StringBuilder();
        if(view instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++)text.append(visibleText(group.getChildAt(i))).append('\n');
        }
        return text.toString();
    }
}
