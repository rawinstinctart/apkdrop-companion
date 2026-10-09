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
public class Alpha14RadarTest {
    private ActivityController<MainActivity> controller;
    @After public void close(){if(controller!=null)controller.destroy();}
    private MainActivity create(){controller=Robolectric.buildActivity(MainActivity.class).create();return controller.get();}
    private JSONObject release(long version) throws Exception {return new JSONObject().put("slug","sample-app").put("packageName","dev.sample.app").put("versionCode",version).put("signers",new JSONArray().put("a".repeat(64)));}
    private InstalledState installed(long version){return new InstalledState("dev.sample.app",version,"1.2",Set.of("a".repeat(64)),Set.of());}
    @Test public void radarUsesNumericVersionAndExactSignersInsteadOfDisplayVersion() throws Exception {
        assertEquals(ReleaseRadar.State.INSTALLED,ReleaseRadar.compare(release(12),installed(12)));
        assertEquals(ReleaseRadar.State.INSTALLED,ReleaseRadar.compare(release(11),installed(12)));
        assertEquals(ReleaseRadar.State.UPDATE,ReleaseRadar.compare(release(13),installed(12)));
        assertEquals(ReleaseRadar.State.NOT_INSTALLED,ReleaseRadar.compare(release(12),null));
        assertEquals(ReleaseRadar.State.DIFFERENT_SIGNER,ReleaseRadar.compare(release(12),new InstalledState("dev.sample.app",12,Set.of("b".repeat(64)),Set.of())));
        assertEquals(ReleaseRadar.State.UNKNOWN,ReleaseRadar.compare(release(12).put("signers",new JSONArray()),installed(12)));
        assertEquals(ReleaseRadar.State.UNKNOWN,ReleaseRadar.compare(release(12).put("versionCode",0),installed(12)));
        assertEquals("Bereits installiert",ReleaseRadar.label(ReleaseRadar.State.INSTALLED,true));
    }
    @Test public void pairingAndImportUrlsRejectRedirectsAndPathInjection(){
        assertEquals("/companion/connect?code="+"a".repeat(32),RadarClient.verificationPath("a".repeat(32)));
        assertTrue(RadarClient.importPath("dev/new-repo").startsWith("/onboarding?repo=https%3A%2F%2Fgithub.com%2F"));
        for(String bad:new String[]{"dev/repo?token=secret","https://evil.test/repo","dev/../repo","dev/repo#frag"})assertThrows(SecurityException.class,()->RadarClient.importPath(bad));
        assertThrows(SecurityException.class,()->RadarClient.verificationPath("a".repeat(32)+"&next=https://evil.test"));
    }
    @Test public void githubRadarIsOptionalAndHasNoImplicitLogin(){
        MainActivity a=create();a.findViewById(R.id.homeGitHubRadar).performClick();
        assertEquals(View.VISIBLE,a.findViewById(R.id.githubRadarPanel).getVisibility());assertEquals(View.GONE,a.findViewById(R.id.discoverControls).getVisibility());
        LinearLayout panel=a.findViewById(R.id.githubRadarPanel);boolean connect=false;for(int i=0;i<panel.getChildCount();i++)if(panel.getChildAt(i) instanceof Button b&&b.getText().toString().equals("GitHub verbinden →"))connect=true;
        assertTrue(connect);assertEquals(a.getColor(R.color.lime_dark),((Button)a.findViewById(R.id.radarGitHub)).getCurrentTextColor());a.findViewById(R.id.radarApps).performClick();assertEquals(View.GONE,panel.getVisibility());assertEquals(View.VISIBLE,a.findViewById(R.id.discoverControls).getVisibility());
    }
    @Test public void preparedInstalledStatesAreReusedWithoutPackageLookupsDuringRender() throws Exception {
        MainActivity a=create();java.lang.reflect.Field field=MainActivity.class.getDeclaredField("store");field.setAccessible(true);Object store=field.get(a);
        java.lang.reflect.Method prepare=StoreController.class.getDeclaredMethod("prepareRadar",JSONArray.class),state=StoreController.class.getDeclaredMethod("radarState",JSONObject.class);prepare.setAccessible(true);state.setAccessible(true);
        JSONObject candidate=release(12);assertEquals(ReleaseRadar.State.UNKNOWN,state.invoke(store,candidate));
        Thread worker=new Thread(()->{try{prepare.invoke(store,new JSONArray().put(candidate).put(candidate));}catch(Exception e){throw new AssertionError(e);}});worker.start();worker.join();
        assertEquals(ReleaseRadar.State.NOT_INSTALLED,state.invoke(store,candidate));assertEquals(ReleaseRadar.State.NOT_INSTALLED,state.invoke(store,candidate));
    }
    @Test public void librarySearchFiltersOnlyRowsAndSurvivesRotation() throws Exception {
        MainActivity a=create();java.lang.reflect.Field f=MainActivity.class.getDeclaredField("library");f.setAccessible(true);AppLibraryController l=(AppLibraryController)f.get(a);
        for(String name:new String[]{"Pocket Notes","Quiet Reader"}){String slug=name.startsWith("Pocket")?"pocket-notes":"quiet-reader";InstallContract r=new InstallContract(slug,name,"r1","1.2",12,"dev."+slug.replace('-','_'),26,36,List.of(),Set.of(),Set.of("a".repeat(64)),"b".repeat(64),4096,"stable","","","","");l.add(r,null,new InstallPolicy.Result(InstallPolicy.Mode.INSTALL,"ready",Set.of(),Set.of()));}
        a.findViewById(R.id.navApps).performClick();((EditText)a.findViewById(R.id.librarySearch)).setText("quiet");assertEquals(1,((LinearLayout)a.findViewById(R.id.libraryList)).getChildCount());assertEquals(2,l.count());
        android.os.Bundle saved=new android.os.Bundle();controller.saveInstanceState(saved).destroy();controller=Robolectric.buildActivity(MainActivity.class).create(saved);a=controller.get();assertEquals("quiet",((EditText)a.findViewById(R.id.librarySearch)).getText().toString());
        a.findViewById(R.id.navUpdates).performClick();assertEquals(View.GONE,a.findViewById(R.id.librarySearch).getVisibility());
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) public void nativeSmartHomeAndRadarPreview() throws Exception {
        MainActivity a=create();java.lang.reflect.Field f=MainActivity.class.getDeclaredField("library");f.setAccessible(true);AppLibraryController l=(AppLibraryController)f.get(a);
        InstallContract r=new InstallContract("sample-app","Pocket Notes","r1","1.2",12,"dev.sample.app",26,36,List.of(),Set.of(),Set.of("a".repeat(64)),"b".repeat(64),4096,"stable","","","","");
        l.add(r,installed(12),new InstallPolicy.Result(InstallPolicy.Mode.CURRENT,"ready",Set.of(),Set.of()));
        assertEquals("Dein App-Radar.",((TextView)a.findViewById(R.id.homeHeadline)).getText().toString());render(a,"alpha14-home");
        a.findViewById(R.id.navUpdates).performClick();assertTrue(((TextView)a.findViewById(R.id.libraryEmpty)).getText().toString().contains("letzter Prüfung"));render(a,"alpha14-updates");
        a.findViewById(R.id.homeGitHubRadar).performClick();render(a,"alpha14-github-radar");
        a.findViewById(R.id.navSettings).performClick();render(a,"alpha14-settings");
    }
    private void render(MainActivity a,String name) throws Exception {
        View root=a.findViewById(R.id.pageRoot);root.measure(View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1600,View.MeasureSpec.EXACTLY));root.layout(0,0,720,1600);
        String dir=System.getProperty("apkdrop.preview.dir");if(dir==null)return;java.io.File file=new java.io.File(dir,name+".png");file.getParentFile().mkdirs();android.graphics.Bitmap b=android.graphics.Bitmap.createBitmap(720,1600,android.graphics.Bitmap.Config.ARGB_8888);root.draw(new android.graphics.Canvas(b));try(java.io.OutputStream out=new java.io.FileOutputStream(file)){b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}b.recycle();
    }
}
