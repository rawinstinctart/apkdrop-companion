package de.rawinstinctai.apkdrop;

import android.view.View;
import android.widget.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=26)
public class Alpha16ExperienceTest {
    private ActivityController<MainActivity> activity;

    @After public void close() {
        if(activity!=null)activity.destroy();
    }
    private StoreController store(MainActivity a) throws Exception {
        var field=MainActivity.class.getDeclaredField("store");
        field.setAccessible(true);
        return (StoreController)field.get(a);
    }
    private JSONObject catalog(String querySlug) throws Exception {
        return new JSONObject().put("schema","apkdrop.discover.v1").put("total",1)
            .put("page",1).put("pages",1)
            .put("apps",new JSONArray().put(new JSONObject()
                .put("slug",querySlug).put("name","Sichere App")
                .put("description","Eine unabhängige Android-App.")
                .put("category","privacy").put("version","1.0")));
    }
    @Test public void discoverHasDistinctCollectionHeaderAndCleanCard() throws Exception {
        activity=Robolectric.buildActivity(MainActivity.class).create();
        MainActivity a=activity.get();
        StoreController s=store(a);
        s.renderCatalog(catalog("alpha16-demo"));
        LinearLayout list=a.findViewById(R.id.discoverList);
        assertEquals("Apps für dich",((TextView)list.getChildAt(0)).getText().toString());
        assertEquals("Aktuelle Releases zuerst",((TextView)list.getChildAt(1)).getText().toString());
        assertEquals(View.GONE,a.findViewById(R.id.categoryScroll).getVisibility());
        assertEquals(View.GONE,a.findViewById(R.id.radarReleases).getVisibility());
        assertEquals(View.GONE,a.findViewById(R.id.radarGitHub).getVisibility());
        assertEquals(View.VISIBLE,a.findViewById(R.id.discoverFilter).getVisibility());
        LinearLayout card=null;
        for(int i=0;i<list.getChildCount();i++) {
            View v=list.getChildAt(i);
            if("alpha16-demo".equals(v.getTag()))card=(LinearLayout)v;
        }
        assertNotNull(card);
        assertEquals("Details ansehen & prüfen →",((Button)card.getChildAt(card.getChildCount()-1)).getText().toString());
        LinearLayout heading=(LinearLayout)card.getChildAt(0);
        assertEquals("Sichere App",((TextView)heading.getChildAt(1)).getText().toString());
        assertEquals("1 App",((TextView)a.findViewById(R.id.discoverCount)).getText().toString());
        assertEquals(View.GONE,a.findViewById(R.id.discoverRefresh).getVisibility());
        assertEquals(View.GONE,a.findViewById(R.id.discoverSort).getVisibility());
    }
    @Test public void emptySearchShowsRecoveryWithoutFictitiousApps() throws Exception {
        activity=Robolectric.buildActivity(MainActivity.class).create();
        MainActivity a=activity.get();
        StoreController s=store(a);
        JSONObject empty=new JSONObject().put("schema","apkdrop.discover.v1")
            .put("total",0).put("page",1).put("pages",1)
            .put("apps",new JSONArray());
        s.renderCatalog(empty);
        LinearLayout list=a.findViewById(R.id.discoverList);
        boolean hasGuidance=false,hasNextStep=false;
        for(int i=0;i<list.getChildCount();i++) {
            View row=list.getChildAt(i);
            if(!(row instanceof LinearLayout))continue;
            LinearLayout card=(LinearLayout)row;
            for(int j=0;j<card.getChildCount();j++) {
                View child=card.getChildAt(j);
                if(child instanceof TextView && ((TextView)child).getText().toString().contains("Noch keine Apps in dieser Auswahl"))
                    hasGuidance=true;
                if(child instanceof Button && ((Button)child).getText().toString().contains("App-Link hinzufügen"))
                    hasNextStep=true;
            }
        }
        assertTrue(hasGuidance);
        assertTrue(hasNextStep);
        assertEquals("0 Apps",((TextView)a.findViewById(R.id.discoverCount)).getText().toString());
    }
    @Test public void bottomNavigationLabelsMayWrapForLargeSystemFonts() {
        activity=Robolectric.buildActivity(MainActivity.class).create();
        MainActivity a=activity.get();
        for(int id:new int[]{R.id.navHome,R.id.navDiscover,R.id.navApps,R.id.navUpdates,R.id.navSettings}) {
            Button button=a.findViewById(id);
            assertEquals(2,button.getMaxLines());
            assertTrue(button.getMinHeight()>=68);
            assertNotNull(button.getContentDescription()==null?button.getText():button.getContentDescription());
        }
    }
    @Test public void categoryChipsAllowLargerTextInsteadOfFixedHeight() throws Exception {
        activity=Robolectric.buildActivity(MainActivity.class).create();
        MainActivity a=activity.get();
        LinearLayout chips=a.findViewById(R.id.categoryChips);
        assertTrue(chips.getChildCount()>1);
        Button first=(Button)chips.getChildAt(0);
        assertEquals(-2,first.getLayoutParams().height);
        assertTrue(first.getMinHeight()>=48);
        assertEquals(100,((EditText)a.findViewById(R.id.discoverInput)).getFilters().length>0?100:0);
    }
}
