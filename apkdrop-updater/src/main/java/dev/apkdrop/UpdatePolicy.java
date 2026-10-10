package dev.apkdrop;

import java.io.File;
import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import com.android.apksig.ApkVerifier;

/** Fail closed. V1 accepts identical signer sets; rotation requires a separate migration. */
public final class UpdatePolicy {
    private UpdatePolicy() {}
    public static final long MAX_BYTES = 250L * 1024 * 1024;
    public static void checkSize(long size) { if(size<1||size>MAX_BYTES)throw new SecurityException("Die APK überschreitet die erlaubte Größe."); }
    public static URI trustedUrl(String value, URI origin) {
        URI uri = URI.create(value);
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
            || (uri.getPort() != -1 && uri.getPort() != 443)
            || (origin != null && (!uri.getHost().equalsIgnoreCase(origin.getHost()) || uri.getPort() != origin.getPort())))
            throw new SecurityException("Unzulässige Update-Adresse.");
        return uri;
    }
    public static void checkIdentity(String installedPackage, long installedCode, int sdk, Set<String> installedSigners, String[] deviceAbis,
        String packageName, long code, int minSdk, Set<String> signers, String[] abis) {
        if (!installedPackage.equals(packageName)) throw new SecurityException("Diese APK gehört zu einer anderen App.");
        if (code <= installedCode) throw new SecurityException("Die APK ist kein neueres Update.");
        if (minSdk < 1 || minSdk > sdk) throw new SecurityException("Diese APK unterstützt deine Android-Version nicht.");
        if (installedSigners.isEmpty() || signers.isEmpty() || !installedSigners.equals(signers)) throw new SecurityException("Die APK-Signatur passt nicht zur installierten App.");
        if (abis.length > 0 && !Arrays.stream(abis).anyMatch(a -> Arrays.asList(deviceAbis).contains(a))) throw new SecurityException("Diese APK passt nicht zu deinem Gerät.");
    }
    public static Set<String> verifySignature(File file, int sdk) throws Exception {
        checkSize(file.length());
        ApkVerifier.Result result = new ApkVerifier.Builder(file).setMinCheckedPlatformVersion(sdk).setMaxCheckedPlatformVersion(sdk).build().verify();
        if (!result.isVerified()) throw new SecurityException("Die kryptografische APK-Signatur ist ungültig.");
        Set<String> signers = new HashSet<>();
        for (Certificate certificate : result.getSignerCertificates()) signers.add(hex(MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded())));
        if (signers.isEmpty()) throw new SecurityException("Die APK hat keine geprüfte Signatur.");
        return signers;
    }
    public static String hex(byte[] bytes) { StringBuilder out = new StringBuilder(bytes.length * 2); for (byte b : bytes) out.append(String.format(java.util.Locale.ROOT,"%02x",b & 255)); return out.toString(); }
}
