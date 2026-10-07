package de.rawinstinctai.apkdrop;

import org.junit.Test;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

public final class AppLibraryTest {
    private static final String SIGNER="a".repeat(64);
    private static AppLibrary.Entry entry(String slug,String pkg) {
        return new AppLibrary.Entry(slug,"App "+slug,pkg,Set.of(SIGNER));
    }

    @Test public void emptyAndMissingStorageRoundTrip() throws Exception {
        assertTrue(AppLibrary.decode(null).entries().isEmpty());
        assertTrue(AppLibrary.decode(new AppLibrary().encode()).entries().isEmpty());
    }

    @Test public void savedAppsSurviveRoundTripWithOrderAndExactIdentity() throws Exception {
        AppLibrary original=new AppLibrary().add(entry("first-app","dev.first.app"))
                .add(new AppLibrary.Entry("second-app","Nähe 🥰\nFreundschaft","dev.second.app",Set.of(SIGNER,"b".repeat(64))));
        AppLibrary restored=AppLibrary.decode(original.encode());
        assertEquals("first-app",restored.entries().get(0).slug);
        assertEquals("Nähe 🥰\nFreundschaft",restored.entries().get(1).name);
        assertEquals("dev.second.app",restored.entries().get(1).packageName);
        assertEquals(Set.of(SIGNER,"b".repeat(64)),restored.entries().get(1).signers);
    }

    @Test public void repeatedAdditionRenamesWithoutDuplicatingApp() {
        AppLibrary library=new AppLibrary().add(entry("sample-app","dev.sample.app"));
        AppLibrary next=library.add(new AppLibrary.Entry("sample-app","New name","dev.sample.app",Set.of(SIGNER)));
        assertEquals(1,next.entries().size()); assertEquals("New name",next.find("sample-app").name);
        assertEquals("App sample-app",library.find("sample-app").name);
    }

    @Test public void removingOnlyChangesTheSelectedSavedEntry() throws Exception {
        AppLibrary original=new AppLibrary().add(entry("first-app","dev.first.app")).add(entry("second-app","dev.second.app"));
        AppLibrary next=AppLibrary.decode(original.remove("first-app").encode());
        assertEquals(2,original.entries().size()); assertEquals(1,next.entries().size());
        assertEquals("second-app",next.entries().get(0).slug);
        assertEquals(1,next.remove("missing-app").entries().size());
    }

    @Test public void aliasesCannotDuplicateTheSamePackage() throws Exception {
        AppLibrary original=new AppLibrary().add(entry("sample-app","dev.sample.app"));
        rejects(IllegalArgumentException.class,()->original.add(entry("other-app","dev.sample.app")));
    }

    @Test public void packageOrSignerChangeCannotReplaceSavedIdentity() throws Exception {
        AppLibrary original=new AppLibrary().add(entry("sample-app","dev.sample.app"));
        rejects(SecurityException.class,()->original.add(entry("sample-app","dev.changed.app")));
        rejects(SecurityException.class,()->original.add(new AppLibrary.Entry("sample-app","Same app","dev.sample.app",Set.of("b".repeat(64)))));
    }

    @Test public void fetchedIdentityMustMatchSlugPackageAndFullSignerSet() throws Exception {
        AppLibrary.Entry saved=entry("sample-app","dev.sample.app");
        saved.requireIdentity("sample-app","dev.sample.app",Set.of(SIGNER));
        rejects(SecurityException.class,()->saved.requireIdentity("other-app","dev.sample.app",Set.of(SIGNER)));
        rejects(SecurityException.class,()->saved.requireIdentity("sample-app","dev.other.app",Set.of(SIGNER)));
        rejects(SecurityException.class,()->saved.requireIdentity("sample-app","dev.sample.app",Set.of(SIGNER,"b".repeat(64))));
    }

