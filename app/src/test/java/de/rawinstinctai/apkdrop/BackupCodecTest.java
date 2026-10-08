package de.rawinstinctai.apkdrop;

import android.content.*;
import java.util.*;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import static org.junit.Assert.*;

@RunWith(org.robolectric.RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk=26)
public class BackupCodecTest {
    private Context ctx;
    private final char[] password="10plus-char-password-123".toCharArray();
    private AppLibrary.Entry entry(String signer) {
        return new AppLibrary.Entry("backup-app","Backup App","de.example.backup",Set.of(signer));
    }
    @Before public void setup() {
        ctx=RuntimeEnvironment.getApplication();
        for(String pref:List.of("apkdrop-library","developer-follows-v1","apkdrop-updates","apkdrop-droppilot"))
            ctx.getSharedPreferences(pref,Context.MODE_PRIVATE).edit().clear().commit();
        DropPilot.enabled(ctx,false);
    }
    @After public void cleanup(){DropPilot.enabled(ctx,false);}
    @Test public void encryptedRoundTripRequiresApprovalBeforeWriting() throws Exception {
        AppLibraryStore local=new AppLibraryStore(ctx);
        local.save(new AppLibrary().add(entry("a".repeat(64))));
        new DeveloperFollows(ctx).toggle("12345","my-dev","My Developer");
        String backup=BackupCodec.export(ctx,password);
        assertFalse(backup.contains("de.example.backup"));
        assertFalse(backup.contains("my-dev"));
        local.save(new AppLibrary());
        new DeveloperFollows(ctx).remove("12345");
        BackupCodec.Plan plan=BackupCodec.preview(backup,password);
        assertEquals(1,plan.apps);assertEquals(1,plan.follows);
        assertNull(local.read().find("backup-app"));
        BackupCodec.apply(ctx,plan);
        assertNotNull(local.read().find("backup-app"));
        assertTrue(new DeveloperFollows(ctx).contains("12345"));
    }
    @Test public void wrongPasswordAndTamperingCannotRestore() throws Exception {
        String backup=BackupCodec.export(ctx,password);
        assertThrows(SecurityException.class,()->BackupCodec.preview(backup,"a-long-wrong-password".toCharArray()));
        JSONObject data=new JSONObject(backup),copy=new JSONObject(backup);
        String cipher=data.getString("ciphertext");
        char changed=cipher.charAt(1)=='A'?'B':'A';
        copy.put("ciphertext",cipher.substring(0,1)+changed+cipher.substring(2));
        assertThrows(SecurityException.class,()->BackupCodec.preview(copy.toString(),password));
    }
    @Test public void signerConflictRejectsMergeWithoutOverwritingLocalApp() throws Exception {
        AppLibraryStore local=new AppLibraryStore(ctx);
        local.save(new AppLibrary().add(entry("a".repeat(64))));
        BackupCodec.Plan backup=BackupCodec.preview(BackupCodec.export(ctx,password),password);
        local.save(new AppLibrary().add(entry("c".repeat(64))));
        assertThrows(SecurityException.class,()->BackupCodec.apply(ctx,backup));
        assertEquals(Set.of("c".repeat(64)),local.read().find("backup-app").signers);
    }
    @Test public void invalidPasswordAndUnsupportedFormatFailClosed() throws Exception {
        assertThrows(IllegalArgumentException.class,()->BackupCodec.export(ctx,"short".toCharArray()));
        assertThrows(SecurityException.class,()->BackupCodec.preview("{\"schema\":\"other\",\"iterations\":1} ",password));
    }
}
