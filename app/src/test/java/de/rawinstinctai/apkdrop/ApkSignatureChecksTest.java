package de.rawinstinctai.apkdrop;

import com.android.apksig.ApkSigner;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.*;
import static org.junit.Assert.*;

/** Real v2 signatures and modified bytes; no mocked signature-verification result. */
public final class ApkSignatureChecksTest {
    private static final byte[] PAYLOAD="APKDROP_TEST_ONLY_UNIQUE_PAYLOAD_0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
    private static Path dir;
    private static File signed, unsigned, tampered, truncated;
    private static Set<String> signers;
    private static String originalHash;

    @BeforeClass public static void createSignedFixture() throws Exception {
        dir=Files.createTempDirectory("apkdrop-signature-tests-");
        Path store=dir.resolve("test-only.p12"),log=dir.resolve("keytool.log");
        String executable=System.getProperty("os.name").startsWith("Windows")?"keytool.exe":"keytool";
        Process keytool=new ProcessBuilder(Paths.get(System.getProperty("java.home"),"bin",executable).toString(),
                "-genkeypair","-noprompt","-alias","fixture","-keyalg","RSA","-keysize","2048",
                "-sigalg","SHA256withRSA","-validity","2","-dname","CN=APKDrop disposable test only",
                "-storetype","PKCS12","-storepass","test-only-password","-keypass","test-only-password",
                "-keystore",store.toString()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if(!keytool.waitFor(30,TimeUnit.SECONDS)) {
            keytool.destroyForcibly();
            throw new AssertionError("Test-only keytool fixture generation timed out");
        }
        assertEquals("keytool: "+Files.readString(log),0,keytool.exitValue());

        KeyStore keys=KeyStore.getInstance("PKCS12");
        try(InputStream in=Files.newInputStream(store)) { keys.load(in,"test-only-password".toCharArray()); }
        PrivateKey key=(PrivateKey)keys.getKey("fixture","test-only-password".toCharArray());
        X509Certificate certificate=(X509Certificate)keys.getCertificate("fixture");
        signers=Set.of(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded())));

        byte[] manifest;
        try(InputStream in=ApkSignatureChecksTest.class.getResourceAsStream("/fixture-manifest.base64")) {
            assertNotNull("The binary manifest fixture must be present",in);
            manifest=Base64.getMimeDecoder().decode(in.readAllBytes());
        }
        unsigned=dir.resolve("unsigned-test-only.apk").toFile();
        try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(unsigned))) {
            zip.putNextEntry(new ZipEntry("AndroidManifest.xml")); zip.write(manifest); zip.closeEntry();
            CRC32 crc=new CRC32(); crc.update(PAYLOAD);
            ZipEntry asset=new ZipEntry("assets/integrity-test.txt");
            asset.setMethod(ZipEntry.STORED); asset.setSize(PAYLOAD.length); asset.setCrc(crc.getValue());
            zip.putNextEntry(asset); zip.write(PAYLOAD); zip.closeEntry();
        }
        signed=dir.resolve("signed-test-only.apk").toFile();
        ApkSigner.SignerConfig signer=new ApkSigner.SignerConfig.Builder("fixture",key,Collections.singletonList(certificate)).build();
        new ApkSigner.Builder(Collections.singletonList(signer)).setInputApk(unsigned).setOutputApk(signed)
                .setMinSdkVersion(26).setV1SigningEnabled(false).setV2SigningEnabled(true)
                .setV3SigningEnabled(false).setV4SigningEnabled(false).build().sign();
        originalHash=ApkSignatureChecks.sha256(signed);

        byte[] bytes=Files.readAllBytes(signed.toPath());
        int offset=findPayload(bytes);
        assertTrue("Stored test payload must exist",offset>=0);
        bytes[offset]^=1;
        tampered=dir.resolve("tampered-test-only.apk").toFile(); Files.write(tampered.toPath(),bytes);
        assertEquals(signed.length(),tampered.length());
        truncated=dir.resolve("truncated-test-only.apk").toFile();
        Files.write(truncated.toPath(),Arrays.copyOf(bytes,bytes.length-1));

        // Positive control must succeed before any negative result is accepted as evidence.
        assertEquals(signers,ApkSignatureChecks.verify(signed,signed.length(),originalHash,signers,26));
    }

    @AfterClass public static void removeFixtures() throws Exception {
        if(dir==null) return;
        try(Stream<Path> paths=Files.walk(dir)) {
            for(Path path:(Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator) Files.deleteIfExists(path);
        }
    }

    @Test public void genuineV2SignaturePassesForSupportedPlatforms() throws Exception {
        for(int sdk:new int[]{26,28,36})
            assertEquals(signers,ApkSignatureChecks.verify(signed,signed.length(),originalHash,signers,sdk));
    }

    @Test public void incorrectSizeIsBlocked() throws Exception {
        blocked(signed,signed.length()+1,originalHash,signers,"APK-Größe stimmt nicht.");
    }

    @Test public void truncatedDownloadIsBlocked() throws Exception {
        blocked(truncated,signed.length(),originalHash,signers,"APK-Größe stimmt nicht.");
    }

    @Test public void wrongHashIsBlocked() throws Exception {
        blocked(signed,signed.length(),"0".repeat(64),signers,"SHA-256 stimmt nicht.");
    }

    @Test public void sameSizeTamperingIsBlockedByHash() throws Exception {
        blocked(tampered,signed.length(),originalHash,signers,"SHA-256 stimmt nicht.");
    }

    @Test public void tamperingWithUpdatedHashStillFailsCryptographicVerification() throws Exception {
        blocked(tampered,tampered.length(),ApkSignatureChecks.sha256(tampered),signers,
                "APK-Signatur ist nicht kryptografisch gültig.");
    }

    @Test public void unsignedApkWithMatchingHashIsBlocked() throws Exception {
        blocked(unsigned,unsigned.length(),ApkSignatureChecks.sha256(unsigned),signers,
                "APK-Signatur ist nicht kryptografisch gültig.");
    }

    @Test public void genuineSignatureFromUnexpectedSignerIsBlocked() throws Exception {
        blocked(signed,signed.length(),originalHash,Set.of("0".repeat(64)),
                "APK-Signatur stimmt nicht mit APKDrop überein.");
    }

    @Test public void additionalExpectedSignerIsNotTreatedAsPartialMatch() throws Exception {
        Set<String> extra=new LinkedHashSet<>(signers); extra.add("0".repeat(64));
        blocked(signed,signed.length(),originalHash,extra,"APK-Signatur stimmt nicht mit APKDrop überein.");
    }

    private static void blocked(File file,long size,String hash,Set<String> expected,String reason) throws Exception {
        try { ApkSignatureChecks.verify(file,size,hash,expected,26); }
        catch(SecurityException rejected) { assertEquals(reason,rejected.getMessage()); return; }
        fail("Expected verification to block: "+reason);
    }

    private static int findPayload(byte[] bytes) {
        outer: for(int i=0;i<=bytes.length-PAYLOAD.length;i++) {
            for(int j=0;j<PAYLOAD.length;j++) if(bytes[i+j]!=PAYLOAD[j]) continue outer;
            return i;
        }
        return -1;
    }
}
