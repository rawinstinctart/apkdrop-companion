package de.rawinstinctai.apkdrop;

import org.junit.Test;
import static org.junit.Assert.*;

/** Tests the identity producer used by inspect() for the always-visible summary. */
public final class LocalApkInspectorTest {
    @Test public void signerMismatchIdentityIncludesUpdateInstruction() {
        String identity=LocalApkInspector.signerIdentity(true,false);
        assertEquals("⚠ Signatur weicht von installierter App ab. Nicht als normales Update verwenden",identity);
        assertTrue(identity.contains("Nicht als normales Update verwenden"));
        assertFalse(identity.contains("stimmt mit"));
    }

    @Test public void matchingSignerIdentityConfirmsInstalledApp() {
        String identity=LocalApkInspector.signerIdentity(true,true);
        assertEquals("✓ Signatur stimmt mit installierter App überein",identity);
        assertTrue(identity.contains("stimmt mit installierter App überein"));
        assertFalse(identity.contains("Nicht als normales Update"));
    }

    @Test public void missingInstalledAppIdentityDoesNotClaimComparison() {
        for(boolean signersEqual:new boolean[]{false,true}) {
            String identity=LocalApkInspector.signerIdentity(false,signersEqual);
            assertEquals("○ Kein installierter Vergleich verfügbar",identity);
            assertFalse(identity.contains("stimmt mit"));
            assertFalse(identity.contains("Nicht als normales Update"));
        }
    }
}
