package de.rawinstinctai.apkdrop;

import android.app.AlertDialog;
import android.view.View;
import android.widget.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowAlertDialog;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=26,qualifiers="w360dp-h800dp-xhdpi")
public class Alpha15AssistantTest {
    private ActivityController<MainActivity> controller;
    @After public void close(){if(controller!=null)controller.destroy();}
    private MainActivity create(){controller=Robolectric.buildActivity(MainActivity.class).create();return controller.get();}
    private StoreController store(MainActivity a)throws Exception{var field=MainActivity.class.getDeclaredField("store");field.setAccessible(true);return (StoreController)field.get(a);}
    @Test public void homeCombinesAPKSuggestionsAndReleasesWithoutTurningThemIntoUpdates() throws Exception {
        MainActivity a=create();StoreController s=store(a);var field=StoreController.class.getDeclaredField("radar");field.setAccessible(true);Object radar=field.get(s);var proposals=GitHubRadarController.class.getDeclaredField("proposals");proposals.setAccessible(true);
        ((Map<String,JSONObject>)proposals.get(radar)).put("dev/new-app",new JSONObject().put("name","New App").put("fullName","dev/new-app"));var checked=GitHubRadarController.class.getDeclaredField("checkedAt");checked.setAccessible(true);checked.setLong(radar,1L);
        var feed=StoreController.class.getDeclaredField("homeReleases");feed.setAccessible(true);feed.set(s,new JSONArray().put(new JSONObject().put("slug","sample-app").put("name","Pocket Notes").put("version","v1.2")));
        var known=StoreController.class.getDeclaredField("homeFeedKnown");known.setAccessible(true);known.setBoolean(s,true);s.changed();
        String summary=((TextView)a.findViewById(R.id.actionSummary)).getText().toString();assertTrue(summary.contains("0 Updates"));assertTrue(summary.contains("1 APK-Vorschlag"));assertTrue(summary.contains("1 neue Releases"));
    }
    @Test public void installedMatchingRequiresExplicitConsentAndIsHiddenFromUpdates(){MainActivity a=create();a.findViewById(R.id.navApps).performClick();assertEquals(View.VISIBLE,a.findViewById(R.id.findInstalledApps).getVisibility());a.findViewById(R.id.findInstalledApps).performClick();AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();assertEquals("Installierte Apps erkennen?",org.robolectric.Shadows.shadowOf(dialog).getTitle().toString());dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();assertEquals(0,((LinearLayout)a.findViewById(R.id.installedSuggestions)).getChildCount());a.findViewById(R.id.navUpdates).performClick();assertEquals(View.GONE,a.findViewById(R.id.findInstalledApps).getVisibility());}
    @Test public void trustSeparatesInstalledSignerEvidenceFromLocalAPKVerification(){InstallContract r=new InstallContract("sample-app","App","r1","1.2",12,"dev.sample.app",26,36,List.of(),Set.of(),Set.of("a".repeat(64)),"b".repeat(64),4096,"stable","","","","");InstalledState different=new InstalledState("dev.sample.app",11,Set.of("c".repeat(64)),Set.of());assertTrue(DropTrust.describe(r,different,null,false).contains("Abweichende Signatur"));assertTrue(DropTrust.describe(r,null,null,false).contains("nach dem Download"));assertTrue(DropTrust.describe(r,null,null,true).contains("lokal bestätigt"));}
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) public void alpha15HomeDiscoverAndConsentPreview() throws Exception {
        MainActivity a=create();render(a,"alpha15-home");a.findViewById(R.id.navApps).performClick();render(a,"alpha15-installed-apps");StoreController s=store(a);
        JSONObject app=new JSONObject().put("slug","pocket-notes").put("name","Pocket Notes").put("description","Private Notizen. Direkt vom Entwickler.").put("version","v1.2").put("channel","stable").put("publishedAt","2026-10-09T10:00:00Z").put("signatureVerified",true).put("developer",new JSONObject().put("handle","sampledev").put("name","Sample Developer"));
        a.findViewById(R.id.navDiscover).performClick();s.renderCatalog(new JSONObject().put("schema","apkdrop.discover.v1").put("apps",new JSONArray().put(app)).put("total",1).put("page",1).put("pages",1));render(a,"alpha15-discover");
        var method=StoreController.class.getDeclaredMethod("renderFeed",JSONArray.class,boolean.class);method.setAccessible(true);method.invoke(s,new JSONArray().put(app.put("notes","## Was ist neu?\nSchnellere Suche und Offline-Notizen.")),false);render(a,"alpha15-release-feed");
    }
    @Test public void discoverToolbarKeepsSortingAndCountReadable() throws Exception {
        MainActivity a=create();StoreController s=store(a);
        a.findViewById(R.id.navDiscover).performClick();
        JSONObject entry=new JSONObject().put("slug","sample-app").put("name","Sample App");
        JSONObject single=new JSONObject().put("schema","apkdrop.discover.v1")
                .put("apps",new JSONArray().put(entry)).put("total",1).put("page",1).put("pages",1);
        s.renderCatalog(single);
        TextView count=a.findViewById(R.id.discoverCount),status=a.findViewById(R.id.discoverStatus);
        Button sort=a.findViewById(R.id.discoverSort),refresh=a.findViewById(R.id.discoverRefresh);
        assertEquals("1 App",count.getText().toString());
        assertEquals(View.VISIBLE,count.getVisibility());
        assertEquals(View.GONE,status.getVisibility());
        assertEquals(View.GONE,sort.getVisibility());
        assertEquals(View.GONE,a.findViewById(R.id.discoverFilter).getVisibility());
        assertEquals(View.GONE,refresh.getVisibility());
        View root=a.findViewById(R.id.pageRoot);
        root.measure(View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1600,View.MeasureSpec.EXACTLY));
        root.layout(0,0,720,1600);
        assertTrue(count.getWidth()>0);
        assertEquals(0,sort.getWidth());
        assertEquals(0,refresh.getWidth());
        java.lang.reflect.Method change=StoreController.class.getDeclaredMethod("chooseSort",boolean.class);
        change.setAccessible(true);change.invoke(s,true);
        assertEquals(View.GONE,sort.getVisibility());
        assertEquals(View.GONE,refresh.getVisibility());
        assertTrue(sort.getContentDescription().toString().contains("Name A bis Z"));
        JSONObject multiple=new JSONObject(single.toString()).put("total",26).put("page",2).put("pages",3);
        s.renderCatalog(multiple);
        assertEquals("26 Apps · Seite 2/3",count.getText().toString());
    }
    private void render(MainActivity a,String name)throws Exception{View root=a.findViewById(R.id.pageRoot);root.measure(View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1600,View.MeasureSpec.EXACTLY));root.layout(0,0,720,1600);String dir=System.getProperty("apkdrop.preview.dir");if(dir==null)return;java.io.File file=new java.io.File(dir,name+".png");file.getParentFile().mkdirs();android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(720,1600,android.graphics.Bitmap.Config.ARGB_8888);root.draw(new android.graphics.Canvas(bitmap));try(var out=new java.io.FileOutputStream(file)){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
}
