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
    @Test(timeout=20000) @LooperMode(LooperMode.Mode.LEGACY) public void cancelPreservesFiltersAndApplyRestoresAfterRotation()throws Exception {
        controller=Robolectric.buildActivity(MainActivity.class).create();MainActivity a=controller.get();StoreController s=store(a);
        a.findViewById(R.id.discoverFilter).performClick();AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();
        choice(dialog.getWindow().getDecorView(),"Kategorie").setSelection(4);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();Bundle canceled=new Bundle();s.saveState(canceled);
        assertEquals("",canceled.getString("discoverCategory"));
        a.findViewById(R.id.discoverFilter).performClick();dialog=ShadowAlertDialog.getLatestAlertDialog();
        choice(dialog.getWindow().getDecorView(),"Kategorie").setSelection(4);
        choice(dialog.getWindow().getDecorView(),"Anzeigen").setSelection(1);
        choice(dialog.getWindow().getDecorView(),"Sortieren").setSelection(1);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();Bundle saved=new Bundle();s.saveState(saved);
        assertEquals("privacy",saved.getString("discoverCategory"));assertTrue(saved.getBoolean("newDiscover"));assertTrue(saved.getBoolean("discoverSortByName"));
        controller.destroy();controller=Robolectric.buildActivity(MainActivity.class).create(saved);a=controller.get();
        assertTrue(((Button)a.findViewById(R.id.discoverFilter)).getText().toString().contains("•"));
        a.findViewById(R.id.discoverFilter).performClick();dialog=ShadowAlertDialog.getLatestAlertDialog();
        assertEquals(4,choice(dialog.getWindow().getDecorView(),"Kategorie").getSelectedItemPosition());
        assertEquals(1,choice(dialog.getWindow().getDecorView(),"Anzeigen").getSelectedItemPosition());
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();Bundle reset=new Bundle();store(a).saveState(reset);
        assertEquals("",reset.getString("discoverCategory"));assertFalse(reset.getBoolean("newDiscover"));assertFalse(reset.getBoolean("discoverSortByName"));
    }
}
