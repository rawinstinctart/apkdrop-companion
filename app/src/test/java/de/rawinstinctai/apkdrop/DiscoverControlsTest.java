package de.rawinstinctai.apkdrop;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=26,qualifiers="w360dp-h800dp-xhdpi")
public class DiscoverControlsTest {
    private org.robolectric.android.controller.ActivityController<MainActivity> controller;
    @After public void close(){if(controller!=null)controller.destroy();RuntimeEnvironment.setFontScale(1f);}
    private StoreController store(MainActivity a)throws Exception {var f=MainActivity.class.getDeclaredField("store");f.setAccessible(true);return (StoreController)f.get(a);}
    private Spinner choice(View view,String name){if(view instanceof Spinner s&&name.contentEquals(s.getContentDescription()))return s;if(view instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){Spinner found=choice(g.getChildAt(i),name);if(found!=null)return found;}return null;}
    @Test(timeout=20000) public void cancelPreservesFiltersAndApplyRestoresAfterRotation()throws Exception {
        controller=Robolectric.buildActivity(MainActivity.class).create();MainActivity a=controller.get();StoreController s=store(a);
        a.findViewById(R.id.discoverFilter).performClick();AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();
        choice(dialog.getWindow().getDecorView(),"Kategorie").setSelection(4);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();Bundle canceled=new Bundle();s.saveState(canceled);
        assertEquals("",canceled.getString("discoverCategory"));
        a.findViewById(R.id.discoverFilter).performClick();dialog=ShadowAlertDialog.getLatestAlertDialog();
        choice(dialog.getWindow().getDecorView(),"Kategorie").setSelection(4);
        choice(dialog.getWindow().getDecorView(),"Anzeigen").setSelection(1);
        choice(dialog.getWindow().getDecorView(),"Sortieren").setSelection(1);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();Bundle saved=new Bundle();s.saveState(saved);
        assertEquals("privacy",saved.getString("discoverCategory"));assertTrue(saved.getBoolean("newDiscover"));assertTrue(saved.getBoolean("discoverSortByName"));
        controller.destroy();controller=Robolectric.buildActivity(MainActivity.class).create(saved);a=controller.get();
        assertTrue(((Button)a.findViewById(R.id.discoverFilter)).getText().toString().contains("•"));
        a.findViewById(R.id.discoverFilter).performClick();dialog=ShadowAlertDialog.getLatestAlertDialog();
        assertEquals(4,choice(dialog.getWindow().getDecorView(),"Kategorie").getSelectedItemPosition());
        assertEquals(1,choice(dialog.getWindow().getDecorView(),"Anzeigen").getSelectedItemPosition());
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();Bundle reset=new Bundle();store(a).saveState(reset);
        assertEquals("",reset.getString("discoverCategory"));assertFalse(reset.getBoolean("newDiscover"));assertFalse(reset.getBoolean("discoverSortByName"));
    }
    @Test(timeout=30000) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk=35,qualifiers="w320dp-h640dp-xhdpi")
    public void compactDiscoverAndLargeFontFilterPreview()throws Exception {
        RuntimeEnvironment.setFontScale(1.5f);controller=Robolectric.buildActivity(MainActivity.class).create();MainActivity a=controller.get();
        a.findViewById(R.id.navDiscover).performClick();
        store(a).renderCatalog(new JSONObject().put("schema","apkdrop.discover.v1").put("total",1).put("page",1).put("pages",1)
                .put("apps",new JSONArray().put(new JSONObject().put("slug","pocket-notes").put("name","Pocket Notes").put("description","Private Notizen. Direkt vom Entwickler.").put("version","1.2"))));
        render(a.findViewById(R.id.pageRoot),"alpha19-1-discover-large");
        assertEquals(View.GONE,a.findViewById(R.id.categoryScroll).getVisibility());
        assertEquals(View.GONE,a.findViewById(R.id.radarReleases).getVisibility());
        assertEquals(View.GONE,a.findViewById(R.id.radarGitHub).getVisibility());
        a.findViewById(R.id.discoverFilter).performClick();AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();
        render(dialog.getWindow().getDecorView(),"alpha19-1-filter-large");
        for(String name:new String[]{"Kategorie","Anzeigen","Sortieren"})assertNotNull(choice(dialog.getWindow().getDecorView(),name));
        dialog.dismiss();
    }
    private void render(View root,String name)throws Exception {
        int width=640,height=1280;root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));root.layout(0,0,width,height);
        String dir=System.getProperty("apkdrop.preview.dir");if(dir==null)return;java.io.File file=new java.io.File(dir,name+".png");file.getParentFile().mkdirs();
        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(width,height,android.graphics.Bitmap.Config.ARGB_8888);root.draw(new android.graphics.Canvas(bitmap));try(var out=new java.io.FileOutputStream(file)){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
    }
}