    @Test public void maximumListSizeIsEnforcedBeforePersistence() throws Exception {
        AppLibrary result=new AppLibrary();
        for(int i=0;i<AppLibrary.MAX_APPS;i++) result=result.add(entry("sample-"+i,"dev.sample.app"+i));
        assertEquals(AppLibrary.MAX_APPS,AppLibrary.decode(result.encode()).entries().size());
        final AppLibrary full=result;
        rejects(IllegalArgumentException.class,()->full.add(entry("overflow-app","dev.overflow.app")));
    }

    @Test public void invalidAndOversizedEntriesAreRejected() throws Exception {
        rejects(IllegalArgumentException.class,()->entry("../escape","dev.sample.app"));
        rejects(IllegalArgumentException.class,()->entry("sample-app","invalid"));
        rejects(IllegalArgumentException.class,()->new AppLibrary.Entry("sample-app","x".repeat(161),"dev.sample.app",Set.of(SIGNER)));
        rejects(IllegalArgumentException.class,()->new AppLibrary.Entry("sample-app","Sample","dev.sample.app",Set.of()));
        rejects(IllegalArgumentException.class,()->new AppLibrary.Entry("sample-app","Sample","dev.sample.app",Set.of("bad")));
    }

    @Test public void malformedVersionCountAndBase64FailClosed() throws Exception {
        rejects(IOException.class,()->AppLibrary.decode("unknown-format"));
        rejects(IOException.class,()->AppLibrary.decode("apkdrop.library.v1:!bad!"));
        rejects(IOException.class,()->AppLibrary.decode(raw(2,0)));
        rejects(IOException.class,()->AppLibrary.decode(raw(1,-1)));
        rejects(IOException.class,()->AppLibrary.decode(raw(1,51)));
        rejects(IOException.class,()->AppLibrary.decode("x".repeat(100001)));
    }

    @Test public void truncatedAndTrailingDataAreNotSilentlyAccepted() throws Exception {
        String encoded=new AppLibrary().add(entry("sample-app","dev.sample.app")).encode();
        byte[] bytes=Base64.getDecoder().decode(encoded.substring("apkdrop.library.v1:".length()));
        rejects(IOException.class,()->AppLibrary.decode(pack(Arrays.copyOf(bytes,bytes.length-1))));
        rejects(IOException.class,()->AppLibrary.decode(pack(Arrays.copyOf(bytes,bytes.length+1))));
    }

    @Test public void duplicateSerializedSlugsAreRejectedInsteadOfMerged() throws Exception {
        rejects(IOException.class,()->AppLibrary.decode(raw(1,2,entry("sample-app","dev.sample.app"),entry("sample-app","dev.sample.app"))));
    }

    @Test public void callerCannotMutateSavedCollections() throws Exception {
        Set<String> signers=new HashSet<>(Set.of(SIGNER));
        AppLibrary.Entry entry=new AppLibrary.Entry("sample-app","Sample","dev.sample.app",signers);
        signers.clear(); assertEquals(Set.of(SIGNER),entry.signers);
        AppLibrary saved=new AppLibrary().add(entry);
        rejects(UnsupportedOperationException.class,()->saved.entries().clear());
        rejects(UnsupportedOperationException.class,()->entry.signers.clear());
    }

    private interface Operation { void run() throws Exception; }
    private static void rejects(Class<? extends Exception> expected,Operation operation) throws Exception {
        try { operation.run(); } catch(Exception e) { if(expected.isInstance(e)) return; throw e; }
        fail("Expected rejection: "+expected.getSimpleName());
    }
    private static String raw(int version,int count,AppLibrary.Entry... entries) throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(DataOutputStream out=new DataOutputStream(bytes)) {
            out.writeInt(version); out.writeInt(count);
            for(AppLibrary.Entry entry:entries) {
                out.writeUTF(entry.slug); out.writeUTF(entry.name); out.writeUTF(entry.packageName);
                out.writeInt(entry.signers.size()); for(String signer:entry.signers) out.writeUTF(signer);
            }
        }
        return pack(bytes.toByteArray());
    }
    private static String pack(byte[] bytes) { return "apkdrop.library.v1:"+Base64.getEncoder().encodeToString(bytes); }
}
