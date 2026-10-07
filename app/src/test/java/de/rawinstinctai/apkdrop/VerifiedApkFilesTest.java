package de.rawinstinctai.apkdrop;

import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import static org.junit.Assert.*;

public final class VerifiedApkFilesTest {
    @Test public void generatedNamesAreUniqueAndAccepted() {
        Set<String> names=new HashSet<>();
        for(int i=0;i<100;i++) {
            String name=VerifiedApkFiles.newName();
            assertTrue(VerifiedApkFiles.allowed(name));
            assertTrue(names.add(name));
        }
    }

    @Test public void pathsLegacyNamesAndPartialDownloadsAreRejected() {
        String valid=VerifiedApkFiles.newName();
        for(String name:new String[]{null,"","verified.apk","verified.apk.part",valid+".part",
                "/"+valid,"../"+valid,"sub/"+valid,valid+"/other",valid.toUpperCase(Locale.ROOT),valid+"?x=1"})
            assertTrue(!VerifiedApkFiles.allowed(name));
    }

    @Test public void prepareRetainsNewestBytesAndRemovesOnlyOwnedFiles() throws Exception {
        File dir=Files.createTempDirectory("apkdrop-cache-test").toFile();
        try {
            File old=write(dir,VerifiedApkFiles.newName(),"older immutable bytes");
            File newest=write(dir,VerifiedApkFiles.newName(),"latest immutable bytes");
            assertTrue(old.setLastModified(1000)); assertTrue(newest.setLastModified(2000));
            File part=write(dir,VerifiedApkFiles.newName()+".part","incomplete");
            File legacy=write(dir,"verified.apk","legacy");
            File legacyPart=write(dir,"verified.apk.part","legacy partial");
            File unrelated=write(dir,"other.txt","keep me");
            VerifiedApkFiles.prepare(dir);
            assertTrue(!old.exists()); assertTrue(!part.exists());
            assertTrue(!legacy.exists()); assertTrue(!legacyPart.exists());
            assertEquals("latest immutable bytes",read(newest)); assertEquals("keep me",read(unrelated));
            File next=write(dir,VerifiedApkFiles.newName(),"different app bytes");
            assertEquals("latest immutable bytes",read(newest));
            assertEquals("different app bytes",read(next));
            assertEquals(3,dir.listFiles().length);
        } finally { remove(dir); }
    }

    @Test public void repeatedPreparationKeepsOnePreviousCompletedDownload() throws Exception {
        File dir=Files.createTempDirectory("apkdrop-retention-test").toFile();
        try {
            for(int i=0;i<5;i++) write(dir,VerifiedApkFiles.newName(),"apk "+i);
            VerifiedApkFiles.prepare(dir); assertEquals(1,dir.listFiles().length);
            VerifiedApkFiles.prepare(dir); assertEquals(1,dir.listFiles().length);
        } finally { remove(dir); }
    }

    @Test public void unavailableCacheFailsClosed() throws Exception {
        File dir=Files.createTempDirectory("apkdrop-missing-cache-test").toFile();
        assertTrue(dir.delete());
        try { VerifiedApkFiles.prepare(dir); fail("Missing cache must fail"); }
        catch(IOException expected) { assertTrue(expected.getMessage().contains("Cache")); }
    }

    private static File write(File dir,String name,String text) throws IOException {
        File file=new File(dir,name); Files.write(file.toPath(),text.getBytes(StandardCharsets.UTF_8)); return file;
    }
    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8);
    }
    private static void remove(File file) {
        File[] children=file.listFiles(); if(children!=null) for(File child:children) remove(child);
        file.delete();
    }
}
