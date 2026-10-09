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
    @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE) public void previewShowsOnlyBackendSummaryWithoutRawNotes() throws Exception {
        controller=Robolectric.buildActivity(MainActivity.class).create();MainActivity activity=controller.get();
        String original="# FREY\n\n"+("- **Ein Update** mit ausführlicher Erklärung.\n").repeat(30);
        InstallContract release=new InstallContract("sample-app","FREY","r1","1.2",12,"de.example.frey",26,35,List.of(),Set.of(),Set.of("a".repeat(64)),"b".repeat(64),4,"beta",original,"FREY: Push-Einstellungen sind leichter erreichbar.","https://apkdrop.rawinstinctai.de/sample-app/releases/file.apk","https://apkdrop.rawinstinctai.de/sample-app/receipt","https://apkdrop.rawinstinctai.de/sample-app");
        java.lang.reflect.Method show=MainActivity.class.getDeclaredMethod("show",InstallContract.class,InstalledState.class,InstallPolicy.Result.class,boolean.class,boolean.class);show.setAccessible(true);
        show.invoke(activity,release,null,InstallPolicy.evaluate(release,null,35,new String[]{"arm64-v8a"}),false,false);
        TextView notes=activity.findViewById(R.id.notesText);String shortText=notes.getText().toString();
        assertEquals("FREY: Push-Einstellungen sind leichter erreichbar.",shortText);
        assertFalse(shortText.contains("Ein Update"));
        assertEquals(View.GONE,activity.findViewById(R.id.notesButton).getVisibility());
        assertEquals(View.GONE,activity.findViewById(R.id.radarText).getVisibility());
        assertEquals(View.GONE,activity.findViewById(R.id.trustSummary).getVisibility());
        java.lang.reflect.Field store=MainActivity.class.getDeclaredField("store");store.setAccessible(true);((StoreController)store.get(activity)).showDetail();
        activity.findViewById(R.id.releaseCard).setVisibility(View.VISIBLE);
        render(activity,"alpha12-detail");
        assertEquals(shortText,notes.getText().toString());
        activity.findViewById(R.id.radarButton).performClick();assertEquals(View.VISIBLE,activity.findViewById(R.id.radarText).getVisibility());
        activity.findViewById(R.id.trustButton).performClick();assertEquals(View.VISIBLE,activity.findViewById(R.id.trustSummary).getVisibility());
        java.lang.reflect.Method reset=MainActivity.class.getDeclaredMethod("resetCandidate");reset.setAccessible(true);reset.invoke(activity);
        assertEquals(View.GONE,activity.findViewById(R.id.trustSummary).getVisibility());
        assertTrue(((Button)activity.findViewById(R.id.trustButton)).getText().toString().contains("ansehen +"));
        assertEquals(activity.getString(R.string.alpha12_radar_closed),((Button)activity.findViewById(R.id.radarButton)).getText().toString());
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
    @Test @Config(sdk=26) public void preparedDownloadRequiresExistingFilePinnedIdentityAndOlderInstalledApp() throws Exception {
        byte[] certificate={1,2,3,4};
        String signer=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(certificate));
        android.content.pm.PackageInfo info=new android.content.pm.PackageInfo();info.packageName="de.example.frey";info.versionCode=1;info.versionName="1.0";
        info.signatures=new android.content.pm.Signature[]{new android.content.pm.Signature(certificate)};
        info.applicationInfo=new android.content.pm.ApplicationInfo();info.applicationInfo.packageName=info.packageName;
        org.robolectric.Shadows.shadowOf(context.getPackageManager()).installPackage(info);
        AppLibrary.Entry pin=new AppLibrary.Entry("sample-app","FREY",info.packageName,Set.of(signer));
        new AppLibraryStore(context).save(new AppLibrary().add(pin));DropPilot.enabled(context,true);
        InstallContract release=new InstallContract(pin.slug,pin.name,"r2","1.1",2,pin.packageName,26,35,List.of(),Set.of(),pin.signers,"b".repeat(64),4,"beta","","","","");
        java.io.File dir=DropPilot.directory(context);assertTrue(dir.isDirectory()||dir.mkdirs());java.io.File file=new java.io.File(dir,VerifiedApkFiles.newName());
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(file)){out.write(new byte[]{1,2,3,4});}
        long run=DropPilot.begin(context);assertTrue(DropPilot.record(context,pin,release,file,run));DropPilot.finish(context,run,"prepared");
        assertEquals(pin.slug,DropPilot.preparedSlug(context));
        assertTrue(DropPilot.headline(context).contains("Update vorbereitet"));
        info.versionCode=2;org.robolectric.Shadows.shadowOf(context.getPackageManager()).installPackage(info);assertNull(DropPilot.preparedSlug(context));
        info.versionCode=1;org.robolectric.Shadows.shadowOf(context.getPackageManager()).installPackage(info);assertEquals(pin.slug,DropPilot.preparedSlug(context));
        assertTrue(file.delete());assertNull(DropPilot.preparedSlug(context));
        assertFalse(DropPilot.record(context,pin,release,file,run));
    }
    @Test public void processDeathNeverLeavesAFalseRunningOrReadyClaim() {
        DropPilot.enabled(context,true);
        DropPilot.preferences(context).edit().putLong("lastStarted",System.currentTimeMillis()).putString("outcome","running").putString("stage","Download läuft").putString("slug","missing-app").putString("file",VerifiedApkFiles.newName()).commit();
        assertNull(DropPilot.preparedSlug(context));
        assertFalse(DropPilot.headline(context).contains("Download läuft"));
        assertTrue(DropPilot.dashboard(context).contains("nicht abgeschlossen"));
    }
}
