package de.rawinstinctai.apkdrop;

import android.app.AlertDialog;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowAlertDialog;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=26,qualifiers="w360dp-h800dp-xhdpi",shadows={Alpha17OnboardingTest.Radar.class,Alpha17OnboardingTest.Connection.class})
public class Alpha17OnboardingTest {
    private ActivityController<MainActivity> controller;
    static final List<String> paths=new CopyOnWriteArrayList<>();
    static final List<JSONObject> bodies=new CopyOnWriteArrayList<>();
    @Before public void setup(){paths.clear();bodies.clear();Connection.saved=new JSONObject();RuntimeEnvironment.getApplication().getSharedPreferences("github-radar-v1",0).edit().clear().commit();}
    @After public void close(){if(controller!=null)controller.destroy();RuntimeEnvironment.setFontScale(1f);}
    private MainActivity create(){controller=Robolectric.buildActivity(MainActivity.class).create();return controller.get();}
    private StoreController store(MainActivity a)throws Exception{return (StoreController)field(a,"store");}
    private Object field(Object target,String name)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private GitHubRadarController radar(MainActivity a)throws Exception{return (GitHubRadarController)field(store(a),"radar");}
    private void invoke(Object target,String name,Class<?> type,Object value)throws Exception{var m=target.getClass().getDeclaredMethod(name,type);m.setAccessible(true);m.invoke(target,value);}
    private void start(GitHubRadarController r)throws Exception{var m=GitHubRadarController.class.getDeclaredMethod("start");m.setAccessible(true);m.invoke(r);}
    private void connect(GitHubRadarController r,boolean imports)throws Exception{var f=GitHubRadarController.class.getDeclaredField("connection");f.setAccessible(true);f.set(r,new JSONObject().put("connected",true).put("login","fixture-dev").put("expires",System.currentTimeMillis()/1000+3600).put("deviceSecret","a".repeat(64)).put("privateImport",imports));}
    private void waitFor(java.util.function.BooleanSupplier condition)throws Exception{long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(System.nanoTime()<until){Shadows.shadowOf(Looper.getMainLooper()).idle();if(condition.getAsBoolean())return;Thread.sleep(5);}fail("Expected async radar state did not arrive.");}
    private CheckBox checkbox(View root){if(root instanceof CheckBox c)return c;if(root instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){CheckBox c=checkbox(g.getChildAt(i));if(c!=null)return c;}return null;}
    private boolean contains(View root,String text){if(root instanceof TextView t&&t.getText().toString().contains(text))return true;if(root instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++)if(contains(g.getChildAt(i),text))return true;return false;}
    @Test public void canceledOnboardingMakesNoPairingRequest()throws Exception{
        MainActivity a=create();GitHubRadarController r=radar(a);start(r);AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();
        assertFalse(checkbox(dialog.getWindow().getDecorView()).isChecked());assertTrue(contains(dialog.getWindow().getDecorView(),"All repositories"));
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();assertFalse(paths.contains("/api/companion/pair"));assertFalse(r.connected());
    }
    @Test public void privateImportPermissionIsAnExplicitPairingChoice()throws Exception{
        MainActivity a=create();start(radar(a));AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();
        checkbox(dialog.getWindow().getDecorView()).setChecked(true);dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();waitFor(()->paths.contains("/api/companion/pair"));
        assertTrue(bodies.get(paths.indexOf("/api/companion/pair")).getBoolean("privateImport"));
    }
    @Test public void defaultPairingRequestsReadOnlyAccess()throws Exception{
        MainActivity a=create();start(radar(a));ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();waitFor(()->paths.contains("/api/companion/pair"));
        assertFalse(bodies.get(paths.indexOf("/api/companion/pair")).getBoolean("privateImport"));
    }
    private void pendingUpgrade(GitHubRadarController r)throws Exception{
        connect(r,false);start(r);AlertDialog d=ShadowAlertDialog.getLatestAlertDialog();checkbox(d.getWindow().getDecorView()).setChecked(true);d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();waitFor(()->Connection.saved.has("previous"));
    }
    @Test public void cancelingConsentUpgradeRestoresTheWorkingConnectionAcrossRestart()throws Exception{
        MainActivity a=create();GitHubRadarController r=radar(a);pendingUpgrade(r);invoke(r,"cancelPairing",String.class,"Bestätigung abgebrochen.");
        assertTrue(r.connected());assertEquals("a".repeat(64),Connection.saved.getString("deviceSecret"));assertFalse(Connection.saved.getBoolean("privateImport"));
        controller.destroy();a=create();assertTrue(radar(a).connected());assertFalse(((JSONObject)field(radar(a),"connection")).getBoolean("privateImport"));
    }
    @Test public void expiredUpgradeRestoresThePreviousConnectionAfterRestart()throws Exception{
        MainActivity a=create();pendingUpgrade(radar(a));controller.destroy();Connection.saved.put("pairExpires",0);a=create();radar(a).resume();
        assertTrue(radar(a).connected());assertEquals("a".repeat(64),Connection.saved.getString("deviceSecret"));assertFalse(Connection.saved.has("previous"));
    }
    @Test public void freshPrivatePreviewNeedsConfirmationAndCannotPublishOrInstall()throws Exception{
        MainActivity a=create();GitHubRadarController r=radar(a);connect(r,true);JSONObject suggestion=new JSONObject().put("fullName","fixture-dev/new-app").put("version","old").put("private",false);
        invoke(r,"preview",JSONObject.class,suggestion);waitFor(()->ShadowAlertDialog.getLatestAlertDialog()!=null&&contains(ShadowAlertDialog.getLatestAlertDialog().getWindow().getDecorView(),"v2.0"));
        AlertDialog d=ShadowAlertDialog.getLatestAlertDialog();assertTrue(contains(d.getWindow().getDecorView(),"Repository: Privat"));d.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();assertFalse(paths.contains("/api/companion/import"));
        invoke(r,"preview",JSONObject.class,suggestion);waitFor(()->ShadowAlertDialog.getLatestAlertDialog()!=d);
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();waitFor(()->paths.contains("/api/companion/import"));
        JSONObject sent=bodies.get(paths.indexOf("/api/companion/import"));assertEquals("v2.0",sent.getString("version"));assertEquals("fresh.apk",sent.getString("filename"));assertTrue(sent.getBoolean("importConsent"));assertFalse(sent.has("publish"));assertNull(Shadows.shadowOf(a).getNextStartedActivity());
    }
    @Test public void readOnlyConnectionCannotImport()throws Exception{
        MainActivity a=create();GitHubRadarController r=radar(a);connect(r,false);invoke(r,"preview",JSONObject.class,new JSONObject().put("fullName","fixture-dev/new-app"));
        waitFor(()->ShadowAlertDialog.getLatestAlertDialog()!=null);assertEquals("Privaten Import freigeben",Shadows.shadowOf(ShadowAlertDialog.getLatestAlertDialog()).getTitle().toString());assertFalse(paths.contains("/api/companion/import"));
    }
    @Test public void invalidOrIncompletePreviewCannotBecomeImportConsent()throws Exception{
        for(JSONObject selected:List.of(new JSONObject().put("filename","fresh.apk").put("version","2.0"),new JSONObject().put("filename","../fresh.apk").put("version","2.0").put("size",4000),new JSONObject().put("filename","fresh.apk").put("version","2.0").put("size",InstallContract.MAX_BYTES+1)))
            assertThrows(Exception.class,()->ImportPreview.parse("fixture-dev/new-app",new JSONObject().put("state","ready").put("repository",new JSONObject()).put("selection",selected)));
        JSONObject data=preview();ImportPreview p=ImportPreview.parse("fixture-dev/new-app",data);data.getJSONObject("selection").put("version","changed");assertEquals("v2.0",p.request().getString("version"));
    }
    @Test public void descriptionExpandsAndClearsWithoutAuthorizingInstall()throws Exception{
        MainActivity a=create();StoreController s=store(a);String longText="Beschreibung vom Entwickler. ".repeat(50);s.renderReleaseMetadata("sample-app",new JSONObject().put("schema","apkdrop.store.v1").put("slug","sample-app").put("description",longText));
        LinearLayout panel=a.findViewById(R.id.appDescriptionPanel);TextView text=(TextView)panel.getChildAt(1);assertEquals(4,text.getMaxLines());((Button)panel.getChildAt(2)).performClick();assertEquals(Integer.MAX_VALUE,text.getMaxLines());assertEquals(longText.trim(),text.getText().toString());
        assertEquals(View.GONE,a.findViewById(R.id.actionButton).getVisibility());
        assertThrows(SecurityException.class,()->s.renderReleaseMetadata("sample-app",new JSONObject().put("schema","apkdrop.store.v1").put("slug","other-app")));
        s.clearReleaseDetails();assertEquals(View.GONE,panel.getVisibility());assertEquals(0,panel.getChildCount());
    }
    @Test public void repositoryRootSupportsOnlyVerifiedPublicMapping()throws Exception{
        assertEquals("fixture-dev/new-app",LinkImport.repository("https://github.com/Fixture-Dev/new-app/"));
        assertThrows(SecurityException.class,()->LinkImport.match("fixture-dev/new-app",new JSONObject().put("schema","apkdrop.dropid.v1").put("published",false)));
        for(String bad:List.of("https://github.com/dev/repo/tree/main","https://github.com/dev/repo?token=secret","https://github.com/dev/repo#readme","https://github.com/dev/.."))assertThrows(IllegalArgumentException.class,()->LinkImport.repository(bad));
    }
    @Test public void emptyPublicProfileExplainsWherePrivateDraftsStay()throws Exception{MainActivity a=create();store(a).renderPublishedApps(new JSONArray());assertTrue(contains(a.findViewById(R.id.discoverList),"noch keine öffentlichen Apps"));assertTrue(contains(a.findViewById(R.id.discoverList),"Private Entwürfe"));}
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk=35,qualifiers="w320dp-h640dp-xhdpi")
    public void narrowLargeFontConsentAndDescriptionPreview()throws Exception{
        RuntimeEnvironment.setFontScale(1.5f);MainActivity a=create();a.findViewById(R.id.homeGitHubRadar).performClick();start(radar(a));AlertDialog d=ShadowAlertDialog.getLatestAlertDialog();render(d.getWindow().getDecorView(),"alpha17-consent-large",640,1280);assertFalse(checkbox(d.getWindow().getDecorView()).isChecked());d.dismiss();
        store(a).showDetail();a.findViewById(R.id.releaseCard).setVisibility(View.VISIBLE);((TextView)a.findViewById(R.id.titleText)).setText("Pocket Notes");store(a).renderReleaseMetadata("sample-app",new JSONObject().put("schema","apkdrop.store.v1").put("slug","sample-app").put("description","Private Notizen und Aufgaben direkt vom Entwickler. Deine Einträge bleiben auf deinem Gerät. ".repeat(8)));render(a.findViewById(R.id.pageRoot),"alpha17-description-large",640,1280);
    }
    private void render(View root,String name,int width,int height)throws Exception{root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));root.layout(0,0,width,height);String dir=System.getProperty("apkdrop.preview.dir");if(dir==null)return;java.io.File file=new java.io.File(dir,name+".png");file.getParentFile().mkdirs();android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(width,height,android.graphics.Bitmap.Config.ARGB_8888);root.draw(new android.graphics.Canvas(image));try(var out=new java.io.FileOutputStream(file)){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}image.recycle();}
    static JSONObject preview()throws Exception{return new JSONObject().put("state","ready").put("repository",new JSONObject().put("name","Fresh App").put("description","Fresh description").put("private",true)).put("selection",new JSONObject().put("version","v2.0").put("filename","fresh.apk").put("size",4096).put("prerelease",false));}
    @Implements(value=RadarConnection.class,isInAndroidSdk=false) public static class Connection {
        static volatile JSONObject saved=new JSONObject();
        @Implementation protected JSONObject read()throws Exception{return new JSONObject(saved.toString());}
        @Implementation protected void save(JSONObject data)throws Exception{saved=new JSONObject(data.toString());}
        @Implementation protected void clear(){saved=new JSONObject();}
    }
    @Implements(value=RadarClient.class,isInAndroidSdk=false) public static class Radar {
        @Implementation protected static JSONObject request(String path,JSONObject body,String token)throws Exception{
            bodies.add(body==null?new JSONObject():new JSONObject(body.toString()));paths.add(path);
            if(path.equals("/api/companion/import/preview"))return preview();
            if(path.equals("/api/companion/pair"))return new JSONObject().put("id","b".repeat(32)).put("expires",System.currentTimeMillis()/1000+600);
            if(path.equals("/api/companion/apps"))return new JSONObject().put("apps",new JSONArray());
            return new JSONObject();
        }
    }
}
