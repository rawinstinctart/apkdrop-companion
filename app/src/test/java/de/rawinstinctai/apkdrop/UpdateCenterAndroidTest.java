package de.rawinstinctai.apkdrop;

import android.app.*;
import android.app.job.*;
import android.content.*;
import android.content.pm.*;
import android.os.Looper;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Android JVM lifecycle/storage tests. Real devices and real installer UI are separate checks. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=26)
public final class UpdateCenterAndroidTest {
    private Context context;
    private ActivityController<MainActivity> activity;
    @Before public void prepare() {
        context=RuntimeEnvironment.getApplication();
        for(String name:List.of("apkdrop-library","apkdrop-release-snapshots","apkdrop-updates","apkdrop-notifications"))
            context.getSharedPreferences(name,Context.MODE_PRIVATE).edit().clear().commit();
    }
    @After public void close() { if(activity!=null) activity.pause().stop().destroy(); }
    private InstallContract release(String slug,String name,String pkg,long code,Set<String> permissions,String signer) {
        return new InstallContract(slug,name,"r"+code,"1."+code,code,pkg,26,35,List.of(),permissions,Set.of(signer),
                "b".repeat(64),100,"stable","","https://apkdrop.rawinstinctai.de/"+slug+"/releases/r"+code+".apk",
                "https://apkdrop.rawinstinctai.de/"+slug+"/receipt","https://apkdrop.rawinstinctai.de/"+slug);
    }
    private String install(String pkg,long code) throws Exception {
        byte[] certificate={1,2,3,4};
        String signer=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(certificate));
        PackageInfo info=new PackageInfo(); info.packageName=pkg; info.versionCode=(int)code; info.versionName="1."+code;
        info.signatures=new Signature[]{new Signature(certificate)};
        info.applicationInfo=new ApplicationInfo(); info.applicationInfo.packageName=pkg;
        info.applicationInfo.minSdkVersion=26; info.applicationInfo.targetSdkVersion=35;
        info.requestedPermissions=new String[0]; shadowOf(context.getPackageManager()).installPackage(info);
        return signer;
    }
    private AppLibrary.Entry save(InstallContract release) throws Exception {
        AppLibrary.Entry pin=new AppLibrary.Entry(release.slug,release.appName,release.packageName,release.signers);
        AppLibraryStore store=new AppLibraryStore(context); store.save(store.read().add(pin)); return pin;
    }
    private MainActivity start() { activity=Robolectric.buildActivity(MainActivity.class).setup(); return activity.get(); }
    private void drain(MainActivity target) throws Exception {
        java.lang.reflect.Field field=MainActivity.class.getDeclaredField("io"); field.setAccessible(true);
        ((ExecutorService)field.get(target)).submit(()->{}).get(5,TimeUnit.SECONDS);
        shadowOf(Looper.getMainLooper()).idle();
    }
    @Test public void activityRestoresLastKnownReleaseWithoutNetworkDownload() throws Exception {
        String signer=install("de.example.app",1); InstallContract release=release("test-app","Test App","de.example.app",2,Set.of(),signer);
        AppLibrary.Entry pin=save(release); new ReleaseSnapshotStore(context).success(pin,release,System.currentTimeMillis());
        MainActivity target=start(); drain(target);
        LinearLayout rows=target.findViewById(R.id.libraryList); assertEquals(1,rows.getChildCount());
        TextView badge=rows.getChildAt(0).findViewById(R.id.trackedBadge);
        TextView detail=rows.getChildAt(0).findViewById(R.id.trackedDetail);
        assertEquals("UPDATE VERFÜGBAR",badge.getText().toString()); assertTrue(detail.getText().toString().contains("Letzter bekannter Release"));
    }
    @Test @Config(qualifiers="w432dp-h960dp-xhdpi")
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    public void restoredRowsPrioritizeSensitiveUpdates() throws Exception {
        String signer=install("de.example.normal",1); install("de.example.sensitive",1);
        InstallContract normal=release("normal-app","A Normal","de.example.normal",2,Set.of(),signer);
        InstallContract sensitive=release("sensitive-app","Z Sensitive","de.example.sensitive",2,Set.of("android.permission.RECORD_AUDIO"),signer);
        ReleaseSnapshotStore snapshots=new ReleaseSnapshotStore(context);
        snapshots.success(save(normal),normal,System.currentTimeMillis()); snapshots.success(save(sensitive),sensitive,System.currentTimeMillis());
        MainActivity target=start(); drain(target);
        TextView first=((LinearLayout)target.findViewById(R.id.libraryList)).getChildAt(0).findViewById(R.id.trackedName);
        assertEquals("Z Sensitive",first.getText().toString());
        String directory=System.getProperty("apkdrop.preview.dir");
        if(directory!=null) {
            capture(target,directory,"alpha4-update-center.png");
            activity.pause().stop().destroy(); activity=null;
            RuntimeEnvironment.setFontScale(1.4f);
            MainActivity enlarged=start(); drain(enlarged);
            capture(enlarged,directory,"alpha4-large-text.png");
        }
    }
    private void capture(MainActivity target,String directory,String name) throws Exception {
        android.view.View root=target.findViewById(R.id.pageRoot);
        root.measure(android.view.View.MeasureSpec.makeMeasureSpec(864,android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(1920,android.view.View.MeasureSpec.EXACTLY));
        root.layout(0,0,864,1920);
        android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(864,1920,android.graphics.Bitmap.Config.ARGB_8888);
        root.draw(new android.graphics.Canvas(image));
        java.io.File file=new java.io.File(directory,name); file.getParentFile().mkdirs();
        try(java.io.OutputStream out=new java.io.FileOutputStream(file)) { assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out)); }
        image.recycle();
    }
    @Test public void failedCheckCannotAppearAsSuccessfulCachedUpdate() throws Exception {
        String signer=install("de.example.app",1); InstallContract release=release("test-app","Test App","de.example.app",2,Set.of(),signer);
        AppLibrary.Entry pin=save(release); ReleaseSnapshotStore snapshots=new ReleaseSnapshotStore(context);
        long now=System.currentTimeMillis(); snapshots.success(pin,release,now-1000); snapshots.failure(pin,"offline",false,now);
        MainActivity target=start(); drain(target);
        TextView badge=((LinearLayout)target.findViewById(R.id.libraryList)).getChildAt(0).findViewById(R.id.trackedBadge);
        assertEquals("PRÜFUNG FEHLGESCHLAGEN",badge.getText().toString());
        assertEquals(android.view.View.GONE,target.findViewById(R.id.updatesButton).getVisibility());
    }
    @Test public void actualInstalledVersionOverridesCachedUpdateAfterInstallerReturn() throws Exception {
        String signer=install("de.example.app",1); InstallContract release=release("test-app","Test App","de.example.app",2,Set.of(),signer);
        AppLibrary.Entry pin=save(release); new ReleaseSnapshotStore(context).success(pin,release,System.currentTimeMillis());
        MainActivity target=start(); drain(target); install("de.example.app",2);
        activity.pause().resume(); drain(target);
        TextView badge=((LinearLayout)target.findViewById(R.id.libraryList)).getChildAt(0).findViewById(R.id.trackedBadge);
        assertEquals("AKTUELL",badge.getText().toString());
    }
    @Test public void staleJobCannotRestoreRemovedSnapshot() throws Exception {
        InstallContract release=ReleaseIntelligenceTest.release(2,Set.of(),26,35,100); AppLibrary.Entry pin=save(release);
        AppLibraryStore library=new AppLibraryStore(context); ReleaseSnapshotStore snapshots=new ReleaseSnapshotStore(context);
        snapshots.success(pin,release,System.currentTimeMillis()); library.save(library.read().remove(pin.slug)); snapshots.prune(library.read());
        snapshots.success(pin,release,System.currentTimeMillis()); assertNull(snapshots.read(pin));
    }
    @Test public void olderResponseCannotOverwriteNewerCheck() throws Exception {
        InstallContract release=ReleaseIntelligenceTest.release(2,Set.of(),26,35,100); AppLibrary.Entry pin=save(release);
        ReleaseSnapshotStore snapshots=new ReleaseSnapshotStore(context); long now=System.currentTimeMillis();
        snapshots.success(pin,release,now); snapshots.failure(pin,"stale failure",false,now-1000);
        assertNull(snapshots.read(pin).error); assertEquals(now,snapshots.read(pin).checkedAt);
    }
    @Test public void snapshotsRejectChangedPin() throws Exception {
        InstallContract release=ReleaseIntelligenceTest.release(2,Set.of(),26,35,100); AppLibrary.Entry pin=save(release);
        ReleaseSnapshotStore snapshots=new ReleaseSnapshotStore(context); snapshots.success(pin,release,System.currentTimeMillis());
        AppLibrary.Entry changed=new AppLibrary.Entry(pin.slug,pin.name,pin.packageName,Set.of("c".repeat(64)));
        assertThrows(SecurityException.class,()->snapshots.read(changed));
    }
    @Test public void schedulingOnlyExistsForSavedAppsAndCanBeDisabled() throws Exception {
        JobScheduler scheduler=context.getSystemService(JobScheduler.class);
        UpdateScheduler.reconcile(context); assertNull(scheduler.getPendingJob(UpdateScheduler.JOB_ID));
        save(ReleaseIntelligenceTest.release(2,Set.of(),26,35,100)); assertTrue(UpdateScheduler.reconcile(context));
        JobInfo job=scheduler.getPendingJob(UpdateScheduler.JOB_ID); assertNotNull(job);
        assertTrue(job.isPersisted()); assertTrue(job.isRequireBatteryNotLow()); assertEquals(JobInfo.NETWORK_TYPE_ANY,job.getNetworkType());
        UpdateScheduler.enabled(context,false); assertNull(scheduler.getPendingJob(UpdateScheduler.JOB_ID));
    }
    @Test public void corruptLibraryDisablesBackgroundJobWithoutResettingPins() {
        context.getSharedPreferences("apkdrop-library",Context.MODE_PRIVATE).edit().putString("apps","corrupt").commit();
        assertFalse(UpdateScheduler.reconcile(context));
        assertEquals("corrupt",context.getSharedPreferences("apkdrop-library",Context.MODE_PRIVATE).getString("apps",null));
    }
    @Test public void queueAndPendingInstallerPersistSeparately() {
        AppLibraryStore store=new AppLibraryStore(context); store.queue(new UpdateQueue(List.of("test-app","other-app")));
        store.pendingInstaller("test-app"); store.pendingLaunched(true);
        AppLibraryStore restored=new AppLibraryStore(context); assertEquals("test-app",restored.queue().current()); assertTrue(restored.pendingLaunched());
        restored.clearPendingInstaller(); assertFalse(store.pendingLaunched()); assertEquals(2,store.queue().size());
    }
    private void queuePrecondition(MainActivity target,String slug) throws Exception {
        java.lang.reflect.Field queue=MainActivity.class.getDeclaredField("queue"); queue.setAccessible(true);
        queue.set(target,new UpdateQueue(List.of(slug)));
        java.lang.reflect.Field waiting=MainActivity.class.getDeclaredField("awaitingInstaller"); waiting.setAccessible(true); waiting.set(target,true);
        new AppLibraryStore(context).queue(new UpdateQueue(List.of(slug)));
        // Installer launch is an explicit precondition, never a mocked success result.
        new AppLibraryStore(context).pendingInstaller(slug); new AppLibraryStore(context).pendingLaunched(true);
    }
    private void present(MainActivity target,InstallContract release,InstalledState installed) throws Exception {
        java.lang.reflect.Method show=MainActivity.class.getDeclaredMethod("show",InstallContract.class,InstalledState.class,InstallPolicy.Result.class,boolean.class,boolean.class);
        show.setAccessible(true);
        show.invoke(target,release,installed,InstallPolicy.evaluate(release,installed,36,new String[]{"arm64-v8a"}),false,false);
    }
    @Test public void cancelledInstallerKeepsQueueItemAndUpdateAction() throws Exception {
        MainActivity target=start(); drain(target); InstallContract release=ReleaseIntelligenceTest.release(2,Set.of(),26,35,100);
        queuePrecondition(target,release.slug); present(target,release,ReleaseIntelligenceTest.installed());
        assertEquals(release.slug,new AppLibraryStore(context).queue().current());
        assertEquals(android.view.View.VISIBLE,target.findViewById(R.id.actionButton).getVisibility());
    }
    @Test public void actualSuccessfulInstallCompletesQueueAndHidesInstallAction() throws Exception {
        MainActivity target=start(); drain(target); InstallContract release=ReleaseIntelligenceTest.release(2,Set.of(),26,35,100);
        queuePrecondition(target,release.slug);
        InstalledState actual=new InstalledState(release.packageName,2,"1.2",release.signers,release.permissions,26,35);
        present(target,release,actual);
        assertNull(new AppLibraryStore(context).queue().current());
        assertEquals(android.view.View.GONE,target.findViewById(R.id.actionButton).getVisibility());
        assertEquals("AKTUELL",((TextView)target.findViewById(R.id.statusBadge)).getText().toString());
    }
    @Test public void signerMismatchCannotAdvanceQueue() throws Exception {
        MainActivity target=start(); drain(target); InstallContract release=ReleaseIntelligenceTest.release(2,Set.of(),26,35,100);
        queuePrecondition(target,release.slug);
        InstalledState changed=new InstalledState(release.packageName,2,Set.of("c".repeat(64)),Set.of()); present(target,release,changed);
        assertEquals(release.slug,new AppLibraryStore(context).queue().current());
        assertEquals("BLOCKIERT",((TextView)target.findViewById(R.id.statusBadge)).getText().toString());
    }
    @Test public void unsafeExternalLinksRejected() {
        assertEquals("test-app",SlugParser.parse("https://apkdrop.rawinstinctai.de/install/test-app"));
        assertThrows(IllegalArgumentException.class,()->SlugParser.parse("https://user@apkdrop.rawinstinctai.de/install/test-app"));
        assertThrows(IllegalArgumentException.class,()->SlugParser.parse("https://apkdrop.rawinstinctai.de:8443/install/test-app"));
        assertThrows(IllegalArgumentException.class,()->SlugParser.parse("https://evil.example/install/test-app"));
    }
    @Test @Config(sdk=33) public void notificationDenialDoesNotRecordReleaseAsNotified() throws Exception {
        InstallContract release=ReleaseIntelligenceTest.release(2,Set.of(),26,35,100); AppLibrary.Entry pin=save(release);
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS);
        UpdateNotifications.post(context,pin,release,ReleaseIntelligenceTest.decide(release));
        assertNull(context.getSharedPreferences("apkdrop-notifications",Context.MODE_PRIVATE).getString(pin.slug,null));
    }
    @Test @Config(sdk=33) public void allowedNotificationsUseExplicitAppLinkAndDeduplicate() throws Exception {
        InstallContract release=ReleaseIntelligenceTest.release(2,Set.of(),26,35,100); AppLibrary.Entry pin=save(release);
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS);
        UpdateNotifications.post(context,pin,release,ReleaseIntelligenceTest.decide(release));
        NotificationManager manager=context.getSystemService(NotificationManager.class);
        assertEquals(1,manager.getActiveNotifications().length);
        Intent intent=shadowOf(manager.getActiveNotifications()[0].getNotification().contentIntent).getSavedIntent();
        assertEquals(MainActivity.class.getName(),intent.getComponent().getClassName());
        assertEquals("https://apkdrop.rawinstinctai.de/install/test-app",intent.getDataString());
        UpdateNotifications.post(context,pin,release,ReleaseIntelligenceTest.decide(release)); assertEquals(1,manager.getActiveNotifications().length);
        UpdateNotifications.remove(context,pin.slug); assertEquals(0,manager.getActiveNotifications().length);
    }
}
