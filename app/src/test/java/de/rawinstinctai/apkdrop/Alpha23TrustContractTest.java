package de.rawinstinctai.apkdrop;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public final class Alpha23TrustContractTest {
    @Test public void serverEvidenceDoesNotPretendAnApkWasVerifiedOnThisDevice() throws Exception {
        JSONObject release=new JSONObject().put("signatureVerified",true)
                .put("trust",new JSONObject().put("schema","apkdrop.trust.v1").put("status","documented"));
        String shown=DropTrust.catalog(release,ReleaseRadar.State.NOT_INSTALLED);
        assertTrue(shown.contains("Geräteprüfung vor Installation"));
        assertFalse(shown.contains("lokal bestätigt"));
        release.getJSONObject("trust").put("status","incomplete");
        assertTrue(DropTrust.catalog(release,ReleaseRadar.State.NOT_INSTALLED).contains("unvollständig"));
        assertTrue(DropTrust.catalog(release,ReleaseRadar.State.DIFFERENT_SIGNER).contains("Abweichende Signatur"));
    }
}
