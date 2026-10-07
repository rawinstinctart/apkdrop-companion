package de.rawinstinctai.apkdrop;

import android.content.Context;
import android.content.pm.*;
import android.os.Build;
import java.security.MessageDigest;
import java.util.*;

final class InstalledState {
    final String packageName;
    final String versionName;
    final long versionCode;
    final int minSdk,targetSdk;
    final Set<String> signers, permissions;

    InstalledState(String packageName,long versionCode,Set<String> signers,Set<String> permissions) {
        this(packageName,versionCode,null,signers,permissions);
    }

    InstalledState(String packageName,long versionCode,String versionName,Set<String> signers,Set<String> permissions) {
        this(packageName,versionCode,versionName,signers,permissions,0,0);
    }

    InstalledState(String packageName,long versionCode,String versionName,Set<String> signers,Set<String> permissions,int minSdk,int targetSdk) {
        this.packageName=packageName; this.versionCode=versionCode; this.versionName=versionName;
        this.minSdk=minSdk; this.targetSdk=targetSdk;
        this.signers=signers; this.permissions=permissions;
    }

    static InstalledState read(Context context,String packageName) throws Exception {
        int flags=PackageManager.GET_PERMISSIONS | (Build.VERSION.SDK_INT>=28
                ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES);
        PackageInfo info;
        try { info=context.getPackageManager().getPackageInfo(packageName,flags); }
        catch(PackageManager.NameNotFoundException missing) { return null; }

        Set<String> permissions=new LinkedHashSet<>();
        if(info.requestedPermissions!=null) Collections.addAll(permissions,info.requestedPermissions);
        return new InstalledState(info.packageName,versionCode(info),info.versionName,fingerprints(info),permissions,
                info.applicationInfo==null?0:info.applicationInfo.minSdkVersion,
                info.applicationInfo==null?0:info.applicationInfo.targetSdkVersion);
    }

    @SuppressWarnings("deprecation")
    static long versionCode(PackageInfo info) { return Build.VERSION.SDK_INT>=28 ? info.getLongVersionCode() : info.versionCode; }

    @SuppressWarnings("deprecation")
    private static Set<String> fingerprints(PackageInfo info) throws Exception {
        Signature[] signatures=Build.VERSION.SDK_INT>=28
                ? (info.signingInfo==null?null:info.signingInfo.getApkContentsSigners()) : info.signatures;
        Set<String> out=new LinkedHashSet<>();
        if(signatures!=null) {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            for(Signature signature:signatures) out.add(hex(digest.digest(signature.toByteArray())));
        }
        if(out.isEmpty()) throw new SecurityException("Installierte App-Signatur fehlt.");
        return out;
    }

    private static String hex(byte[] bytes) {
        StringBuilder out=new StringBuilder(bytes.length*2);
        for(byte b:bytes) out.append(String.format(Locale.ROOT,"%02x",b&255));
        return out.toString();
    }
}
