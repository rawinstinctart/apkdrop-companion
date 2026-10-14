package de.rawinstinctai.apkdrop;

import org.junit.Test;
import java.util.Set;
import static org.junit.Assert.*;

/** Alpha 34 summary must distinguish evidence from publisher trust. */
public final class LocalApkReportTest {
    @Test public void conciseReportKeepsSeparateTechnicalEvidence() {
        LocalApkInspector.Report report=new LocalApkInspector.Report(
                "✓ APK-Signatur gültig\nDropPass-Herkunft: noch nicht geprüft",
                "SHA-256 der APK: example\nSignaturzertifikat SHA-256: reference");
        assertFalse(report.summary.contains("Signaturzertifikat"));
        assertTrue(report.summary.contains("noch nicht geprüft"));
        assertTrue(report.details.contains("SHA-256"));
    }

    @Test public void newPermissionCalculationDoesNotMutateInputs() {
        Set<String> declared=Set.of("android.permission.INTERNET","android.permission.CAMERA");
        Set<String> existing=Set.of("android.permission.INTERNET");
        assertEquals(Set.of("android.permission.CAMERA"),
                LocalApkInspector.newPermissions(declared,existing));
        assertEquals(2,declared.size());
        assertEquals(1,existing.size());
    }
}
