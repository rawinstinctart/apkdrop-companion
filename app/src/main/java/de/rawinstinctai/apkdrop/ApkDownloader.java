package de.rawinstinctai.apkdrop;

import android.content.Context;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.Locale;

final class ApkDownloader {
    interface Progress { void onProgress(int percent); }
    interface ConnectionOpener { HttpURLConnection open(URL url) throws IOException; }
    static final class Cancellation {
        private volatile boolean cancelled;
        private volatile boolean keepPartial;
        void pause() { keepPartial=true; cancel(); }
        private HttpURLConnection connection;
        synchronized void attach(HttpURLConnection active) {
            connection=active;
            if(cancelled) active.disconnect();
        }
        void cancel() {
            HttpURLConnection active;
            synchronized(this) { cancelled=true; active=connection; }
            if(active!=null) active.disconnect();
        }
        synchronized void detach(HttpURLConnection active) { if(connection==active) connection=null; }
        void check() throws InterruptedException {
            if(cancelled || Thread.currentThread().isInterrupted()) throw new InterruptedException("Download abgebrochen.");
        }
    }
    private ApkDownloader() {}

    static synchronized File download(Context context,InstallContract release,Progress progress) throws Exception {
        return download(context,release,progress,new Cancellation(),url->(HttpURLConnection)url.openConnection());
    }
    static synchronized File download(Context context,InstallContract release,Progress progress,Cancellation cancellation) throws Exception {
        return download(context,release,progress,cancellation,url->(HttpURLConnection)url.openConnection());
    }
    static synchronized File download(Context context,InstallContract release,Progress progress,Cancellation cancellation,
                                      ConnectionOpener opener) throws Exception {
        return download(context,release,progress,cancellation,opener,new File(context.getCacheDir(),"apkdrop"));
    }
    static synchronized File downloadTo(Context context,InstallContract release,Progress progress,Cancellation cancellation,
                                         File directory) throws Exception {
        if(!directory.getCanonicalFile().equals(new File(context.getCacheDir(),"apkdrop-pilot").getCanonicalFile()))
            throw new SecurityException("Unzulässiges Vorbereitungsverzeichnis.");
        return download(context,release,progress,cancellation,url->(HttpURLConnection)url.openConnection(),directory);
    }
    private static File download(Context context,InstallContract release,Progress progress,Cancellation cancellation,
                                      ConnectionOpener opener,File directory) throws Exception {
        URI uri=URI.create(release.downloadUrl);
        if(!"https".equals(uri.getScheme()) || !"apkdrop.rawinstinctai.de".equalsIgnoreCase(uri.getHost())
                || uri.getUserInfo()!=null || uri.getFragment()!=null || uri.getQuery()!=null)
            throw new SecurityException("Unzulässige APK-Adresse.");

        cancellation.check();
        HttpURLConnection c=opener.open(new URL(release.downloadUrl));
        cancellation.attach(c);

        File part=null,verified=null;
        try {
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(10000); c.setReadTimeout(30000);
            c.setRequestProperty("Accept","application/vnd.android.package-archive");
            c.setRequestProperty("Accept-Encoding","identity");
            c.setRequestProperty("User-Agent","APKDrop-Companion/0.1");
            File dir=directory;
            if(!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Privater APKDrop-Cache konnte nicht angelegt werden.");
            VerifiedApkFiles.prepare(dir);
            String name=VerifiedApkFiles.newName();
            part=new File(dir,"resume-"+release.sha256+".part");
            long offset=part.isFile()?part.length():0;
            if(offset>=release.size) { if(!part.delete())throw new IOException("Download konnte nicht neu gestartet werden."); offset=0; }
            File[] old=dir.listFiles(f->f.getName().startsWith("resume-") && !f.getName().equals(partName(release)));
            if(old!=null)for(File f:old)f.delete();
            if(offset>0)c.setRequestProperty("Range","bytes="+offset+"-");
            verified=new File(dir,name);
            cancellation.check();
            int status=c.getResponseCode();
            cancellation.check();
            if(status!=200 && status!=206) throw new IOException("APK-Download nicht verfügbar ("+status+").");
            if(status==206) {
                String expected="bytes "+offset+"-"+(release.size-1)+"/"+release.size;
                if(offset==0 || !expected.equals(c.getHeaderField("Content-Range")))throw new SecurityException("Ungültige Download-Fortsetzung.");
            } else offset=0;
            long declared=c.getContentLengthLong();
            if(declared>0 && declared!=release.size-offset) throw new SecurityException("Unerwartete Dateigröße.");

            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            long count=offset; int last=-1;
            if(offset>0)try(InputStream prior=new FileInputStream(part)) {
                byte[] b=new byte[65536];for(int n;(n=prior.read(b))!=-1;){cancellation.check();digest.update(b,0,n);}
            }
            try(InputStream in=c.getInputStream(); FileOutputStream out=new FileOutputStream(part,offset>0)) {
                byte[] buffer=new byte[65536];
                for(;;) {
                    cancellation.check();
                    int n=in.read(buffer);
                    if(n==-1) break;
                    cancellation.check();
                    count+=n;
                    if(count>release.size || count>InstallContract.MAX_BYTES)
                        throw new SecurityException("APK ist größer als erwartet.");
                    out.write(buffer,0,n); digest.update(buffer,0,n);
                    int percent=(int)Math.min(100,(count*100)/release.size);
                    if(percent!=last) { last=percent; progress.onProgress(percent); }
                }
                out.getFD().sync();
            }

            cancellation.check();
            if(count!=release.size) throw new SecurityException("APK ist unvollständig.");
            if(!hex(digest.digest()).equals(release.sha256)) throw new SecurityException("SHA-256 stimmt nicht.");
            cancellation.check();
            if(!part.renameTo(verified)) throw new IOException("Geprüfte APK konnte nicht finalisiert werden.");
            cancellation.check();
            return verified;
        } catch(Exception e) {
            if(part!=null && (!(e instanceof IOException) || cancellation.cancelled) && !cancellation.keepPartial) part.delete();
            if(part!=null && e instanceof SecurityException) part.delete();
            if(verified!=null) verified.delete();
            throw e;
        } finally { cancellation.detach(c); c.disconnect(); }
    }

    private static String partName(InstallContract release) {return "resume-"+release.sha256+".part";}

    private static String hex(byte[] bytes) {
        StringBuilder out=new StringBuilder(bytes.length*2);
        for(byte b:bytes) out.append(String.format(Locale.ROOT,"%02x",b&255));
        return out.toString();
    }
}
