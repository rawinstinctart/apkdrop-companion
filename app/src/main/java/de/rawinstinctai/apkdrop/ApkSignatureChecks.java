package de.rawinstinctai.apkdrop;

import com.android.apksig.ApkVerifier;
import java.io.*;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.*;

/** File-level checks shared by the Android verifier and real signed-fixture JVM tests. */
final class ApkSignatureChecks {
    private ApkSignatureChecks() {}

    static Set<String> verify(File file,long expectedSize,String expectedHash,Set<String> expectedSigners,int sdk)
            throws Exception {
        if(file.length()!=expectedSize) throw new SecurityException("APK-Größe stimmt nicht.");
        if(!sha256(file).equals(expectedHash)) throw new SecurityException("SHA-256 stimmt nicht.");

        Set<String> verified=verifiedSigners(file,sdk);
        if(!verified.equals(expectedSigners))
            throw new SecurityException("APK-Signatur stimmt nicht mit APKDrop überein.");
        return verified;
    }

    /** Local, offline check for an APK selected by the user. No trusted publisher is implied. */
    static Inspection inspect(File file,int sdk) throws Exception {
        if(file==null || !file.isFile() || file.length()==0)
            throw new SecurityException("Die APK-Datei ist leer oder nicht lesbar.");
        String hash=sha256(file);
        return new Inspection(hash,verifiedSigners(file,sdk));
    }

    static final class Inspection {
        final String sha256;
        final Set<String> signers;
        Inspection(String sha256,Set<String> signers) { this.sha256=sha256;this.signers=signers; }
    }

    private static Set<String> verifiedSigners(File file,int sdk) throws Exception {
        ApkVerifier.Result result=new ApkVerifier.Builder(file)
                .setMinCheckedPlatformVersion(sdk).build().verify();
        if(!result.isVerified()) throw new SecurityException("APK-Signatur ist nicht kryptografisch gültig.");

        Set<String> verified=new LinkedHashSet<>();
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        for(X509Certificate certificate:result.getSignerCertificates())
            verified.add(hex(digest.digest(certificate.getEncoded())));
        if(verified.isEmpty()) throw new SecurityException("Kein APK-Signaturzertifikat vorhanden.");
        return Collections.unmodifiableSet(verified);
    }

    static String sha256(File file) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(file)) {
            byte[] buffer=new byte[65536];
            for(int n;(n=in.read(buffer))!=-1;) digest.update(buffer,0,n);
        }
        return hex(digest.digest());
    }

    private static String hex(byte[] bytes) {
        StringBuilder out=new StringBuilder(bytes.length*2);
        for(byte b:bytes) out.append(String.format(Locale.ROOT,"%02x",b&255));
        return out.toString();
    }
}
