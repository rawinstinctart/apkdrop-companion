package de.rawinstinctai.apkdrop;

import android.app.job.*;
import android.content.*;
import android.os.*;
import android.view.View;
import android.widget.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ActivityController;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

/** Exercises the real share/parser/resolver/activity/storage/scheduler path with fixture HTTP responses. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=26,qualifiers="w360dp-h800dp-xhdpi",shadows={ShareTakeoverTest.Contracts.class,ShareTakeoverTest.PublicStore.class})
public class ShareTakeoverTest {
    static volatile boolean expired,hold,holdContract;
    static final List<String> fetched=Collections.synchronizedList(new ArrayList<>());
    private ActivityController<MainActivity> controller;
    private Context context;
    @Before public void prepare() throws Exception {
        java.lang.reflect.Method scale=android.animation.ValueAnimator.class.getDeclaredMethod("setDurationScale",float.class);
        scale.setAccessible(true);scale.invoke(null,0f);
        context=RuntimeEnvironment.getApplication();expired=false;hold=false;holdContract=false;fetched.clear();
        for(String name:List.of("apkdrop-library","apkdrop-release-snapshots","apkdrop-updates","apkdrop-notifications"))
            context.getSharedPreferences(name,Context.MODE_PRIVATE).edit().clear().commit();
        context.getSystemService(JobScheduler.class).cancelAll();
    }
    @After public void close(){hold=false;holdContract=false;if(controller!=null)controller.destroy();}
    private MainActivity share(String link) {
        Intent intent=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"Diese App: "+link);
        controller=Robolectric.buildActivity(MainActivity.class,intent).create();return controller.get();
    }
    private void ready(MainActivity a) throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(System.nanoTime()<until){Shadows.shadowOf(Looper.getMainLooper()).idle();
            if(a.findViewById(R.id.addButton).getVisibility()==View.VISIBLE&&a.findViewById(R.id.addButton).isEnabled())return;
            Thread.sleep(5);
        }
        fail("Shared app did not reach its reviewed details: "+((TextView)a.findViewById(R.id.statusText)).getText());
    }
    private void takeOver(MainActivity a) throws Exception {
        takeOver(a,false);
    }
    private void takeOver(MainActivity a,boolean capture) throws Exception {
        assertEquals("Test App",((TextView)a.findViewById(R.id.titleText)).getText().toString());
        assertNull(new AppLibraryStore(context).read().find("test-app"));
        assertNull(context.getSystemService(JobScheduler.class).getPendingJob(UpdateScheduler.JOB_ID));
        a.findViewById(R.id.addButton).performClick();
        AppLibrary.Entry saved=new AppLibraryStore(context).read().find("test-app");assertNotNull(saved);
        assertEquals("de.example.app",saved.packageName);assertEquals(Set.of("a".repeat(64)),saved.signers);
        JobInfo job=context.getSystemService(JobScheduler.class).getPendingJob(UpdateScheduler.JOB_ID);
        assertNotNull(job);assertTrue(job.isPersisted());assertEquals(6L*60*60*1000,job.getIntervalMillis());
        assertTrue(((TextView)a.findViewById(R.id.monitoringStatus)).getText().toString().contains("aktiv"));
        assertFalse(a.findViewById(R.id.addButton).isEnabled());
        assertNull(Shadows.shadowOf(a).getNextStartedActivity());
        if(capture)render(a,"alpha9-takeover-saved");
        Bundle state=new Bundle();controller.saveInstanceState(state).destroy();
        controller=Robolectric.buildActivity(MainActivity.class).create(state);
        assertNotNull(new AppLibraryStore(context).read().find("test-app"));assertTrue(UpdateScheduler.scheduled(context));
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) public void apkdropShareTakesOverWithOneTapAndPersistsMonitoring() throws Exception {
        MainActivity a=share("https://apkdrop.rawinstinctai.de/install/test-app");ready(a);render(a,"alpha9-share-details");takeOver(a,true);
    }
    @Test public void githubRepositoryShareUsesTheSameReviewedPublicImportFlow() throws Exception {
        MainActivity a=share("https://github.com/fixture-dev/example");ready(a);
        assertTrue(fetched.contains("/api/dropid/fixture-dev.json"));takeOver(a);
    }
    @Test public void githubShareUsesVerifiedMappingThenTheCurrentApkdropContract() throws Exception {
        MainActivity a=share("https://github.com/fixture-dev/example/releases/tag/old-tag");ready(a);
        assertTrue(fetched.contains("/api/dropid/fixture-dev.json"));assertTrue(fetched.contains("contract:test-app"));takeOver(a);
    }
    @Test public void takingOverExplicitlyEnablesChecksAndLaterOptOutIsRespected() throws Exception {
        UpdateScheduler.enabled(context,false);
        MainActivity a=share("https://apkdrop.rawinstinctai.de/test-app");ready(a);a.findViewById(R.id.addButton).performClick();
        assertTrue(UpdateScheduler.enabled(context));assertTrue(UpdateScheduler.scheduled(context));
        ((Switch)a.findViewById(R.id.backgroundSwitch)).setChecked(false);
        assertFalse(UpdateScheduler.scheduled(context));assertNotNull(new AppLibraryStore(context).read().find("test-app"));
        assertTrue(((TextView)a.findViewById(R.id.monitoringStatus)).getText().toString().contains("ausgeschaltet"));
        a.findViewById(R.id.addButton).performClick();assertTrue(UpdateScheduler.scheduled(context));
        assertEquals(1,new AppLibraryStore(context).read().entries().size());
    }
    @Test public void unresolvedShareSurvivesSavedStateWithoutImportingTwice() throws Exception {
        hold=true;MainActivity a=share("https://github.com/fixture-dev/example/releases/latest");
        Bundle state=new Bundle();controller.saveInstanceState(state).destroy();
        assertEquals("https://github.com/fixture-dev/example/releases/latest",state.getString("pendingLink").replace("Diese App: ",""));
        hold=false;controller=Robolectric.buildActivity(MainActivity.class).create(state);a=controller.get();ready(a);takeOver(a);
    }
    @Test public void expiredMappingNeverOffersTakeoverOrSchedulesChecks() throws Exception {
        expired=true;MainActivity a=share("https://github.com/fixture-dev/example/releases/latest");
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(System.nanoTime()<until){Shadows.shadowOf(Looper.getMainLooper()).idle();
            if(((TextView)a.findViewById(R.id.statusText)).getText().toString().contains("keine bestätigte"))break;Thread.sleep(5);}
        assertTrue(((TextView)a.findViewById(R.id.statusText)).getText().toString().contains("keine bestätigte"));
        assertEquals(View.GONE,a.findViewById(R.id.addButton).getVisibility());assertTrue(new AppLibraryStore(context).read().entries().isEmpty());
        assertFalse(UpdateScheduler.scheduled(context));assertFalse(fetched.contains("contract:test-app"));
    }
    @Test public void newShareReusesActivityAndResolvesWithoutSavingThePreviousApp() throws Exception {
        MainActivity a=share("https://apkdrop.rawinstinctai.de/install/test-app");ready(a);
        a.onNewIntent(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"https://github.com/fixture-dev/example/releases/latest"));
        assertEquals(View.GONE,a.findViewById(R.id.addButton).getVisibility());ready(a);
        assertTrue(fetched.contains("/api/dropid/fixture-dev.json"));assertTrue(new AppLibraryStore(context).read().entries().isEmpty());
        takeOver(a);
    }
    @Test public void incomingShareIsNotHiddenBehindAPreviousUpdateQueue() throws Exception {
        new AppLibraryStore(context).queue(new UpdateQueue(List.of("older-app")));
        MainActivity a=share("https://apkdrop.rawinstinctai.de/install/test-app");ready(a);
        assertNull(new AppLibraryStore(context).queue().current());takeOver(a);
    }
    @Test public void styledAndroidShareTextIsAccepted() throws Exception {
        Intent intent=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,
            new android.text.SpannableString("Diese App:\nhttps://apkdrop.rawinstinctai.de/install/test-app\nViel Spaß!"));
        controller=Robolectric.buildActivity(MainActivity.class,intent).create();MainActivity a=controller.get();ready(a);takeOver(a);
    }
    @Test public void restoringAnInFlightUpdateDoesNotTreatItAsANewShare() throws Exception {
        AppLibraryStore pins=new AppLibraryStore(context);
        pins.save(pins.read().add(new AppLibrary.Entry("test-app","Test App","de.example.app",Set.of("a".repeat(64)))));
        pins.queue(new UpdateQueue(List.of("test-app")));holdContract=true;
        controller=Robolectric.buildActivity(MainActivity.class).create();
        Bundle state=new Bundle();controller.saveInstanceState(state).destroy();
        assertNull(state.getString("pendingLink"));holdContract=false;
        controller=Robolectric.buildActivity(MainActivity.class).create(state);
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(System.nanoTime()<until){Shadows.shadowOf(Looper.getMainLooper()).idle();
            if(((TextView)controller.get().findViewById(R.id.titleText)).getText().toString().equals("Test App"))break;Thread.sleep(5);}
        assertEquals("test-app",pins.queue().current());
        assertEquals(View.VISIBLE,controller.get().findViewById(R.id.queueCard).getVisibility());
    }
    @Test public void shareArrivingDuringARequestSurvivesRestartAndOpensWhenIdle() throws Exception {
        holdContract=true;MainActivity a=share("https://apkdrop.rawinstinctai.de/install/test-app");
        String next="https://github.com/fixture-dev/example/releases/latest";
        a.onNewIntent(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,next));
        Bundle state=new Bundle();controller.saveInstanceState(state).destroy();assertEquals(next,state.getString("deferredLink"));
        holdContract=false;controller=Robolectric.buildActivity(MainActivity.class).create(state);a=controller.get();
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(System.nanoTime()<until){Shadows.shadowOf(Looper.getMainLooper()).idle();if(fetched.contains("/api/dropid/fixture-dev.json"))break;Thread.sleep(5);}
        assertTrue(fetched.contains("/api/dropid/fixture-dev.json"));ready(a);takeOver(a);
    }
    @Test public void pendingShareOpensWhenReturningFromACancelledInstaller() throws Exception {
        AppLibraryStore pins=new AppLibraryStore(context);pins.pendingInstaller("older-app");pins.pendingLaunched(true);
        MainActivity a=share("https://github.com/fixture-dev/example/releases/latest");a.onResume();
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(System.nanoTime()<until){Shadows.shadowOf(Looper.getMainLooper()).idle();if(fetched.contains("/api/dropid/fixture-dev.json"))break;Thread.sleep(5);}
        assertTrue(fetched.contains("/api/dropid/fixture-dev.json"));ready(a);
        assertNull(pins.pendingInstaller());takeOver(a);
    }
    private void render(MainActivity a,String name) throws Exception {
        String dir=System.getProperty("apkdrop.preview.dir");if(dir==null)return;
        View root=a.findViewById(R.id.pageRoot);root.measure(View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1600,View.MeasureSpec.EXACTLY));root.layout(0,0,720,1600);
        java.io.File file=new java.io.File(dir,name+".png");file.getParentFile().mkdirs();android.graphics.Bitmap b=android.graphics.Bitmap.createBitmap(720,1600,android.graphics.Bitmap.Config.ARGB_8888);root.draw(new android.graphics.Canvas(b));
        try(java.io.OutputStream out=new java.io.FileOutputStream(file)){b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}b.recycle();
    }
    @Implements(value=ContractClient.class,isInAndroidSdk=false)
    public static class Contracts {
        @Implementation protected static InstallContract fetch(String slug) throws Exception {
            fetched.add("contract:"+slug);
            while(holdContract){if(Thread.currentThread().isInterrupted())throw new InterruptedException();Thread.sleep(5);}
            return new InstallContract(slug,"Test App","r2","1.2",2,"de.example.app",26,36,List.of(),Set.of(),Set.of("a".repeat(64)),"b".repeat(64),100,"stable","", "https://apkdrop.rawinstinctai.de/"+slug+"/releases/r2.apk","https://apkdrop.rawinstinctai.de/"+slug+"/receipt","");
        }
    }
    @Implements(value=StoreClient.class,isInAndroidSdk=false)
    public static class PublicStore {
        @Implementation protected static JSONObject get(String path) throws Exception {
            fetched.add(path);
            while(hold){if(Thread.currentThread().isInterrupted())throw new InterruptedException();Thread.sleep(5);}
            if(!path.equals("/api/dropid/fixture-dev.json"))throw new IllegalStateException("No display fixture");
            return new JSONObject().put("schema","apkdrop.dropid.v1").put("published",true).put("handle","fixture-dev")
                .put("github",new JSONObject().put("status","verified"))
                .put("apps",new JSONArray().put(new JSONObject().put("slug","test-app").put("repo","fixture-dev/example")
                    .put("repository",new JSONObject().put("status",expired?"expired":"verified"))));
        }
    }
}
