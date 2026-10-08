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
        assertEquals(View.VISIBLE,a.findViewById(R.id.homePanel).getVisibility());
        assertEquals(View.GONE,a.findViewById(R.id.discoverPanel).getVisibility());
        a.findViewById(R.id.navDiscover).performClick();
        assertEquals(View.VISIBLE,a.findViewById(R.id.discoverPanel).getVisibility());assertEquals(View.GONE,a.findViewById(R.id.libraryPanel).getVisibility());
        a.findViewById(R.id.navApps).performClick();assertEquals(View.VISIBLE,a.findViewById(R.id.libraryPanel).getVisibility());assertEquals(View.GONE,a.findViewById(R.id.settingsPanel).getVisibility());
        a.findViewById(R.id.navUpdates).performClick();assertEquals("Updates",((TextView)a.findViewById(R.id.sectionTitle)).getText().toString());
        a.findViewById(R.id.navSettings).performClick();assertEquals(View.VISIBLE,a.findViewById(R.id.settingsPanel).getVisibility());assertEquals(View.GONE,a.findViewById(R.id.libraryPanel).getVisibility());
    }
    @Test public void restoresSelectedTabAndFollowsWithoutAccount() throws Exception {
        Bundle saved=new Bundle();saved.putInt("storeTab",4);MainActivity a=create(saved);
        assertEquals("Einstellungen",((TextView)a.findViewById(R.id.sectionTitle)).getText().toString());
        Context c=a;DeveloperFollows follows=new DeveloperFollows(c);c.getSharedPreferences("developer-follows-v1",Context.MODE_PRIVATE).edit().clear().commit();
        follows.toggle("42","fixture-dev","Fixture Developer");assertTrue(new DeveloperFollows(c).contains("42"));
        follows.toggle("42","renamed-dev","Renamed Developer");assertEquals(0,follows.ids().length());
    }
    @Test public void activityRestartRestoresSelectedTab() {
        MainActivity a=create(null);
        a.findViewById(R.id.navSettings).performClick();
        controller.recreate();
        MainActivity restored=controller.get();
        assertEquals("Einstellungen",((TextView)restored.findViewById(R.id.sectionTitle)).getText().toString());
        assertEquals(View.VISIBLE,restored.findViewById(R.id.settingsPanel).getVisibility());
    }
    @Test public void homeProvidesRealEmptyStateAndShortcuts() {
        MainActivity a=create(null);
        assertEquals("0",((TextView)a.findViewById(R.id.homeAppCount)).getText().toString());
        ((Button)a.findViewById(R.id.homeUpdates)).performClick();
        assertEquals("Entdecken",((TextView)a.findViewById(R.id.sectionTitle)).getText().toString());
        a.findViewById(R.id.navHome).performClick();
        a.findViewById(R.id.homeLibrary).performClick();
        assertEquals("Meine Apps",((TextView)a.findViewById(R.id.sectionTitle)).getText().toString());
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    @Test public void homeBlockedStateIsNotReportedAsCurrent() throws Exception {
        MainActivity a=create(null);
        java.lang.reflect.Field controllerField=MainActivity.class.getDeclaredField("library");
        controllerField.setAccessible(true);
        AppLibraryController controller=(AppLibraryController)controllerField.get(a);
        AppLibrary.Entry entry=new AppLibrary.Entry("sample-app","Sample","dev.sample.app",java.util.Set.of("a".repeat(64)));
        java.lang.reflect.Field libraryField=AppLibraryController.class.getDeclaredField("library");
        libraryField.setAccessible(true);
        libraryField.set(controller,new AppLibrary().add(entry));
        InstallPolicy.Result blocked=new InstallPolicy.Result(InstallPolicy.Mode.BLOCKED,"Signer mismatch",java.util.Set.of(),java.util.Set.of());
        Class<?> stateClass=Class.forName("de.rawinstinctai.apkdrop.AppLibraryController$State");
        java.lang.reflect.Constructor<?> constructor=stateClass.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object state=constructor.newInstance(null,null,blocked,null,true,System.currentTimeMillis());
        java.lang.reflect.Field statesField=AppLibraryController.class.getDeclaredField("states");
        statesField.setAccessible(true);
        ((java.util.Map<String,Object>)statesField.get(controller)).put(entry.slug,state);
        assertTrue(controller.homeStatus().contains("Prüfproblemen"));
        assertFalse(controller.homeStatus().contains("laut letzter Prüfung aktuell"));
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    @Test public void homeCachedUpdateIsMarkedAsLastKnown() throws Exception {
        MainActivity a=create(null);
        java.lang.reflect.Field controllerField=MainActivity.class.getDeclaredField("library");
        controllerField.setAccessible(true);
        AppLibraryController controller=(AppLibraryController)controllerField.get(a);
        AppLibrary.Entry entry=new AppLibrary.Entry("sample-app","Sample","dev.sample.app",java.util.Set.of("a".repeat(64)));
        java.lang.reflect.Field libraryField=AppLibraryController.class.getDeclaredField("library");
        libraryField.setAccessible(true);
        libraryField.set(controller,new AppLibrary().add(entry));
        InstallPolicy.Result update=new InstallPolicy.Result(InstallPolicy.Mode.UPDATE,"update",java.util.Set.of(),java.util.Set.of());
        Class<?> stateClass=Class.forName("de.rawinstinctai.apkdrop.AppLibraryController$State");
        java.lang.reflect.Constructor<?> constructor=stateClass.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object state=constructor.newInstance(null,null,update,null,false,System.currentTimeMillis());
        java.lang.reflect.Field cachedField=stateClass.getDeclaredField("cached");
        cachedField.setAccessible(true); cachedField.setBoolean(state,true);
        java.lang.reflect.Field statesField=AppLibraryController.class.getDeclaredField("states");
        statesField.setAccessible(true);
        ((java.util.Map)statesField.get(controller)).put(entry.slug,state);
        assertTrue(controller.homeStatus().contains("laut gespeichertem Prüfstand"));
        assertTrue(controller.homeStatus().contains("vor dem Download wird frisch geprüft"));
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
