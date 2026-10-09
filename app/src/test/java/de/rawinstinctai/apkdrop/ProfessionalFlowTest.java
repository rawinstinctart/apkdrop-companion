package de.rawinstinctai.apkdrop;

import android.view.View;
import android.widget.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ActivityController;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w320dp-h640dp-xhdpi")
public class ProfessionalFlowTest {
    private ActivityController<MainActivity> controller;
    @Before public void setup(){
        var context=RuntimeEnvironment.getApplication();
        context.getSharedPreferences("apkdrop-github-radar",0).edit().clear().commit();
        context.getSharedPreferences("apkdrop-updates",0).edit().clear().commit();
        DropPilot.enabled(context,false);
    }
    @After public void close(){if(controller!=null)controller.destroy();RuntimeEnvironment.setFontScale(1f);}
    private MainActivity create(){controller=Robolectric.buildActivity(MainActivity.class).create();return controller.get();}
    private AppLibraryController library(MainActivity a) throws Exception {
        var f=MainActivity.class.getDeclaredField("library");f.setAccessible(true);return (AppLibraryController)f.get(a);
    }
    private void save(MainActivity a,String slug,String name,InstallPolicy.Mode mode) throws Exception {
        InstallContract r=new InstallContract(slug,name,"r1","2.0",20,"dev.sample."+slug.replace('-','_'),26,36,
            List.of(),Set.of(),Set.of("a".repeat(64)),"b".repeat(64),4096,"stable","# Neu\n\n- Ein klarer nächster Schritt.","","","");
        library(a).add(r,null,new InstallPolicy.Result(mode,"Release verfügbar",Set.of(),Set.of()));
    }
    @Test public void firstUseHasOneDiscoverActionAndNoEmptyCounters(){
        MainActivity a=create();
        assertEquals(View.GONE,a.findViewById(R.id.homeStats).getVisibility());
        assertEquals(View.GONE,a.findViewById(R.id.homeAppsHeading).getVisibility());
        assertEquals(View.GONE,a.findViewById(R.id.homeActivity).getVisibility());
        assertEquals(View.GONE,a.findViewById(R.id.homePreview).getVisibility());
        assertEquals(View.GONE,a.findViewById(R.id.homeDiscover).getVisibility());
        assertEquals("Apps entdecken →",((Button)a.findViewById(R.id.homeUpdates)).getText().toString());
        assertEquals(View.VISIBLE,a.findViewById(R.id.homeAddLink).getVisibility());
    }
    @Test public void savedAppsRestoreHomeSectionsAndDoNotDuplicateUpdateAction() throws Exception {
        MainActivity a=create();save(a,"sample-app","Pocket Notes",InstallPolicy.Mode.UPDATE);
        assertEquals(View.VISIBLE,a.findViewById(R.id.homeStats).getVisibility());
        assertEquals(View.VISIBLE,a.findViewById(R.id.homeAppsHeading).getVisibility());
        assertEquals(View.VISIBLE,a.findViewById(R.id.homeActivity).getVisibility());
        LinearLayout actions=a.findViewById(R.id.actionCenter);
        for(int i=0;i<actions.getChildCount();i++)if(actions.getChildAt(i) instanceof Button)
            assertFalse(((Button)actions.getChildAt(i)).getText().toString().contains("Updates · jetzt prüfen"));
    }
    @Test public void searchRecoveryClearsFilterAndRestoresSavedApps() throws Exception {
        MainActivity a=create();save(a,"sample-app","Pocket Notes",InstallPolicy.Mode.CURRENT);save(a,"other-app","Quiet Reader",InstallPolicy.Mode.CURRENT);
        a.findViewById(R.id.navApps).performClick();
        ((EditText)a.findViewById(R.id.librarySearch)).setText("kein-treffer");
        assertEquals(0,((LinearLayout)a.findViewById(R.id.libraryList)).getChildCount());
        assertEquals("Suche zurücksetzen →",((Button)a.findViewById(R.id.libraryDiscover)).getText().toString());
        a.findViewById(R.id.libraryDiscover).performClick();
        assertEquals("",((EditText)a.findViewById(R.id.librarySearch)).getText().toString());
        assertEquals(2,((LinearLayout)a.findViewById(R.id.libraryList)).getChildCount());
    }
    @Test public void emptyUpdateRecoveryOpensLibraryInsteadOfCatalog() throws Exception {
        MainActivity a=create();save(a,"sample-app","Pocket Notes",InstallPolicy.Mode.CURRENT);
        a.findViewById(R.id.navUpdates).performClick();
        assertEquals(View.GONE,a.findViewById(R.id.addLinkButton).getVisibility());
        assertEquals("Meine Apps ansehen →",((Button)a.findViewById(R.id.libraryDiscover)).getText().toString());
        a.findViewById(R.id.libraryDiscover).performClick();
        assertTrue(a.findViewById(R.id.navApps).isSelected());
        assertEquals(1,((LinearLayout)a.findViewById(R.id.libraryList)).getChildCount());
        assertEquals(View.VISIBLE,a.findViewById(R.id.addLinkButton).getVisibility());
    }
    @Test public void updateRowsHaveSpecificActionAndNamedMenu() throws Exception {
        MainActivity a=create();save(a,"sample-app","Pocket Notes",InstallPolicy.Mode.UPDATE);a.findViewById(R.id.navUpdates).performClick();
        View row=((LinearLayout)a.findViewById(R.id.libraryList)).getChildAt(0);
        assertEquals("Update ansehen & prüfen →",((Button)row.findViewById(R.id.trackedOpen)).getText().toString());
        assertEquals("Aktionen für Pocket Notes",row.findViewById(R.id.trackedMenu).getContentDescription());
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void narrowScreensAndLargeFontsKeepActionsReadable() throws Exception {
        RuntimeEnvironment.setFontScale(1.5f);MainActivity a=create();
        save(a,"sample-app","Eine App mit einem richtig langen Namen",InstallPolicy.Mode.UPDATE);
        for(int id:new int[]{R.id.navHome,R.id.navApps,R.id.navUpdates,R.id.navSettings}){
            a.findViewById(id).performClick();render(a,"alpha16-1-"+id+"-large");
        }
        a.findViewById(R.id.navUpdates).performClick();render(a,"alpha16-1-updates-large");
        View row=((LinearLayout)a.findViewById(R.id.libraryList)).getChildAt(0);
        TextView open=row.findViewById(R.id.trackedOpen);
        assertEquals(-2,open.getLayoutParams().height);
        assertTrue(open.getHeight()>=open.getLayout().getHeight()+open.getCompoundPaddingTop()+open.getCompoundPaddingBottom());
        assertTrue(a.findViewById(R.id.bottomNav).getBottom()<=1280);
        for(int id:new int[]{R.id.navHome,R.id.navDiscover,R.id.navApps,R.id.navUpdates,R.id.navSettings}) {
            TextView nav=a.findViewById(id);
            int last=nav.getLayout().getLineCount()-1;
            assertTrue("Navigation text clips for "+nav.getText(),
                    nav.getLayout().getLineBottom(last)+nav.getCompoundPaddingTop()+nav.getCompoundPaddingBottom()<=nav.getHeight());
        }
        a.findViewById(R.id.navHome).performClick();render(a,"alpha16-1-home-large");
        LinearLayout home=a.findViewById(R.id.homeAppActions);
        LinearLayout card=(LinearLayout)home.getChildAt(0);
        assertEquals(LinearLayout.VERTICAL,card.getOrientation());
        RuntimeEnvironment.setFontScale(1f);
    }
    private void render(MainActivity a,String name) throws Exception {
        View root=a.findViewById(R.id.pageRoot);
        root.measure(View.MeasureSpec.makeMeasureSpec(640,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1280,View.MeasureSpec.EXACTLY));root.layout(0,0,640,1280);
        String dir=System.getProperty("apkdrop.preview.dir");if(dir==null)return;
        java.io.File file=new java.io.File(dir,name+".png");file.getParentFile().mkdirs();
        var bitmap=android.graphics.Bitmap.createBitmap(640,1280,android.graphics.Bitmap.Config.ARGB_8888);root.draw(new android.graphics.Canvas(bitmap));
        try(var out=new java.io.FileOutputStream(file)){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
    }
}
