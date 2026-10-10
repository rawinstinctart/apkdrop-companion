package dev.apkdrop;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Android 8+, Java/Kotlin. No account or background polling. Android confirms installation. */
public final class DropUpdate {
    private static final ExecutorService IO = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private DropUpdate() {}
    public static final class Task implements AutoCloseable {
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private volatile HttpURLConnection connection;
        private volatile File ownedFile;
        public void cancel() { cancelled.set(true); HttpURLConnection c=connection;if(c!=null)c.disconnect(); File f=ownedFile;if(f!=null)f.delete(); }
        @Override public void close() { cancel(); }
        private void ensureActive() { if(cancelled.get())throw new IllegalStateException("Update abgebrochen."); }
    }
    public static Task check(Activity activity, String endpoint) { return check(activity, endpoint, true); }
    /** Set userInitiated=false for a quiet check: only an available update opens a dialog. */
    public static Task check(Activity activity, String endpoint, boolean userInitiated) {
        Task task = new Task();
        IO.execute(() -> {
            try {
                URI api = UpdatePolicy.trustedUrl(endpoint, null);
                if (api.getQuery()!=null || !api.getPath().matches("/api/[a-z0-9-]{3,40}/(?:(?:stable|beta|nightly)/)?update\\.json")) throw new SecurityException("Ungültiger APKDrop-Endpunkt.");
                Installed installed = installed(activity);
                String url = endpoint + "?versionCode="+installed.code+"&packageName="+Uri.encode(installed.name)+"&sdk="+Build.VERSION.SDK_INT
                    +"&maxApkBytes="+UpdatePolicy.MAX_BYTES+"&abis="+Uri.encode(String.join(",",Build.SUPPORTED_ABIS))+"&signers="+Uri.encode(String.join(",",installed.signers));
                JSONObject data = new JSONObject(new String(read(task,url,api,64*1024),java.nio.charset.StandardCharsets.UTF_8));
                if (!"apkdrop.update.v1".equals(data.optString("schema"))) throw new SecurityException("Unbekannter Update-Vertrag.");
                if (!"available".equals(data.optString("status"))) {
                    if (userInitiated) post(activity,task,()->message(activity,statusMessage(data.optString("reason"))));
                    return;
                }
                JSONObject json = data.getJSONObject("release");
                Release release = new Release(json,api);
                UpdatePolicy.checkIdentity(installed.name,installed.code,Build.VERSION.SDK_INT,installed.signers,Build.SUPPORTED_ABIS,
                    release.packageName,release.code,release.minSdk,release.signers,release.abis);
                post(activity,task,()->new AlertDialog.Builder(activity).setTitle("Neue Version: "+release.version)
                    .setMessage((release.notes.isEmpty()?"Eine neue Version deiner App ist verfügbar.":release.notes)+"\n\n"+String.format(java.util.Locale.GERMANY,"%.1f MiB",release.size/1048576.0)+" · Download und APK-Prüfung\nAndroid bestätigt anschließend die Installation.")
                    .setNegativeButton("Später",(d,w)->task.cancel()).setOnCancelListener(d->task.cancel())
                    .setPositiveButton("Jetzt aktualisieren",(d,w)->download(activity,task,release)).show());
            } catch (Exception error) { if(userInitiated)post(activity,task,()->message(activity,errorMessage(error))); }
        });
        return task;
    }
    private static void download(Activity activity, Task task, Release release) {
        LinearLayout layout=new LinearLayout(activity);layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(48,24,48,24);
        TextView text=new TextView(activity);text.setText("Download wird gestartet …");layout.addView(text);
        ProgressBar bar=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(100);layout.addView(bar,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        AlertDialog progress=new AlertDialog.Builder(activity).setTitle("Update herunterladen").setView(layout).setNegativeButton("Abbrechen",(d,w)->task.cancel()).setOnCancelListener(d->task.cancel()).create();progress.show();
        IO.execute(()->{File file=null;try{
            File dir=new File(activity.getCacheDir(),"apkdrop-updates");if(!dir.isDirectory()&&!dir.mkdirs())throw new IllegalStateException("Kein Speicher für das Update verfügbar.");
            // Keep only recent files; never remove a file that another task is currently writing.
            File[] old=dir.listFiles();if(old!=null)for(File item:old)if(item.lastModified()<System.currentTimeMillis()-86400000L)item.delete();
            file=File.createTempFile("update-", ".part",dir);task.ownedFile=file;downloadFile(task,release,file,(done,total)->post(activity,task,()->{int percent=(int)(done*100/total);bar.setProgress(percent);text.setText(percent+" % heruntergeladen");}));
            task.ensureActive();post(activity,task,()->text.setText("APK und Signatur werden geprüft …"));
            verifyFile(activity,file,release);
            task.ensureActive();File verified=new File(dir,UUID.randomUUID()+".apk");if(!file.renameTo(verified))throw new IllegalStateException("Update-Datei konnte nicht vorbereitet werden.");file=verified;task.ownedFile=verified;
            final File ready=file;
            if(!alive(activity)||task.cancelled.get()){ready.delete();return;}
            post(activity,task,()->{progress.dismiss();offerInstall(activity,task,ready,release);});
        }catch(Exception error){if(file!=null)file.delete();MAIN.post(()->{if(alive(activity)){progress.dismiss();if(!task.cancelled.get())message(activity,errorMessage(error));}});}});
    }
    /** Rechecks the on-disk APK before handing its restricted content URI to Android. */
    private static void offerInstall(Activity activity, Task task, File file, Release release) {
        if (Build.VERSION.SDK_INT>=26&&!activity.getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(activity).setTitle("Installation freigeben").setMessage("Erlaube dieser App in Android, Updates zu installieren. Kehre danach zurück und tippe auf „Installation starten“.")
                .setNegativeButton("Später",(d,w)->file.delete()).setOnCancelListener(d->file.delete())
                .setPositiveButton("Einstellungen öffnen",(d,w)->{try{activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName())));retryInstall(activity,task,file,release);}catch(Exception e){file.delete();message(activity,errorMessage(e));}}).show();return;
        }
        installVerified(activity,task,file,release);
    }
    private static void retryInstall(Activity activity,Task task,File file,Release release){
        new AlertDialog.Builder(activity).setTitle("Geprüftes Update bereit").setMessage("Nach der Android-Freigabe kannst du die Installation starten.")
            .setNegativeButton("Abbrechen",(d,w)->{task.cancel();file.delete();}).setOnCancelListener(d->{task.cancel();file.delete();})
            .setPositiveButton("Installation starten",(d,w)->offerInstall(activity,task,file,release)).show();
    }
    private static void installVerified(Activity activity,Task task,File file,Release release){
        IO.execute(()->{try{task.ensureActive();verifyFile(activity,file,release);task.ensureActive();post(activity,task,()->{try{
            Uri uri=FileProvider.getUriForFile(activity,activity.getPackageName()+".apkdrop.files",file);
            Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(intent);
            task.ownedFile=null; // Android may still read the APK after this Activity stops.
        }catch(Exception error){file.delete();message(activity,errorMessage(error));}});}catch(Exception error){file.delete();post(activity,task,()->message(activity,errorMessage(error)));}});
    }
    static void verifyFile(Activity activity,File file,Release release)throws Exception{
        if(file.length()!=release.size)throw new SecurityException("Die APK ist unvollständig.");
        MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=new java.io.FileInputStream(file)){byte[] buffer=new byte[65536];for(int n;(n=in.read(buffer))!=-1;)digest.update(buffer,0,n);}
        if(!UpdatePolicy.hex(digest.digest()).equals(release.sha256))throw new SecurityException("Die APK-Prüfsumme stimmt nicht. Bitte erneut versuchen.");
        Set<String> verified=UpdatePolicy.verifySignature(file,Build.VERSION.SDK_INT);if(!verified.equals(release.signers))throw new SecurityException("Die heruntergeladene Signatur passt nicht zum Release.");
        PackageInfo archive=activity.getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(),signingFlags());
        if(archive==null||archive.applicationInfo==null)throw new SecurityException("Android kann diese APK nicht lesen.");
        Installed current=installed(activity);long code=versionCode(archive);int min=archive.applicationInfo.minSdkVersion;
        if(code!=release.code||min!=release.minSdk)throw new SecurityException("Die APK-Metadaten passen nicht zum Release.");
        UpdatePolicy.checkIdentity(current.name,current.code,Build.VERSION.SDK_INT,current.signers,Build.SUPPORTED_ABIS,archive.packageName,code,min,verified,release.abis);
    }
    interface Progress { void update(long done,long total); }
    static void downloadFile(Task task,Release release,File target,Progress progress)throws Exception{
        HttpURLConnection connection=open(task,release.downloadUrl,release.origin);try{
            int status=connection.getResponseCode();if(status!=200)throw new IllegalStateException("Download nicht verfügbar ("+status+").");
            long length=connection.getContentLengthLong();if(length>0&&length!=release.size)throw new SecurityException("Unerwartete APK-Größe.");
            MessageDigest digest=MessageDigest.getInstance("SHA-256");long count=0,last=0;
            try(InputStream input=connection.getInputStream();FileOutputStream output=new FileOutputStream(target)){byte[] buffer=new byte[65536];for(int n;(n=input.read(buffer))!=-1;){task.ensureActive();count+=n;if(count>release.size||count>UpdatePolicy.MAX_BYTES)throw new SecurityException("Die APK überschreitet die erlaubte Größe.");output.write(buffer,0,n);digest.update(buffer,0,n);if(count-last>=65536){progress.update(count,release.size);last=count;}}output.getFD().sync();}
            task.ensureActive();if(count!=release.size||!UpdatePolicy.hex(digest.digest()).equals(release.sha256))throw new SecurityException("Die APK ist unvollständig oder ihre Prüfsumme stimmt nicht.");progress.update(count,release.size);
        }finally{connection.disconnect();task.connection=null;}
    }
    private static byte[] read(Task task,String url,URI origin,int limit)throws Exception{
        HttpURLConnection c=open(task,url,origin);try{int status=c.getResponseCode();if(status!=200)throw new IllegalStateException(status==404?"Update-Endpunkt nicht verfügbar. Veröffentlichung und Channel prüfen.":"Update-Prüfung gerade nicht verfügbar ("+status+").");
        try(InputStream input=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[4096];for(int n;(n=input.read(buffer))!=-1;){task.ensureActive();if(out.size()+n>limit)throw new SecurityException("Update-Antwort ist zu groß.");out.write(buffer,0,n);}return out.toByteArray();}}finally{c.disconnect();task.connection=null;}
    }
    private static HttpURLConnection open(Task task,String value,URI origin)throws Exception{task.ensureActive();URI uri=UpdatePolicy.trustedUrl(value,origin);HttpURLConnection c=(HttpURLConnection)uri.toURL().openConnection();task.connection=c;c.setInstanceFollowRedirects(false);c.setConnectTimeout(10000);c.setReadTimeout(20000);c.setRequestProperty("Accept-Encoding","identity");c.setRequestProperty("Accept","application/json, application/vnd.android.package-archive");return c;}
    @SuppressWarnings("deprecation") private static int signingFlags(){return Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;}
    @SuppressWarnings("deprecation") private static Set<String> fingerprints(PackageInfo info)throws Exception{
        Signature[] signatures=Build.VERSION.SDK_INT>=28?(info.signingInfo!=null?info.signingInfo.getApkContentsSigners():null):info.signatures;
        Set<String> out=new HashSet<>();if(signatures!=null)for(Signature signature:signatures)out.add(UpdatePolicy.hex(MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())));if(out.isEmpty())throw new SecurityException("Installierte App-Signatur fehlt.");return out;
    }
    @SuppressWarnings("deprecation") private static long versionCode(PackageInfo info){return Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode;}
    private static Installed installed(Activity activity)throws Exception{PackageInfo info=activity.getPackageManager().getPackageInfo(activity.getPackageName(),signingFlags());return new Installed(info.packageName,versionCode(info),fingerprints(info));}
    private static final class Installed {final String name;final long code;final Set<String> signers;Installed(String name,long code,Set<String> signers){this.name=name;this.code=code;this.signers=signers;}}
    static final class Release {
        final String version,packageName,sha256,notes,downloadUrl;final long code,size;final int minSdk;final Set<String> signers;final String[] abis;final URI origin;
        Release(JSONObject json,URI origin)throws Exception{this.origin=origin;version=json.getString("version");packageName=json.getString("packageName");code=json.getLong("versionCode");minSdk=json.getInt("minSdk");size=json.getLong("size");sha256=json.getString("sha256").toLowerCase(java.util.Locale.ROOT);notes=json.optString("notes","");downloadUrl=UpdatePolicy.trustedUrl(json.getString("downloadUrl"),origin).toString();
            UpdatePolicy.checkSize(size);if(!sha256.matches("[a-f0-9]{64}")||notes.length()>12000||version.length()>120)throw new SecurityException("Ungültige Release-Daten.");
            abis=array(json.getJSONArray("abis"));signers=new HashSet<>(java.util.Arrays.asList(array(json.getJSONArray("signers"))));if(signers.isEmpty()||signers.size()>8||signers.stream().anyMatch(s->!s.matches("[a-f0-9]{64}")))throw new SecurityException("Ungültige Signaturdaten.");
        }
    }
    private static String[] array(JSONArray array)throws Exception{if(array.length()>8)throw new SecurityException("Zu viele Release-Merkmale.");String[] values=new String[array.length()];for(int i=0;i<values.length;i++)values[i]=array.getString(i);return values;}
    private static boolean alive(Activity activity){return !activity.isFinishing()&&!activity.isDestroyed();}
    private static void post(Activity activity,Task task,Runnable work){MAIN.post(()->{if(alive(activity)&&!task.cancelled.get())work.run();else task.cancel();});}
    private static void message(Activity activity,String text){Toast.makeText(activity,text,Toast.LENGTH_LONG).show();}
    private static String errorMessage(Exception error){return error instanceof SecurityException||error instanceof IllegalStateException?error.getMessage():"Update konnte nicht geladen werden. Prüfe deine Verbindung und versuche es erneut.";}
    private static String statusMessage(String reason){switch(reason){case "same_version":case "newer_installed":return "Du nutzt bereits eine aktuelle Version.";case "android_version":case "architecture":return "Dieses Update passt nicht zu deinem Android-Gerät.";case "package_mismatch":case "signer_mismatch":return "Dieses Update passt nicht zur installierten App-Identität.";case "verification_incomplete":return "Der neue Release ist noch nicht vollständig geprüft.";default:return "Noch kein passender Release verfügbar.";}}
}
