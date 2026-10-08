package de.rawinstinctai.apkdrop;

import android.content.Context;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=26)
public final class ApkDownloaderCancellationTest {
    private static final class SlowConnection extends HttpURLConnection {
        final CountDownLatch reading=new CountDownLatch(1);
        final CountDownLatch disconnected=new CountDownLatch(1);
        SlowConnection(URL url) { super(url); }
        @Override public void connect() {}
        @Override public void disconnect() { connected=false; disconnected.countDown(); }
        @Override public boolean usingProxy() { return false; }
        @Override public int getResponseCode() { return 200; }
        @Override public long getContentLengthLong() { return -1; }
        @Override public InputStream getInputStream() {
            return new InputStream() {
                @Override public int read() throws IOException {
                    byte[] one=new byte[1];
                    return read(one,0,1)<0?-1:one[0]&255;
                }
                @Override public int read(byte[] buffer,int offset,int length) throws IOException {
                    reading.countDown();
                    try {
                        if(!disconnected.await(5,TimeUnit.SECONDS)) throw new IOException("slow read did not stop");
                        throw new IOException("connection closed");
                    } catch(InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IOException("read interrupted",interrupted);
                    }
                }
            };
        }
    }

    @Test public void cancelDisconnectsSlowReadAndDeletesPartialFile() throws Exception {
        Context context=RuntimeEnvironment.getApplication();
        InstallContract release=new InstallContract("sample-app","Sample","release-1","1.0",7,
                "de.example.sample",26,36,List.of(),Set.of(),Set.of("a".repeat(64)),"b".repeat(64),
                1,"stable","","https://apkdrop.rawinstinctai.de/sample-app/releases/release-1.apk",
                "https://apkdrop.rawinstinctai.de/sample-app/receipt","https://apkdrop.rawinstinctai.de/sample-app");
        SlowConnection connection=new SlowConnection(new URL(release.downloadUrl));
        ApkDownloader.Cancellation cancellation=new ApkDownloader.Cancellation();
        FutureTask<File> task=new FutureTask<>(()->ApkDownloader.download(context,release,pct->{},cancellation,url->connection));
        Thread worker=new Thread(task,"alpha6-slow-download-test");
        worker.start();
        assertTrue("download did not enter slow read",connection.reading.await(2,TimeUnit.SECONDS));
        File directory=new File(context.getCacheDir(),"apkdrop");
        assertTrue("partial file should exist while streaming",directory.listFiles(file->file.getName().endsWith(".part")).length>0);
        cancellation.cancel();
        worker.interrupt();
        worker.join(2000);
        assertFalse("cancel should release the blocked reader promptly",worker.isAlive());
        assertTrue(connection.disconnected.await(1,TimeUnit.SECONDS));
        assertThrows(ExecutionException.class,task::get);
        File[] remaining=directory.listFiles();
        assertNotNull(remaining);
        assertEquals("partial or late verified APK must not remain",0,remaining.length);
    }
}
