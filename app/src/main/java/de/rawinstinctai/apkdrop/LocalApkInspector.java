package de.rawinstinctai.apkdrop;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import java.io.*;
import java.util.*;

/**
 * User-selected APK check. The selected bytes never leave this device.
 * This is inspection-only: no download, upload, install, trust-store write or APKDrop Pass claim.
 */
final class LocalApkInspector {
    private static final long MAX_BYTES=250L*1024*1024;
    private LocalApkInspector() {}

    enum SignerComparison { MATCH, MISMATCH, NOT_INSTALLED }

    static final class Report {
        final String text,packageName,versionName;
        final SignerComparison signerComparison;
        final int declaredPermissions,addedPermissions;
        Report(String text,String packageName,String versionName,SignerComparison signerComparison,
                int declaredPermissions,int addedPermissions) {
            this.text=text;this.packageName=packageName;this.versionName=versionName;
            this.signerComparison=signerComparison;this.declaredPermissions=declaredPermissions;
            this.addedPermissions=addedPermissions;
        }
    }

    static Report inspect(Context context,Uri uri) throws Exception {
        if(uri==null || !"content".equals(uri.getScheme()))
            throw new SecurityException("Bitte eine APK über die Dateiauswahl öffnen.");
        File dir=new File(context.getCacheDir(),"manual-apk-inspection");
        if(!dir.isDirectory() && !dir.mkdirs())
            throw new IOException("Privater Prüfspeicher ist nicht verfügbar.");
        File temp=File.createTempFile("selected-", ".apk",dir);
        try {
            long size=copyBounded(context,uri,temp);
            ApkSignatureChecks.Inspection checked=ApkSignatureChecks.inspect(temp,Build.VERSION.SDK_INT);
            int flags=PackageManager.GET_PERMISSIONS | (Build.VERSION.SDK_INT>=28
                    ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES);
            PackageInfo archive=context.getPackageManager().getPackageArchiveInfo(temp.getAbsolutePath(),flags);
            if(archive==null || archive.applicationInfo==null || archive.packageName==null
                    || archive.packageName.isEmpty())
                throw new SecurityException("Android kann die Paketdaten dieser APK nicht lesen.");

            InstalledState installed=InstalledState.read(context,archive.packageName);
            SignerComparison comparison=installed==null?SignerComparison.NOT_INSTALLED
                    :installed.signers.equals(checked.signers)?SignerComparison.MATCH:SignerComparison.MISMATCH;
            String signerStatus=comparison==SignerComparison.NOT_INSTALLED
                    ?"Noch kein Vergleich mit einer installierten Version möglich."
                    :comparison==SignerComparison.MATCH
                        ?"✓ Signatur stimmt mit der installierten App überein."
                        :"⚠ Signatur weicht von der installierten App ab. Nicht als normales Update verwenden.";

            Set<String> declared=new LinkedHashSet<>();
            if(archive.requestedPermissions!=null) Collections.addAll(declared,archive.requestedPermissions);
            int addedPermissions=-1;
            String permissionInfo=declared.size()+" angeforderte Berechtigung(en)";
            if(installed!=null) {
                Set<String> added=new LinkedHashSet<>(declared);
                added.removeAll(installed.permissions);
                addedPermissions=added.size();
                permissionInfo+="; "+addedPermissions+" neu gegenüber installiert.";
            }

            StringBuilder result=new StringBuilder("✓ APK-Signatur kryptografisch gültig für dieses Android-Gerät.");
            result.append("\n\n").append(signerStatus);
            result.append("\n\nPaket: ").append(archive.packageName);
            result.append("\nVersion: ").append(archive.versionName==null?"—":archive.versionName);
            result.append(" (Code ").append(InstalledState.versionCode(archive)).append(")");
            result.append("\nGröße: ").append(String.format(Locale.GERMANY,"%.1f MiB",size/1048576.0));
            result.append("\nAndroid: mindestens ").append(archive.applicationInfo.minSdkVersion);
            result.append(" · Ziel ").append(archive.applicationInfo.targetSdkVersion);
            result.append("\nBerechtigungen: ").append(permissionInfo);
            result.append("\n\nSHA-256 der APK:\n").append(checked.sha256);
            result.append("\n\nSignaturzertifikat(e) SHA-256:\n")
                    .append(String.join("\n",checked.signers));
            result.append("\n\nHerkunft/DropPass: nicht geprüft. Ein gültiges Zertifikat beweist nicht, wer die Datei bereitgestellt hat.");
            result.append("\nDies ist kein Malware-Scan und keine Installationsfreigabe. Es wurde nichts installiert oder hochgeladen.");
            return new Report(result.toString(),archive.packageName,archive.versionName,
                    comparison,declared.size(),addedPermissions);
        } finally {
            if(temp.exists() && !temp.delete())
                throw new IOException("Temporäre APK-Datei konnte nicht gelöscht werden.");
        }
    }

    private static long copyBounded(Context context,Uri uri,File target) throws IOException {
        long written=0;
        try(InputStream in=context.getContentResolver().openInputStream(uri);
            OutputStream out=new BufferedOutputStream(new FileOutputStream(target))) {
            if(in==null)throw new IOException("Datei kann nicht geöffnet werden.");
            byte[] chunk=new byte[65536];
            for(int count;(count=in.read(chunk))!=-1;) {
                if(Thread.currentThread().isInterrupted())throw new IOException("Prüfung abgebrochen.");
                if(count==0)continue;
                if(written>MAX_BYTES-count)
                    throw new IOException("Die ausgewählte APK überschreitet 250 MiB.");
                out.write(chunk,0,count);
                written+=count;
            }
        }
        if(written==0)throw new IOException("Die ausgewählte Datei ist leer.");
        return written;
    }
}
