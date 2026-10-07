package de.rawinstinctai.apkdrop;

import android.content.Context;
import android.content.pm.*;
import android.os.Build;
import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class ApkVerifierUtil {
    private ApkVerifierUtil() {}

    static void verify(Context context,File file,InstallContract release,InstalledState current) throws Exception {
        Set<String> verified=ApkSignatureChecks.verify(file,release.size,release.sha256,
                release.signers,Build.VERSION.SDK_INT);

        int flags=PackageManager.GET_PERMISSIONS | (Build.VERSION.SDK_INT>=28
                ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES);
        PackageInfo archive=context.getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(),flags);
        if(archive==null || archive.applicationInfo==null) throw new SecurityException("Android kann diese APK nicht lesen.");
        if(!release.packageName.equals(archive.packageName)) throw new SecurityException("Package-ID der APK stimmt nicht.");
        if(InstalledState.versionCode(archive)!=release.versionCode) throw new SecurityException("VersionCode der APK stimmt nicht.");
        if(archive.applicationInfo.minSdkVersion!=release.minSdk) throw new SecurityException("minSdk der APK stimmt nicht.");
        if(release.targetSdk>0 && archive.applicationInfo.targetSdkVersion!=release.targetSdk)
            throw new SecurityException("targetSdk der APK stimmt nicht.");

        // PackageManager may normalize/filter permissions for the device. APKDrop's contract
        // describes declarations in the signed manifest, so compare that exact representation.
        ManifestPermissions.requireExact(ManifestPermissions.read(file),release.permissions);

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
        return ApkSignatureChecks.sha256(file);
    }
}
