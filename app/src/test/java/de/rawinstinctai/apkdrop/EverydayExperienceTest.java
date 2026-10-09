package de.rawinstinctai.apkdrop;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=26)
public class EverydayExperienceTest {
    private ActivityController<MainActivity> activity;
    @After public void close() {if(activity!=null)activity.destroy();}
    private Context context() {return RuntimeEnvironment.getApplication();}
    private JSONObject catalog(String slug) throws Exception {
        return new JSONObject().put("schema","apkdrop.discover.v1").put("page",1).put("pages",1).put("total",1)
                .put("apps",new JSONArray().put(new JSONObject().put("slug",slug).put("name",slug)));
    }
    private JSONObject release(String version) throws Exception {
        return new JSONObject().put("slug","sample-app").put("channel","stable").put("version",version).put("publishedAt","2026-10-08");
    }
    private StoreController store(MainActivity a) throws Exception {
        java.lang.reflect.Field field=MainActivity.class.getDeclaredField("store");field.setAccessible(true);return (StoreController)field.get(a);
    }
    @Test public void cacheIsBoundedAndSurvivesControllerRecreation() throws Exception {
        context().getSharedPreferences("discover-cache-v1",0).edit().clear().commit();
        CatalogCache cache=new CatalogCache(context());
        for(int i=0;i<6;i++) cache.save("/api/discover?page="+i,catalog("app-"+i));
        assertEquals(4,context().getSharedPreferences("discover-cache-v1",0).getAll().size());
        assertNotNull(new CatalogCache(context()).read("/api/discover?page=5"));
    }
    @Test public void cacheNeverReusesDifferentQueryExpiredOrFutureMetadata() throws Exception {
        long now=1_800_000_000_000L;
        JSONObject envelope=new JSONObject().put("path","one").put("at",now-1000).put("data",catalog("sample-app"));
        assertNotNull(CatalogCache.decode(envelope.toString(),"one",now));
        assertNull(CatalogCache.decode(envelope.toString(),"two",now));
        envelope.put("at",now-CatalogCache.MAX_AGE-1);assertNull(CatalogCache.decode(envelope.toString(),"one",now));
        envelope.put("at",now+1);assertNull(CatalogCache.decode(envelope.toString(),"one",now));
    }
    @Test public void malformedCatalogCannotEnterOfflineCache() throws Exception {
        assertThrows(SecurityException.class,()->new CatalogCache(context()).save("bad",catalog("../outside")));
        assertThrows(SecurityException.class,()->CatalogCache.validate(catalog("sample-app").put("schema","apkdrop.install.v1")));
        assertThrows(SecurityException.class,()->CatalogCache.validate(catalog("sample-app").put("page",2)));
    }
    @Test public void withdrawingCachedCatalogRemovesItsOfflineCopy() throws Exception {
        CatalogCache cache=new CatalogCache(context());cache.save("withdrawn",catalog("sample-app"));
        assertNotNull(cache.read("withdrawn"));cache.remove("withdrawn");assertNull(new CatalogCache(context()).read("withdrawn"));
    }
    @Test public void newReleasesStayUnreadUntilExplicitlyMarkedAndSeparateChannels() throws Exception {
        context().getSharedPreferences("following-read-v1",0).edit().clear().commit();
        FollowReadState state=new FollowReadState(context());JSONObject first=release("1.0");assertTrue(state.unseen(first));
        state.mark(new JSONArray().put(first));assertFalse(new FollowReadState(context()).unseen(first));
        assertTrue(state.unseen(release("1.1")));assertTrue(state.unseen(release("1.0").put("channel","beta")));
        JSONObject renamed=new JSONObject(first.toString()).put("name","New display name");assertFalse(state.unseen(renamed));
        assertTrue(state.unseen(release("1.0").put("publishedAt","2026-10-09")));
    }
    @Test public void readHistoryIsBounded() throws Exception {
        context().getSharedPreferences("following-read-v1",0).edit().clear().commit();FollowReadState state=new FollowReadState(context());
        for(int batch=0;batch<11;batch++){JSONArray items=new JSONArray();for(int i=0;i<60;i++)items.put(release("build-"+(batch*60+i)));state.mark(items);}
        assertEquals(600,context().getSharedPreferences("following-read-v1",0).getAll().size());
        assertFalse(state.unseen(release("build-659")));
    }
    @Test public void homeLinkShortcutOpensVisibleInput() {
        activity=Robolectric.buildActivity(MainActivity.class).create();MainActivity a=activity.get();a.findViewById(R.id.homeAddLink).performClick();
        assertEquals(View.VISIBLE,a.findViewById(R.id.libraryPanel).getVisibility());assertEquals(View.VISIBLE,a.findViewById(R.id.linkPanel).getVisibility());
        assertTrue(a.findViewById(R.id.urlInput).hasFocus());
    }
    @Test public void searchFiltersAndFeedChoiceSurviveRotation() throws Exception {
        Bundle saved=new Bundle();saved.putInt("storeTab",4);saved.putString("discoverQuery","privacy");saved.putString("discoverCategory","privacy");
        saved.putInt("discoverPage",3);saved.putBoolean("following",true);saved.putBoolean("followingNewOnly",true);
        activity=Robolectric.buildActivity(MainActivity.class).create(saved);MainActivity a=activity.get();
        Bundle next=new Bundle();store(a).saveState(next);
        assertEquals("privacy",next.getString("discoverQuery"));assertEquals("privacy",next.getString("discoverCategory"));
        assertEquals(3,next.getInt("discoverPage"));assertTrue(next.getBoolean("following"));assertTrue(next.getBoolean("followingNewOnly"));
        assertEquals("Einstellungen",((TextView)a.findViewById(R.id.sectionTitle)).getText().toString());
    }
    @Test public void sortingUsesNamesWithoutChangingInstallationTarget() throws Exception {
        activity=Robolectric.buildActivity(MainActivity.class).create();MainActivity a=activity.get();StoreController store=store(a);
        JSONObject data=catalog("zulu-app");data.getJSONArray("apps").put(new JSONObject().put("slug","alpha-app").put("name","Alpha"));
        store.renderCatalog(data);a.findViewById(R.id.discoverSort).performClick();
        PopupMenu popup=org.robolectric.shadows.ShadowPopupMenu.getLatestPopupMenu();
        assertNotNull(popup);assertTrue(popup.getMenu().performIdentifierAction(2,0));
        LinearLayout rows=a.findViewById(R.id.discoverList);java.util.List<LinearLayout> apps=new java.util.ArrayList<>();for(int i=0;i<rows.getChildCount();i++)if(rows.getChildAt(i).getTag() instanceof String)apps.add((LinearLayout)rows.getChildAt(i));LinearLayout heading=(LinearLayout)apps.get(0).getChildAt(0);
        assertEquals("alpha-app",apps.get(0).getTag());assertEquals("Alpha",((TextView)heading.getChildAt(1)).getText().toString());assertEquals(2,apps.size());
    }
    @Test public void trustSummaryRequiresActualVerificationAndExposesBlockedReason() {
        InstallContract r=new InstallContract("sample-app","Sample","r1","1.0",1,"dev.sample.app",26,36,List.of(),Set.of(),Set.of("a".repeat(64)),"b".repeat(64),4096,"stable","","","","");
        InstallPolicy.Result decision=new InstallPolicy.Result(InstallPolicy.Mode.INSTALL,"ready",Set.of(),Set.of());
        assertTrue(TrustSummary.describe(r,decision,false).contains("erst nach dem Download"));
        assertFalse(TrustSummary.describe(r,decision,false).contains("✓"));
        assertTrue(TrustSummary.describe(r,decision,true).contains("APK lokal geprüft"));
        decision=new InstallPolicy.Result(InstallPolicy.Mode.BLOCKED,"Signer mismatch",Set.of(),Set.of());
        assertTrue(TrustSummary.describe(r,decision,false).contains("Installation blockiert: Signer mismatch"));
    }
}
