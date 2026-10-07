package de.rawinstinctai.apkdrop;

import android.content.Context;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.Locale;

final class ApkDownloader {
    interface Progress { void onProgress(int percent); }
    private ApkDownloader() {}

    static synchronized File download(Context context,InstallContract release,Progress progress) throws Exception {
        URI uri=URI.create(release.downloadUrl);
        if(!"https".equals(uri.getScheme()) || !"apkdrop.rawinstinctai.de".equalsIgnoreCase(uri.getHost())
                || uri.getUserInfo()!=null || uri.getFragment()!=null || uri.getQuery()!=null)
            throw new SecurityException("Unzulässige APK-Adresse.");

        HttpURLConnection c=(HttpURLConnection)new URL(release.downloadUrl).openConnection();
        c.setInstanceFollowRedirects(false);
        c.setConnectTimeout(10000); c.setReadTimeout(30000);
        c.setRequestProperty("Accept","application/vnd.android.package-archive");
        c.setRequestProperty("Accept-Encoding","identity");
        c.setRequestProperty("User-Agent","APKDrop-Companion/0.1");

        File dir=new File(context.getCacheDir(),"apkdrop");
        if(!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Privater APKDrop-Cache konnte nicht angelegt werden.");
        VerifiedApkFiles.prepare(dir);
        String name=VerifiedApkFiles.newName();
        File part=new File(dir,name+".part");
        File verified=new File(dir,name);

        try {
            int status=c.getResponseCode();
            if(status!=200) throw new IOException("APK-Download nicht verfügbar ("+status+").");
            long declared=c.getContentLengthLong();
            if(declared>0 && declared!=release.size) throw new SecurityException("Unerwartete Dateigröße.");

            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            long count=0; int last=-1;
            try(InputStream in=c.getInputStream(); FileOutputStream out=new FileOutputStream(part)) {
                byte[] buffer=new byte[65536];
                for(int n;(n=in.read(buffer))!=-1;) {
                    if(Thread.currentThread().isInterrupted()) throw new InterruptedException("Download abgebrochen.");
                    count+=n;
                    if(count>release.size || count>InstallContract.MAX_BYTES)
                        throw new SecurityException("APK ist größer als erwartet.");
                    out.write(buffer,0,n); digest.update(buffer,0,n);
                    int percent=(int)Math.min(100,(count*100)/release.size);
                    if(percent!=last) { last=percent; progress.onProgress(percent); }
                }
                out.getFD().sync();
            }

            if(count!=release.size) throw new SecurityException("APK ist unvollständig.");
            if(!hex(digest.digest()).equals(release.sha256)) throw new SecurityException("SHA-256 stimmt nicht.");
            if(!part.renameTo(verified)) throw new IOException("Geprüfte APK konnte nicht finalisiert werden.");
            return verified;
        } catch(Exception e) {
            part.delete(); verified.delete(); throw e;
        } finally { c.disconnect(); }
    }

    private static String hex(byte[] bytes) {
        StringBuilder out=new StringBuilder(bytes.length*2);
        for(byte b:bytes) out.append(String.format(Locale.ROOT,"%02x",b&255));
        return out.toString();
    }
}
