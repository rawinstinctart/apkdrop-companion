package de.rawinstinctai.apkdrop;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.List;
import java.util.Set;
import org.json.JSONObject;

public class ReleaseSummaryContractTest {
    @Test public void summaryRoundtripRemainsDisplayOnly() throws Exception {
        InstallContract original=new InstallContract(
                "example","Example","release-1","1.2.0",12L,"de.example.app",
                26,36,List.of(),Set.of(),Set.of("a".repeat(64)),
                "b".repeat(64),1234L,"stable","- Feature eins\n- Technische Details",
                "https://apkdrop.rawinstinctai.de/example/releases/release-1.apk",
                "https://apkdrop.rawinstinctai.de/example/receipts/release-1",
                "https://apkdrop.rawinstinctai.de/example"
        );
        JSONObject wire=original.json();
        wire.getJSONObject("release").put("notesSummary","Feature eins in einem Satz.");
        InstallContract parsed=InstallContract.parse(wire.toString());
        assertEquals("Feature eins in einem Satz.",parsed.notesSummary);
        assertEquals(original.notes,parsed.notes);
        assertEquals(parsed.notesSummary,InstallContract.parse(parsed.json().toString()).notesSummary);
        parsed.requireSameArtifact(original); // Editable copy is never an APK identity field.
    }

    @Test public void olderBackendsKeepWorkingAndSummarySizeIsBounded() throws Exception {
        InstallContract original=new InstallContract(
                "example","Example","release-1","1.2.0",12L,"de.example.app",
                26,36,List.of(),Set.of(),Set.of("a".repeat(64)),
                "b".repeat(64),1234L,"stable","- Neu",
                "https://apkdrop.rawinstinctai.de/example/releases/release-1.apk",
                "https://apkdrop.rawinstinctai.de/example/receipts/release-1",
                "https://apkdrop.rawinstinctai.de/example"
        );
        JSONObject wire=original.json();
        wire.getJSONObject("release").remove("notesSummary");
        assertEquals("",InstallContract.parse(wire.toString()).notesSummary);
        wire.getJSONObject("release").put("notesSummary","x".repeat(801));
        assertThrows(SecurityException.class,()->InstallContract.parse(wire.toString()));
    }
}
