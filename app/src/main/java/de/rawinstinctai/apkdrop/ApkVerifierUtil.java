package de.rawinstinctai.apkdrop;

import android.content.Context;
import android.content.pm.*;
import android.os.Build;
import com.android.apksig.ApkVerifier;
import java.io.*;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class ApkVerifierUtil {
    private ApkVerifierUtil() {}

    static void verify(Context context,File file,InstallContract release,InstalledState current) throws Exception {
        if(file.length()!=release.size) throw new SecurityException("APK-Größe stimmt nicht.");
        if(!sha256(file).equals(release.sha256)) throw new SecurityException("SHA-256 stimmt nicht.");

        ApkVerifier.Result result=new ApkVerifier.Builder(file)
                .setMinCheckedPlatformVersion(Build.VERSION.SDK_INT).build().verify();
        if(!result.isVerified()) throw new SecurityException("APK-Signatur ist nicht kryptografisch gültig.");

        Set<String> verified=new LinkedHashSet<>();
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        for(X509Certificate certificate:result.getSignerCertificates())
            verified.add(hex(digest.digest(certificate.getEncoded())));
        if(!verified.equals(release.signers))
            throw new SecurityException("APK-Signatur stimmt nicht mit APKDrop überein.");

        int flags=PackageManager.GET_PERMISSIONS | (Build.VERSION.SDK_INT>=28
                ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES);
        PackageInfo archive=context.getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(),flags);
        if(archive==null || archive.applicationInfo==null) throw new SecurityException("Android kann diese APK nicht lesen.");
        if(!release.packageName.equals(archive.packageName)) throw new SecurityException("Package-ID der APK stimmt nicht.");
        if(InstalledState.versionCode(archive)!=release.versionCode) throw new SecurityException("VersionCode der APK stimmt nicht.");
        if(archive.applicationInfo.minSdkVersion!=release.minSdk) throw new SecurityException("minSdk der APK stimmt nicht.");
        if(release.targetSdk>0 && archive.applicationInfo.targetSdkVersion!=release.targetSdk)
            throw new SecurityException("targetSdk der APK stimmt nicht.");

        Set<String> permissions=new LinkedHashSet<>();
        if(archive.requestedPermissions!=null) Collections.addAll(permissions,archive.requestedPermissions);
        if(!permissions.equals(release.permissions))
            throw new SecurityException("Berechtigungen der APK stimmen nicht mit APKDrop überein.");

        Set<String> localAbis=readAbis(file);
        if(!localAbis.equals(new LinkedHashSet<>(release.abis)))
            throw new SecurityException("Native Architekturen der APK stimmen nicht mit APKDrop überein.");

        if(current!=null) {
            if(!current.packageName.equals(archive.packageName)) throw new SecurityException("Diese APK gehört zu einer anderen App.");
            if(!current.signers.equals(verified)) throw new SecurityException("Signierschlüssel weicht von der installierten App ab.");
            if(release.versionCode<=current.versionCode) throw new SecurityException("Kein neueres Update.");
        }
    }

    private static Set<String> readAbis(File file) throws Exception {
        Set<String> abis=new LinkedHashSet<>();
        try(ZipFile zip=new ZipFile(file)) {
            Enumeration<? extends ZipEntry> entries=zip.entries();
            while(entries.hasMoreElements()) {
                String name=entries.nextElement().getName();
                if(!name.startsWith("lib/")) continue;
                String[] parts=name.split("/");
                if(parts.length>=3 && !parts[1].trim().isEmpty()) abis.add(parts[1]);
            }
        }
        return abis;
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
