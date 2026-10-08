package de.rawinstinctai.apkdrop;

import android.content.Context;
import android.text.Spanned;
import android.text.style.*;
import android.view.View;
import android.widget.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35,qualifiers="w360dp-h800dp-xhdpi")
public class Alpha12ExperienceTest {
    private ActivityController<MainActivity> controller;
    private Context context;
    @Before public void setup() {context=RuntimeEnvironment.getApplication();DropPilot.enabled(context,false);DropPilot.preferences(context).edit().clear().commit();}
    @After public void close(){if(controller!=null)controller.destroy();DropPilot.enabled(context,false);}
    @Test public void markdownRendersNativeHeadingsListsAndInlineStyles() {
        Spanned result=ReleaseNotes.render("# FREY 1.0\n\n## Neu\n- **Push-Auswahl** und *Audio*\n- `UnifiedPush`\n1. Installieren\n```text\n**literal code**\n```");
        assertTrue(result.toString().contains("FREY 1.0\n\nNeu\n• Push-Auswahl und Audio"));
        assertTrue(result.toString().contains("1. Installieren"));
        assertTrue(result.toString().contains("**literal code**"));
        assertEquals(2,result.getSpans(0,result.length(),RelativeSizeSpan.class).length);
        assertTrue(result.getSpans(0,result.length(),StyleSpan.class).length>=4);
        assertEquals(2,result.getSpans(0,result.length(),TypefaceSpan.class).length);
    }
    @Test public void untrustedMarkupNeverBecomesExecutableOrRemoteContent() {
        Spanned result=ReleaseNotes.render("[Nicht öffnen](javascript:alert) ![Bild](https://evil.test/tracker.png) <script>literal</script>");
        assertEquals(0,result.getSpans(0,result.length(),URLSpan.class).length);
        assertFalse(result.toString().contains("tracker.png"));
        assertFalse(result.toString().contains("javascript:"));
        assertTrue(result.toString().contains("<script>literal</script>"));
    }
    @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE) public void previewPreservesStylesAndFullNotesCanBeExpanded() throws Exception {
        controller=Robolectric.buildActivity(MainActivity.class).create();MainActivity activity=controller.get();
        String original="# FREY\n\n"+("- **Ein Update** mit ausführlicher Erklärung.\n").repeat(30);
        InstallContract release=new InstallContract("sample-app","FREY","r1","1.2",12,"de.example.frey",26,35,List.of(),Set.of(),Set.of("a".repeat(64)),"b".repeat(64),4,"beta",original,"https://apkdrop.rawinstinctai.de/sample-app/releases/file.apk","https://apkdrop.rawinstinctai.de/sample-app/receipt","https://apkdrop.rawinstinctai.de/sample-app");
        java.lang.reflect.Method show=MainActivity.class.getDeclaredMethod("show",InstallContract.class,InstalledState.class,InstallPolicy.Result.class,boolean.class,boolean.class);show.setAccessible(true);
        show.invoke(activity,release,null,InstallPolicy.evaluate(release,null,35,new String[]{"arm64-v8a"}),false,false);
        TextView notes=activity.findViewById(R.id.notesText);String shortText=notes.getText().toString();
        assertTrue(shortText.length()<ReleaseNotes.render(original).length());
        assertEquals(View.GONE,activity.findViewById(R.id.radarText).getVisibility());
        assertEquals(View.GONE,activity.findViewById(R.id.trustSummary).getVisibility());
        activity.findViewById(R.id.homePanel).setVisibility(View.GONE);
        activity.findViewById(R.id.detailPanel).setVisibility(View.VISIBLE);
        activity.findViewById(R.id.releaseCard).setVisibility(View.VISIBLE);
        render(activity,"alpha12-detail");
        activity.findViewById(R.id.notesButton).performClick();assertEquals(ReleaseNotes.render(original).toString(),notes.getText().toString());
        activity.findViewById(R.id.notesButton).performClick();assertEquals(shortText,notes.getText().toString());
        activity.findViewById(R.id.radarButton).performClick();assertEquals(View.VISIBLE,activity.findViewById(R.id.radarText).getVisibility());
        activity.findViewById(R.id.trustButton).performClick();assertEquals(View.VISIBLE,activity.findViewById(R.id.trustSummary).getVisibility());
    }
    private void render(MainActivity activity,String name) throws Exception {
        String dir=System.getProperty("apkdrop.preview.dir");if(dir==null)return;
        View root=activity.findViewById(R.id.pageRoot);
        root.measure(View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1600,View.MeasureSpec.EXACTLY));root.layout(0,0,720,1600);
        java.io.File file=new java.io.File(dir,name+".png");file.getParentFile().mkdirs();
        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(720,1600,android.graphics.Bitmap.Config.ARGB_8888);root.draw(new android.graphics.Canvas(bitmap));
        try(java.io.OutputStream out=new java.io.FileOutputStream(file)){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
    }
    @Test public void jobHistoryOnlyReportsRealStartsAndCannotBeOverwrittenByOldRuns() throws Exception {
        assertTrue(DropPilot.dashboard(context).contains("Noch keine Hintergrundprüfung"));
        DropPilot.enabled(context,true);
        long old=DropPilot.begin(context);DropPilot.stage(context,old,"Download · 35 %");
        assertEquals("Download · 35 %",DropPilot.headline(context));
        long next=DropPilot.begin(context);DropPilot.finish(context,old,"prepared");
        assertEquals("running",DropPilot.preferences(context).getString("outcome",""));
        DropPilot.finish(context,next,"error");
        assertTrue(DropPilot.dashboard(context).contains("fehlgeschlagen"));
        DropPilot.enabled(context,false);DropPilot.stage(context,next,"FAKE");
        assertTrue(DropPilot.headline(context).startsWith("Aus"));
        assertFalse(DropPilot.headline(context).contains("FAKE"));
    }
    @Test public void processDeathNeverLeavesAFalseRunningOrReadyClaim() {
        DropPilot.enabled(context,true);
        DropPilot.preferences(context).edit().putLong("lastStarted",System.currentTimeMillis()).putString("outcome","running").putString("stage","Download läuft").putString("slug","missing-app").putString("file",VerifiedApkFiles.newName()).commit();
        assertNull(DropPilot.preparedSlug(context));
        assertFalse(DropPilot.headline(context).contains("Download läuft"));
        assertTrue(DropPilot.dashboard(context).contains("nicht abgeschlossen"));
    }
}
