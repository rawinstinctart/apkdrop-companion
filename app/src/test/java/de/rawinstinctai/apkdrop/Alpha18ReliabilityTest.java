package de.rawinstinctai.apkdrop;
import android.content.Context;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=26)
public class Alpha18ReliabilityTest {
    static InstallContract release(String sha) {
        return new InstallContract("sample-app","Sample","release-1","1.0",7,"de.example.sample",26,36,List.of(),Set.of(),Set.of("a".repeat(64)),sha,6,"stable","","https://apkdrop.rawinstinctai.de/sample-app/releases/release-1.apk","https://apkdrop.rawinstinctai.de/sample-app/receipt","https://apkdrop.rawinstinctai.de/sample-app");
    }
    static String hash(byte[] bytes)throws Exception {return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    static class Connection extends HttpURLConnection {
        final int status;final byte[] bytes;final String range;
        Connection(URL url,int status,String range,byte[] bytes){super(url);this.status=status;this.bytes=bytes;this.range=range;}
        public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}
        public int getResponseCode(){return status;}public long getContentLengthLong(){return bytes.length;}
        public String getHeaderField(String key){return "Content-Range".equals(key)?range:null;}
        public InputStream getInputStream(){return new ByteArrayInputStream(bytes);}
    }
    @Before public void clean()throws Exception {
        File dir=new File(RuntimeEnvironment.getApplication().getCacheDir(),"apkdrop");dir.mkdirs();for(File f:dir.listFiles())f.delete();
    }
    @Test public void processRestartResumesBytesAndRehashesEntireFile()throws Exception {
        Context c=RuntimeEnvironment.getApplication();byte[] all="abcdef".getBytes();InstallContract r=release(hash(all));File dir=new File(c.getCacheDir(),"apkdrop");
        Files.write(new File(dir,"resume-"+r.sha256+".part").toPath(),"abc".getBytes());Connection[] seen=new Connection[1];
        File ready=ApkDownloader.download(c,r,p->{},new ApkDownloader.Cancellation(),u->seen[0]=new Connection(u,206,"bytes 3-5/6","def".getBytes()));
        assertEquals("bytes=3-",seen[0].getRequestProperty("Range"));assertArrayEquals(all,Files.readAllBytes(ready.toPath()));assertTrue(VerifiedApkFiles.allowed(ready.getName()));
    }
    @Test public void rangeIgnoredRestartsSafely()throws Exception {
        Context c=RuntimeEnvironment.getApplication();InstallContract r=release(hash("abcdef".getBytes()));Files.write(new File(c.getCacheDir(),"apkdrop/resume-"+r.sha256+".part").toPath(),"abc".getBytes());
        File f=ApkDownloader.download(c,r,p->{},new ApkDownloader.Cancellation(),u->new Connection(u,200,null,"abcdef".getBytes()));assertEquals(6,f.length());
    }
    @Test public void wrongRangeAndCorruptedRetainedBytesFailClosed()throws Exception {
        Context c=RuntimeEnvironment.getApplication();InstallContract r=release(hash("abcdef".getBytes()));File part=new File(c.getCacheDir(),"apkdrop/resume-"+r.sha256+".part");Files.write(part.toPath(),"xxx".getBytes());
        assertThrows(SecurityException.class,()->ApkDownloader.download(c,r,p->{},new ApkDownloader.Cancellation(),u->new Connection(u,206,"bytes 3-5/6","def".getBytes())));assertFalse(part.exists());
        Files.write(part.toPath(),"abc".getBytes());assertThrows(SecurityException.class,()->ApkDownloader.download(c,r,p->{},new ApkDownloader.Cancellation(),u->new Connection(u,206,"bytes 0-2/6","def".getBytes())));assertFalse(part.exists());
    }
    @Test public void networkErrorsRetryButSecurityDoesNot(){assertTrue(UpdateFailure.retryable(new SocketTimeoutException()));assertTrue(UpdateFailure.retryable(new UnknownHostException()));assertFalse(UpdateFailure.retryable(new SecurityException()));assertTrue(UpdateFailure.message(new UnknownHostException()).contains("Offline"));}
    @Test public void queueSurvivesStoreRecreationAndCancellation(){Context c=RuntimeEnvironment.getApplication();AppLibraryStore first=new AppLibraryStore(c);first.queue(new UpdateQueue(List.of("sample-app","other-app")));assertEquals("sample-app",new AppLibraryStore(c).queue().current());first.queue(first.queue().next());assertEquals("other-app",new AppLibraryStore(c).queue().current());}
    @Test public void selfUpdateRejectsForeignIdentity() {InstallContract r=release("b".repeat(64));assertThrows(SecurityException.class,()->CompanionIdentity.require(r,new InstalledState("de.rawinstinctai.apkdrop.debug",23,Set.of("a".repeat(64)),Set.of())));}
    @Test public void dismissingCurrentNotificationRetainsDeduplicationReceipt(){Context c=RuntimeEnvironment.getApplication();c.getSharedPreferences("apkdrop-notifications",0).edit().putString("sample-app","7:hash").commit();UpdateNotifications.remove(c,"sample-app");assertEquals("7:hash",c.getSharedPreferences("apkdrop-notifications",0).getString("sample-app",null));UpdateNotifications.forget(c,"sample-app");assertFalse(c.getSharedPreferences("apkdrop-notifications",0).contains("sample-app"));}
}
