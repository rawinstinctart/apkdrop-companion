package de.rawinstinctai.apkdrop;

import org.junit.Test;
import java.util.Set;
import static org.junit.Assert.*;

public final class Alpha29DropTrustTest {
    private InstallPolicy.Result result(InstallPolicy.Mode mode,String why,Set<String> sensitive) {
        return new InstallPolicy.Result(mode,why,Set.of(),sensitive);
    }
    @Test public void securityBlockNeverClaimsSuccessfulUpdate() {
        String guidance=DropTrustGuidance.nextStep(result(InstallPolicy.Mode.BLOCKED,"Signatur stimmt nicht",Set.of()),false);
        assertTrue(guidance.contains("gesperrt"));
        assertTrue(guidance.contains("unverändert"));
        assertFalse(guidance.contains("Bereit zum Installieren"));
    }
    @Test public void storedSnapshotCannotClaimFreshVerification() {
        assertTrue(DropTrustGuidance.nextStep(result(InstallPolicy.Mode.UPDATE,"ok",Set.of()),true).contains("erneut prüfen"));
    }
    @Test public void sensitivePermissionsAlwaysRequireDetails() {
        assertTrue(DropTrustGuidance.nextStep(result(InstallPolicy.Mode.UPDATE,"ok",Set.of("android.permission.CAMERA")),false).contains("Berechtigungen"));
    }
    @Test public void policyStatesAreExplainedWithoutInstalling() {
        assertTrue(DropTrustGuidance.nextStep(result(InstallPolicy.Mode.CURRENT,"Installiert",Set.of()),false).contains("Aktuell"));
        assertTrue(DropTrustGuidance.nextStep(result(InstallPolicy.Mode.UPDATE,"ok",Set.of()),false).contains("Android bestätigen"));
        assertTrue(DropTrustGuidance.nextStep(null,false).contains("Prüfung ausstehend"));
    }
    @Test public void interruptedCheckPreservesUserTrust() {
        assertTrue(DropTrustGuidance.recovery(new java.net.UnknownHostException()).contains("bleibt erhalten"));
        assertTrue(DropTrustGuidance.recovery(new SecurityException("bad signer")).contains("Keine Installation"));
    }
}
