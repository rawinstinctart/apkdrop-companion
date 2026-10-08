package de.rawinstinctai.apkdrop;

import android.view.View;
import android.widget.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ActivityController;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=26,qualifiers="w360dp-h800dp-xhdpi")
public class PremiumExperienceTest {
    private ActivityController<MainActivity> controller;
    @Before public void stableAnimationClock() throws Exception {
        java.lang.reflect.Method scale=android.animation.ValueAnimator.class.getDeclaredMethod("setDurationScale",float.class);
        scale.setAccessible(true);scale.invoke(null,0f);
    }
    @After public void close(){if(controller!=null)controller.destroy();RuntimeEnvironment.setFontScale(1f);}
    private MainActivity create(){controller=Robolectric.buildActivity(MainActivity.class).create();return controller.get();}
    private JSONObject profile() throws Exception {
        return new JSONObject().put("schema","apkdrop.dropid.v1").put("published",true).put("handle","fixture-dev")
                .put("github",new JSONObject().put("status","verified"))
                .put("apps",new JSONArray().put(new JSONObject().put("slug","sample-app").put("repo","fixture-dev/messenger")
                        .put("repository",new JSONObject().put("status","verified"))));
    }
    @Test public void githubReleaseLinksNeverBecomeGuessedSlugs() throws Exception {
        assertEquals("fixture-dev/messenger",LinkImport.repository("Hier ist die App: https://github.com/fixture-dev/messenger/releases/tag/v1.2"));
        assertEquals("sample-app",LinkImport.match("fixture-dev/messenger",profile()));
        assertEquals("fixture-dev/messenger",LinkImport.repository("https://github.com/Fixture-Dev/Messenger/releases/download/v1.2/app.apk"));
        for(String bad:new String[]{"http://github.com/fixture-dev/messenger/releases","https://github.com.evil.test/fixture-dev/messenger/releases",
            "https://evil@github.com/fixture-dev/messenger/releases","https://github.com/fixture-dev/messenger/releases?token=secret",
            "https://github.com/fixture-dev/messenger/releases#x","https://github.com/fixture-dev/messenger/releases/download/v1/a%2fb.apk",
            "https://github.com/fixture-dev/messenger/issues/1","https://github.com/fixture-dev/messenger/releases https://github.com/fixture-dev/other/releases"})
            assertThrows(bad,IllegalArgumentException.class,()->LinkImport.repository(bad));
    }
    @Test public void withdrawnExpiredWrongAndAmbiguousRepositoriesFailClosed() throws Exception {
        JSONObject p=profile();p.put("published",false);assertThrows(SecurityException.class,()->LinkImport.match("fixture-dev/messenger",p));
        JSONObject expired=profile();expired.getJSONArray("apps").getJSONObject(0).getJSONObject("repository").put("status","expired");
        assertThrows(SecurityException.class,()->LinkImport.match("fixture-dev/messenger",expired));
        JSONObject wrong=profile();assertThrows(SecurityException.class,()->LinkImport.match("fixture-dev/other",wrong));
        JSONObject ambiguous=profile();ambiguous.getJSONArray("apps").put(new JSONObject(ambiguous.getJSONArray("apps").getJSONObject(0).toString()).put("slug","other-app"));
        assertThrows(SecurityException.class,()->LinkImport.match("fixture-dev/messenger",ambiguous));
    }
    @Test public void updateActionLivesInUpdatesAndCategoriesSurviveRecreation() throws Exception {
        MainActivity a=create();a.findViewById(R.id.navApps).performClick();assertEquals(View.GONE,a.findViewById(R.id.checkAllButton).getVisibility());
        a.findViewById(R.id.navUpdates).performClick();assertEquals(View.VISIBLE,a.findViewById(R.id.checkAllButton).getVisibility());
        a.findViewById(R.id.navDiscover).performClick();
        LinearLayout chips=a.findViewById(R.id.categoryChips);chips.getChildAt(4).performClick();
        a.findViewById(R.id.navUpdates).performClick();
        android.os.Bundle saved=new android.os.Bundle();controller.saveInstanceState(saved).destroy();
        controller=Robolectric.buildActivity(MainActivity.class).create(saved);a=controller.get();chips=a.findViewById(R.id.categoryChips);
        assertTrue(chips.getChildAt(4).isSelected());assertEquals(9,chips.getChildCount());
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) public void nativeScreensKeepCardsVisibleAndRenderReviewImages() throws Exception {
        MainActivity a=create();
        java.lang.reflect.Field field=MainActivity.class.getDeclaredField("library");field.setAccessible(true);AppLibraryController library=(AppLibraryController)field.get(a);
        InstallContract release=new InstallContract("sample-app","Pocket Notes","r1","1.2",12,"dev.sample.app",26,36,List.of(),Set.of(),Set.of("a".repeat(64)),"b".repeat(64),4096,"stable","Mehr Ruhe für deine Gedanken.","","","");
        library.add(release,null,new InstallPolicy.Result(InstallPolicy.Mode.INSTALL,"ready",Set.of(),Set.of()));
        assertFalse(library.homeStatus().contains("Apps laut letzter Prüfung aktuell"));
        assertTrue(library.homeStatus().contains("noch nicht installiert"));
        render(a,"alpha8-home");
        android.os.Bundle state=new android.os.Bundle();state.putInt("storeTab",1);
        java.lang.reflect.Field f=MainActivity.class.getDeclaredField("store");f.setAccessible(true);StoreController store=(StoreController)f.get(a);store.restoreState(state);
        JSONObject data=new JSONObject().put("schema","apkdrop.discover.v1").put("total",2).put("page",1).put("pages",1)
            .put("apps",new JSONArray().put(new JSONObject().put("slug","pocket-notes").put("name","Pocket Notes").put("description","Deine Gedanken. Lokal auf deinem Android.").put("category","productivity").put("version","1.2").put("channel","stable").put("developer",new JSONObject().put("handle","fixture-dev")))
            .put(new JSONObject().put("slug","quiet-reader").put("name","Quiet Reader").put("category","tools").put("description","Lesen ohne Ablenkung.").put("version","2.0").put("channel","stable")));
        store.renderCatalog(data);render(a,"alpha8-discover");
        View list=a.findViewById(R.id.discoverList);assertTrue("First app must be above the fold",list.getTop()<900);
        a.findViewById(R.id.navApps).performClick();render(a,"alpha8-library");
        View nav=a.findViewById(R.id.bottomNav);assertTrue(nav.getBottom()<=1600);
    }
    private void render(MainActivity a,String name) throws Exception {
        View root=a.findViewById(R.id.pageRoot);root.measure(View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1600,View.MeasureSpec.EXACTLY));root.layout(0,0,720,1600);
        String dir=System.getProperty("apkdrop.preview.dir");if(dir==null)return;
        java.io.File file=new java.io.File(dir,name+".png");file.getParentFile().mkdirs();android.graphics.Bitmap b=android.graphics.Bitmap.createBitmap(720,1600,android.graphics.Bitmap.Config.ARGB_8888);root.draw(new android.graphics.Canvas(b));try(java.io.OutputStream out=new java.io.FileOutputStream(file)){b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}b.recycle();
    }
}
