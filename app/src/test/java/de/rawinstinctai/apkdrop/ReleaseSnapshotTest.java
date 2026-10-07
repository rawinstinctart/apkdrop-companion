package de.rawinstinctai.apkdrop;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public final class ReleaseSnapshotTest {
    private static final long NOW=System.currentTimeMillis();
    private InstallContract release(long code) { return ReleaseIntelligenceTest.release(code,Set.of(),26,35,100); }
    private AppLibrary.Entry pin() { return new AppLibrary.Entry("test-app","Test App","de.example.app",Set.of(ReleaseIntelligenceTest.SIGNER)); }
    @Test public void contractRoundTripPreservesFullIdentity() throws Exception {
        InstallContract r=release(2),parsed=InstallContract.parse(r.json().toString()); r.requireSameArtifact(parsed);
        assertEquals(r.notes,parsed.notes); assertEquals(r.receiptUrl,parsed.receiptUrl);
    }
    @Test public void snapshotSurvivesRestartWithTimestamp() throws Exception {
        ReleaseSnapshot snapshot=new ReleaseSnapshot(release(2),release(1),NOW,null,false);
        ReleaseSnapshot restored=ReleaseSnapshot.decode(snapshot.encode(),pin(),NOW);
        assertEquals(NOW,restored.checkedAt); assertEquals(2,restored.release.versionCode); assertEquals(1,restored.previous.versionCode);
    }
    @Test public void failureKeepsFactsButMarksError() throws Exception {
        ReleaseSnapshot next=new ReleaseSnapshot(release(2),null,NOW,null,false).failed("offline",false,NOW);
        ReleaseSnapshot restored=ReleaseSnapshot.decode(next.encode(),pin(),NOW);
        assertEquals("offline",restored.error); assertEquals(2,restored.release.versionCode);
    }
    @Test public void sameReleaseKeepsPreviousBaseline() {
        ReleaseSnapshot snapshot=new ReleaseSnapshot(release(2),release(1),NOW,null,false).success(release(2),NOW);
        assertEquals(1,snapshot.previous.versionCode);
    }
    @Test public void newerReleaseMovesBaseline() {
        ReleaseSnapshot snapshot=new ReleaseSnapshot(release(2),release(1),NOW,null,false).success(release(3),NOW);
        assertEquals(2,snapshot.previous.versionCode);
    }
    @Test public void restoredSignerMismatchBlocked() throws Exception {
        ReleaseSnapshot snapshot=new ReleaseSnapshot(release(2),null,NOW,null,false);
        AppLibrary.Entry changed=new AppLibrary.Entry("test-app","Test App","de.example.app",Set.of("c".repeat(64)));
        assertThrows(SecurityException.class,()->ReleaseSnapshot.decode(snapshot.encode(),changed,NOW));
    }
    @Test public void corruptOrOversizedSnapshotRejected() {
        assertThrows(Exception.class,()->ReleaseSnapshot.decode("broken",pin(),NOW));
        assertThrows(Exception.class,()->ReleaseSnapshot.decode("x".repeat(280001),pin(),NOW));
    }
    @Test public void futureTimestampRejected() throws Exception {
        ReleaseSnapshot snapshot=new ReleaseSnapshot(release(2),null,NOW+600000,null,false);
        assertThrows(Exception.class,()->ReleaseSnapshot.decode(snapshot.encode(),pin(),NOW));
    }
    @Test public void errorOnlySnapshotRoundTrip() throws Exception {
        ReleaseSnapshot snapshot=new ReleaseSnapshot(null,null,NOW,"no release",true);
        ReleaseSnapshot restored=ReleaseSnapshot.decode(snapshot.encode(),pin(),NOW);
        assertNull(restored.release); assertTrue(restored.blocked);
    }
}
