package de.rawinstinctai.apkdrop;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ActivityController;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=26,qualifiers="w360dp-h800dp-xhdpi")
public final class StoreNavigationTest {
    private ActivityController<MainActivity> controller;
    @After public void close(){if(controller!=null)controller.destroy();RuntimeEnvironment.setFontScale(1f);}
    private MainActivity create(Bundle state){controller=Robolectric.buildActivity(MainActivity.class).create(state);return controller.get();}
    @Test public void navigationSeparatesCatalogLibraryUpdatesAndSettings() {
        MainActivity a=create(null);
        assertEquals(View.VISIBLE,a.findViewById(R.id.discoverPanel).getVisibility());assertEquals(View.GONE,a.findViewById(R.id.libraryPanel).getVisibility());
        a.findViewById(R.id.navApps).performClick();assertEquals(View.VISIBLE,a.findViewById(R.id.libraryPanel).getVisibility());assertEquals(View.GONE,a.findViewById(R.id.settingsPanel).getVisibility());
        a.findViewById(R.id.navUpdates).performClick();assertEquals("Updates",((TextView)a.findViewById(R.id.sectionTitle)).getText().toString());
        a.findViewById(R.id.navSettings).performClick();assertEquals(View.VISIBLE,a.findViewById(R.id.settingsPanel).getVisibility());assertEquals(View.GONE,a.findViewById(R.id.libraryPanel).getVisibility());
    }
    @Test public void restoresSelectedTabAndFollowsWithoutAccount() throws Exception {
        Bundle saved=new Bundle();saved.putInt("storeTab",3);MainActivity a=create(saved);
        assertEquals("Einstellungen",((TextView)a.findViewById(R.id.sectionTitle)).getText().toString());
        Context c=a;DeveloperFollows follows=new DeveloperFollows(c);c.getSharedPreferences("developer-follows-v1",Context.MODE_PRIVATE).edit().clear().commit();
        follows.toggle("42","fixture-dev","Fixture Developer");assertTrue(new DeveloperFollows(c).contains("42"));
        follows.toggle("42","renamed-dev","Renamed Developer");assertEquals(0,follows.ids().length());
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) public void catalogRendersNativeCardsAndKeepsInstallActionVisible() throws Exception {
        MainActivity a=create(null);java.lang.reflect.Field field=MainActivity.class.getDeclaredField("store");field.setAccessible(true);StoreController store=(StoreController)field.get(a);
        org.json.JSONObject data=new org.json.JSONObject().put("schema","apkdrop.discover.v1").put("total",2).put("page",1).put("pages",1).put("apps",new org.json.JSONArray()
            .put(new org.json.JSONObject().put("slug","pocket-notes").put("name","Pocket Notes").put("description","Deine Gedanken. Lokal auf deinem Android.").put("version","1.2").put("channel","stable").put("developer",new org.json.JSONObject().put("handle","fixture-dev")))
            .put(new org.json.JSONObject().put("slug","quiet-reader").put("name","Quiet Reader").put("description","Lesen ohne Ablenkung.").put("version","2.0").put("channel","stable")));
        store.renderCatalog(data);LinearLayout rows=a.findViewById(R.id.discoverList);assertEquals(2,rows.getChildCount());
        LinearLayout card=(LinearLayout)rows.getChildAt(0);assertTrue(((Button)card.getChildAt(card.getChildCount()-1)).getText().toString().contains("prüfen"));
        String dir=System.getProperty("apkdrop.preview.dir");if(dir!=null){View root=a.findViewById(R.id.pageRoot);root.measure(View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1600,View.MeasureSpec.EXACTLY));root.layout(0,0,720,1600);
            java.io.File file=new java.io.File(dir,"alpha5-discover.png");android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(720,1600,android.graphics.Bitmap.Config.ARGB_8888);root.draw(new android.graphics.Canvas(image));try(java.io.OutputStream out=new java.io.FileOutputStream(file)){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}image.recycle();}
        org.json.JSONObject invalid=new org.json.JSONObject(data.toString()).put("schema","unknown");assertThrows(SecurityException.class,()->store.renderCatalog(invalid));
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) public void largeTextKeepsNavigationInsideWindow() throws Exception {
        RuntimeEnvironment.setFontScale(1.4f);MainActivity a=create(null);a.findViewById(R.id.navSettings).performClick();
        View root=a.findViewById(R.id.pageRoot);root.measure(View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1600,View.MeasureSpec.EXACTLY));root.layout(0,0,720,1600);
        View nav=a.findViewById(R.id.bottomNav);assertTrue(nav.getBottom()<=1600);assertTrue(nav.getHeight()>=96);
        String dir=System.getProperty("apkdrop.preview.dir");if(dir!=null){java.io.File file=new java.io.File(dir,"alpha5-settings-large-text.png");file.getParentFile().mkdirs();
            android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(720,1600,android.graphics.Bitmap.Config.ARGB_8888);root.draw(new android.graphics.Canvas(image));try(java.io.OutputStream out=new java.io.FileOutputStream(file)){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}image.recycle();}
    }
}
