package de.rawinstinctai.apkdrop;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class InstallPolicyTest {
    private static final String FP="a".repeat(64);

    private InstallContract release(long code,int minSdk,List<String> abis,String... permissions) {
        return new InstallContract("sample-app","Sample App","r1","1.2.0",code,"dev.sample.app",
                minSdk,36,abis,new LinkedHashSet<>(List.of(permissions)),Set.of(FP),"b".repeat(64),
                4096,"stable","","https://apkdrop.rawinstinctai.de/sample-app/releases/r1.apk",
                "https://apkdrop.rawinstinctai.de/sample-app/receipts/r1","https://apkdrop.rawinstinctai.de/sample-app");
    }

    @Test public void newSensitivePermissionIsVisible() {
        InstallPolicy.Result result=InstallPolicy.evaluate(release(2,26,Collections.emptyList(),"android.permission.CAMERA"),
                null,36,new String[]{"arm64-v8a"});
        assertEquals(InstallPolicy.Mode.INSTALL,result.mode);
        assertTrue(result.sensitiveAdded.contains("android.permission.CAMERA"));
    }

    @Test public void exactSignerUpdateIsAllowed() {
        InstalledState installed=new InstalledState("dev.sample.app",1,Set.of(FP),Collections.emptySet());
        assertEquals(InstallPolicy.Mode.UPDATE,
                InstallPolicy.evaluate(release(2,26,Collections.emptyList()),installed,36,new String[]{"arm64-v8a"}).mode);
    }

    @Test public void signerMismatchIsBlocked() {
        InstalledState installed=new InstalledState("dev.sample.app",1,Set.of("c".repeat(64)),Collections.emptySet());
        assertEquals(InstallPolicy.Mode.BLOCKED,
                InstallPolicy.evaluate(release(2,26,Collections.emptyList()),installed,36,new String[]{"arm64-v8a"}).mode);
    }

    @Test public void downgradeIsNeverOffered() {
        InstalledState installed=new InstalledState("dev.sample.app",3,Set.of(FP),Collections.emptySet());
        assertEquals(InstallPolicy.Mode.CURRENT,
                InstallPolicy.evaluate(release(2,26,Collections.emptyList()),installed,36,new String[]{"arm64-v8a"}).mode);
    }

    @Test public void sameNumericVersionIsCurrent() {
        InstalledState installed=new InstalledState("dev.sample.app",2,Set.of(FP),Collections.emptySet());
        InstallPolicy.Result result=InstallPolicy.evaluate(release(2,26,Collections.emptyList()),installed,36,new String[]{"arm64-v8a"});
        assertEquals(InstallPolicy.Mode.CURRENT,result.mode);
        assertEquals("Diese Version ist bereits installiert.",result.reason);
        assertTrue(result.addedPermissions.isEmpty());
    }

    @Test public void downgradeExplainsThatInstalledVersionIsNewer() {
        InstalledState installed=new InstalledState("dev.sample.app",3,Set.of(FP),Collections.emptySet());
        InstallPolicy.Result result=InstallPolicy.evaluate(release(2,26,Collections.emptyList()),installed,36,new String[]{"arm64-v8a"});
        assertEquals(InstallPolicy.Mode.CURRENT,result.mode);
        assertEquals("Auf deinem Gerät ist bereits eine neuere Version installiert.",result.reason);
    }

    @Test public void changedSignerIsBlockedEvenForCurrentVersion() {
        InstalledState installed=new InstalledState("dev.sample.app",2,Set.of("c".repeat(64)),Collections.emptySet());
        assertEquals(InstallPolicy.Mode.BLOCKED,
                InstallPolicy.evaluate(release(2,26,Collections.emptyList()),installed,36,new String[]{"arm64-v8a"}).mode);
    }

    @Test public void additionalSignerIsBlocked() {
        InstallContract release=release(2,26,Collections.emptyList());
        InstalledState installed=new InstalledState("dev.sample.app",1,Set.of(FP,"c".repeat(64)),Collections.emptySet());
        assertEquals(InstallPolicy.Mode.BLOCKED,
                InstallPolicy.evaluate(release,installed,36,new String[]{"arm64-v8a"}).mode);
    }

    @Test public void differentPackageIsBlocked() {
        InstalledState installed=new InstalledState("dev.other.app",1,Set.of(FP),Collections.emptySet());
        assertEquals(InstallPolicy.Mode.BLOCKED,
                InstallPolicy.evaluate(release(2,26,Collections.emptyList()),installed,36,new String[]{"arm64-v8a"}).mode);
    }

    @Test public void updateWarnsOnlyAboutNewSensitivePermissions() {
        Set<String> existing=Set.of("android.permission.INTERNET","android.permission.CAMERA");
        InstalledState installed=new InstalledState("dev.sample.app",1,Set.of(FP),existing);
        InstallPolicy.Result result=InstallPolicy.evaluate(release(2,26,Collections.emptyList(),
                "android.permission.INTERNET","android.permission.CAMERA","android.permission.RECORD_AUDIO",
                "android.permission.WAKE_LOCK"),installed,36,new String[]{"arm64-v8a"});
        assertEquals(InstallPolicy.Mode.UPDATE,result.mode);
        assertEquals(Set.of("android.permission.RECORD_AUDIO","android.permission.WAKE_LOCK"),result.addedPermissions);
        assertEquals(Set.of("android.permission.RECORD_AUDIO"),result.sensitiveAdded);
        assertEquals(Set.of("android.permission.INTERNET","android.permission.CAMERA"),existing);
    }

    @Test public void removedPermissionsAreNotNewPermissions() {
        InstalledState installed=new InstalledState("dev.sample.app",1,Set.of(FP),Set.of("android.permission.CAMERA"));
        InstallPolicy.Result result=InstallPolicy.evaluate(release(2,26,Collections.emptyList()),installed,36,new String[]{"arm64-v8a"});
        assertEquals(InstallPolicy.Mode.UPDATE,result.mode);
        assertTrue(result.addedPermissions.isEmpty());
        assertTrue(result.sensitiveAdded.isEmpty());
    }

    @Test public void longVersionCodesAreComparedWithoutTruncation() {
        long code=(long)Integer.MAX_VALUE+10;
        InstalledState installed=new InstalledState("dev.sample.app",code,Set.of(FP),Collections.emptySet());
        assertEquals(InstallPolicy.Mode.UPDATE,
                InstallPolicy.evaluate(release(code+1,26,Collections.emptyList()),installed,36,new String[]{"arm64-v8a"}).mode);
        assertEquals(InstallPolicy.Mode.CURRENT,
                InstallPolicy.evaluate(release(code,26,Collections.emptyList()),installed,36,new String[]{"arm64-v8a"}).mode);
    }

    @Test public void sdkAndAbiMismatchesAreBlocked() {
        InstalledState installed=new InstalledState("dev.sample.app",1,Set.of(FP),Collections.emptySet());
        assertEquals(InstallPolicy.Mode.BLOCKED,
                InstallPolicy.evaluate(release(2,37,Collections.emptyList()),installed,36,new String[]{"arm64-v8a"}).mode);
        assertEquals(InstallPolicy.Mode.BLOCKED,
                InstallPolicy.evaluate(release(2,26,List.of("x86_64")),installed,36,new String[]{"arm64-v8a"}).mode);
    }
}
