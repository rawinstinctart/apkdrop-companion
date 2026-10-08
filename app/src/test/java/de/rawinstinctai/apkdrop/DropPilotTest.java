package de.rawinstinctai.apkdrop;

import android.app.job.*;
import android.content.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(org.robolectric.RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk=35)
public class DropPilotTest {
    private Context context;
    private final String signer="a".repeat(64);
    private final String hash="b".repeat(64);

    @Before public void setup() throws Exception {
        context=org.robolectric.RuntimeEnvironment.getApplication();
        DropPilot.enabled(context,false);
        context.getSharedPreferences("apkdrop-library",Context.MODE_PRIVATE).edit().clear().commit();
        context.getSystemService(JobScheduler.class).cancelAll();
    }
    @After public void cleanup(){DropPilot.enabled(context,false);}

    private InstallContract release(long version) {
        return new InstallContract("test-app","Test App","release-"+version,"1."+version,version,
                "de.example.app",26,35,List.of(),Set.of(),Set.of(signer),hash,4,"stable","",
                "https://apkdrop.rawinstinctai.de/test-app/releases/file.apk",
                "https://apkdrop.rawinstinctai.de/test-app/receipt",
                "https://apkdrop.rawinstinctai.de/test-app");
    }
    private AppLibrary.Entry pin() throws Exception {
        AppLibrary.Entry pin=new AppLibrary.Entry("test-app","Test App","de.example.app",Set.of(signer));
        new AppLibraryStore(context).save(new AppLibrary().add(pin));
        return pin;
    }
    @Test public void defaultOffAndNeverStartsWithoutPin() {
        assertFalse(DropPilot.enabled(context));
        DropPilot.enabled(context,true);
        assertFalse(DropPilot.scheduled(context));
        assertNull(context.getSystemService(JobScheduler.class).getPendingJob(DropPilot.INITIAL_JOB));
    }
    @Test public void onlyUnmeteredChargingJobsAndExplicitOptOut() throws Exception {
        pin();
        DropPilot.enabled(context,true);
        JobInfo periodic=context.getSystemService(JobScheduler.class).getPendingJob(DropPilot.PERIODIC_JOB);
        assertNotNull(periodic);
        assertEquals(JobInfo.NETWORK_TYPE_UNMETERED,periodic.getNetworkType());
        assertTrue(periodic.isRequireCharging());
        assertTrue(periodic.isRequireBatteryNotLow());
        assertTrue(periodic.isPersisted());
        assertNotNull(context.getSystemService(JobScheduler.class).getPendingJob(DropPilot.INITIAL_JOB));
        DropPilot.enabled(context,false);
        assertNull(context.getSystemService(JobScheduler.class).getPendingJob(DropPilot.PERIODIC_JOB));
    }
    @Test public void preparedFileIsBoundToExactAppAndReleaseAndClearedOnOptOut() throws Exception {
        AppLibrary.Entry pin=pin();DropPilot.enabled(context,true);
        File dir=DropPilot.directory(context);assertTrue(dir.isDirectory()||dir.mkdirs());
        File file=new File(dir,VerifiedApkFiles.newName());
        try(FileOutputStream output=new FileOutputStream(file)){output.write(new byte[]{1,2,3,4});}
        assertTrue(DropPilot.record(context,pin,release(2),file));
        assertEquals(file,DropPilot.candidate(context,release(2)));
        assertNull(DropPilot.candidate(context,release(3)));
        DropPilot.enabled(context,false);
        assertFalse(file.exists());
        DropPilot.enabled(context,true);
        assertNull(DropPilot.candidate(context,release(2)));
    }
    @Test public void removedPinCannotKeepPreparedUpdate() throws Exception {
        AppLibrary.Entry pin=pin();DropPilot.enabled(context,true);
        File dir=DropPilot.directory(context);assertTrue(dir.isDirectory()||dir.mkdirs());
        File file=new File(dir,VerifiedApkFiles.newName());
        try(FileOutputStream output=new FileOutputStream(file)){output.write(new byte[]{1,2,3,4});}
        new AppLibraryStore(context).save(new AppLibrary());
        assertFalse(DropPilot.record(context,pin,release(2),file));
        assertNull(DropPilot.candidate(context,release(2)));
    }
}
