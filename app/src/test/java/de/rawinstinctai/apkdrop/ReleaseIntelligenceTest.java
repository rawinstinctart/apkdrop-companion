package de.rawinstinctai.apkdrop;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public final class ReleaseIntelligenceTest {
    static final String SIGNER="a".repeat(64),HASH="b".repeat(64);
    static InstallContract release(long code,Set<String> permissions,int min,int target,long bytes) {
        return new InstallContract("test-app","Test App","r"+code,"1."+code,code,"de.example.app",min,target,
                List.of("arm64-v8a"),permissions,Set.of(SIGNER),HASH,bytes,"stable","Developer notes",
                "https://apkdrop.rawinstinctai.de/test-app/releases/r"+code+".apk",
                "https://apkdrop.rawinstinctai.de/test-app/receipt","https://apkdrop.rawinstinctai.de/test-app");
    }
    static InstalledState installed() { return new InstalledState("de.example.app",1,"1.1",Set.of(SIGNER),Set.of(),26,35); }
    static InstallPolicy.Result decide(InstallContract release) { return InstallPolicy.evaluate(release,installed(),36,new String[]{"arm64-v8a"}); }
    @Test public void sensitiveUpdateFirst() {
        InstallContract audio=release(2,Set.of("android.permission.RECORD_AUDIO"),26,36,100);
        assertEquals(0,ReleaseIntelligence.priority(decide(audio),null,false));
        assertTrue(ReleaseIntelligence.summary(audio,installed(),decide(audio)).contains("Mikrofonzugriff"));
    }
    @Test public void ordinaryBeforeErrorsAndCurrent() {
        assertEquals(1,ReleaseIntelligence.priority(decide(release(2,Set.of(),26,35,100)),null,false));
        assertEquals(2,ReleaseIntelligence.priority(null,"offline",false));
        assertEquals(5,ReleaseIntelligence.priority(decide(release(1,Set.of(),26,35,100)),null,false));
    }
    @Test public void blockedNeverGetsOrdinaryExplanation() {
        InstallContract release=release(2,Set.of(),40,40,100);
        assertTrue(ReleaseIntelligence.summary(release,installed(),decide(release)).startsWith("Blockiert:"));
    }
    @Test public void newAppNeverClaimsSignerContinuity() {
        InstallContract release=release(2,Set.of(),26,35,100);
        InstallPolicy.Result decision=InstallPolicy.evaluate(release,null,36,new String[]{"arm64-v8a"});
        assertFalse(ReleaseIntelligence.summary(release,null,decision).contains("Gleicher Signierer"));
        assertTrue(ReleaseIntelligence.radar(release,null,null).contains("Kein Vergleich"));
    }
    @Test public void radarUsesInstalledSdkAndHistoricalSizeSeparately() {
        InstallContract next=release(2,Set.of(),26,36,82),previous=release(1,Set.of(),26,35,100);
        String radar=ReleaseIntelligence.radar(next,installed(),previous);
        assertTrue(radar.contains("targetSdk: 35 → 36")); assertTrue(radar.contains("-18,0 %"));
        assertTrue(radar.contains("Gegenüber zuletzt geprüftem Release"));
    }
    @Test public void noInventedBaselineOrNoteCount() {
        String radar=ReleaseIntelligence.radar(release(2,Set.of(),26,36,82),installed(),null);
        assertFalse(radar.contains("APK-Größe:")); assertFalse(radar.contains("Änderungen laut"));
        assertTrue(radar.contains("nach dem Download lokal verifiziert"));
    }
    @Test public void unknownSdkDoesNotClaimUnchanged() {
        InstalledState unknown=new InstalledState("de.example.app",1,Set.of(SIGNER),Set.of());
        InstallContract r=release(2,Set.of(),26,35,100);
        assertFalse(ReleaseIntelligence.summary(r,unknown,decide(r)).contains("Mindestanforderung unverändert"));
    }
    @Test public void removedPermissionsAreCounted() {
        InstalledState installed=new InstalledState("de.example.app",1,Set.of(SIGNER),Set.of("android.permission.CAMERA"));
        assertTrue(ReleaseIntelligence.radar(release(2,Set.of(),26,35,100),installed,null).contains("+0 / −1"));
    }
    @Test public void staleArtifactRejectedBeforeDownload() {
        InstallContract original=release(2,Set.of(),26,35,100),changed=release(2,Set.of("android.permission.CAMERA"),26,35,100);
        assertThrows(SecurityException.class,()->original.requireSameArtifact(changed));
    }
    @Test public void unchangedArtifactAccepted() { InstallContract r=release(2,Set.of(),26,35,100); r.requireSameArtifact(r); }
}
