package de.rawinstinctai.apkdrop;

import android.app.job.*;
import android.content.*;
import java.io.File;

/** User-opted-in, bounded offline preparation. Never installs or grants APK permissions. */
final class DropPilot {
    static final int PERIODIC_JOB=405, INITIAL_JOB=406;
    static final long LIMIT=64L*1024*1024;
    private static final long PERIOD=12L*60*60*1000;
    private static final String PREF="apkdrop-droppilot";
    private static long activeRun;
    private DropPilot() {}
    static SharedPreferences preferences(Context c) {return c.getSharedPreferences(PREF,Context.MODE_PRIVATE);}
    static synchronized long begin(Context c) {
        if(!enabled(c))return 0;
        long run=preferences(c).getLong("run",0)+1;
        activeRun=run;
        preferences(c).edit().putLong("run",run).putLong("lastStarted",System.currentTimeMillis())
                .putString("outcome","running").putString("stage","Prüfe gespeicherte Apps …").apply();
        return run;
    }
    static synchronized void stage(Context c,long run,String message) {
        if(run!=0 && activeRun==run && enabled(c))preferences(c).edit().putString("stage",message).apply();
    }
    static synchronized void finish(Context c,long run,String result) {
        if(run==0 || activeRun!=run)return;
        activeRun=0;
        if(enabled(c))preferences(c).edit().putLong("lastFinished",System.currentTimeMillis())
                .putString("outcome",result).remove("stage").apply();
    }
    static synchronized String preparedSlug(Context c) {
        if(!enabled(c))return null;
        SharedPreferences p=preferences(c);String slug=p.getString("slug",""),name=p.getString("file","");
        if(slug.isEmpty()||!VerifiedApkFiles.allowed(name))return null;
        File file=new File(directory(c),name);
        if(!file.isFile()||file.length()!=p.getLong("size",-1))return null;
        try {
            AppLibrary.Entry pin=new AppLibraryStore(c).read().find(slug);
            if(pin==null || !pin.packageName.equals(p.getString("pkg",""))
                    || !pin.signers.equals(new java.util.HashSet<>(java.util.Arrays.asList(p.getString("signers","").split(",")))))return null;
            InstalledState installed=InstalledState.read(c,pin.packageName);
            if(installed==null||installed.versionCode>=p.getLong("version",-1)||!installed.signers.equals(pin.signers))return null;
            return slug;
        }catch(Exception unavailable) {return null;}
    }
    static synchronized String headline(Context c) {
        if(!enabled(c))return "Aus · keine Hintergrunddownloads";
        if(activeRun!=0)return preferences(c).getString("stage","Prüfung läuft …");
        if(preparedSlug(c)!=null)return "Update vorbereitet · vor Installation erneut prüfen";
        try {if(new AppLibraryStore(c).read().entries().isEmpty())return "Füge zuerst eine App hinzu";}catch(Exception bad){return "App-Liste konnte nicht gelesen werden";}
        if(!scheduled(c))return "Noch nicht eingeplant · Android-Einstellungen prüfen";
        try {
            android.net.ConnectivityManager net=c.getSystemService(android.net.ConnectivityManager.class);
            android.net.NetworkCapabilities caps=net==null?null:net.getNetworkCapabilities(net.getActiveNetwork());
            if(caps==null || !caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    || net.isActiveNetworkMetered())return "Warte auf WLAN / ungetaktetes Netz";
            Intent battery=c.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if(battery!=null) {
                int state=battery.getIntExtra(android.os.BatteryManager.EXTRA_STATUS,-1);
                if(state!=android.os.BatteryManager.BATTERY_STATUS_CHARGING && state!=android.os.BatteryManager.BATTERY_STATUS_FULL)
                    return "Warte auf Laden";
                int level=battery.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL,-1),scale=battery.getIntExtra(android.os.BatteryManager.EXTRA_SCALE,-1);
                if(level>=0 && scale>0 && 100L*level/scale<=15)return "Akku weiter laden";
            }
        }catch(Exception unavailable) { /* Scheduling status remains independently known. */ }
        return "Eingeplant · Android bestimmt den nächsten Lauf";
    }
    static synchronized String dashboard(Context c) {
        SharedPreferences p=preferences(c);String result=headline(c);
        long started=p.getLong("lastStarted",0),finished=p.getLong("lastFinished",0);
        if(started==0)result+="\nNoch keine Hintergrundprüfung ausgeführt.";
        else {
            result+="\nLetzte Prüfung gestartet: "+java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT).format(new java.util.Date(started));
            String outcome=p.getString("outcome","");
            if(activeRun==0)result+="\n"+switch(outcome) {
                case "prepared" -> "Letzter Lauf: Update vorbereitet.";
                case "current" -> "Letzter Lauf: kein geeignetes Update gefunden.";
                case "oversize" -> "Letzter Lauf: Update über 64 MiB · manuell herunterladen.";
                case "storage" -> "Letzter Lauf: zu wenig freier Speicher.";
                case "error" -> "Letzter Lauf: Prüfung oder Download fehlgeschlagen · erneuter Versuch vorgesehen.";
                case "interrupted", "running" -> "Letzter Lauf nicht abgeschlossen · Android kann erneut starten.";
                default -> "Letzter Lauf: kein Ergebnis gespeichert.";
            };
            if(activeRun==0 && finished>=started)result+="\nAbgeschlossen: "+java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(new java.util.Date(finished));
        }
        if(preparedSlug(c)!=null)result+="\nDownload: "+p.getString("appName",p.getString("slug",""))+" · v"+p.getString("versionName","");
        return result+"\n\nNur ungetaktetes Netz, beim Laden, mit ausreichend Akku. Maximal eine APK bis 64 MiB. Installation bestätigst du selbst.";
    }

    static boolean enabled(Context c) {return c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getBoolean("enabled",false);}
    static synchronized void enabled(Context c,boolean value) {
        boolean was=enabled(c);
        c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putBoolean("enabled",value)
                .putBoolean("initialRequested",value && !was).apply();
        if(!value) {activeRun=0;clear(c);}
        reconcile(c);
    }
    static File directory(Context c) {return new File(c.getCacheDir(),"apkdrop-pilot");}
    static synchronized void clear(Context c) {
        c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit()
                .remove("slug").remove("sha").remove("pkg").remove("version").remove("file").remove("size").remove("signers").remove("appName").remove("versionName").apply();
        File[] files=directory(c).listFiles();
        if(files!=null) for(File file:files) if(file.isFile() && (VerifiedApkFiles.allowed(file.getName()) || file.getName().endsWith(".part"))) file.delete();
    }
    static synchronized File candidate(Context c,InstallContract release) {
        if(!enabled(c))return null;
        AppLibrary.Entry pin;
        try {pin=new AppLibraryStore(c).read().find(release.slug);}catch(Exception bad){return null;}
        if(pin==null)return null;
        try {pin.requireIdentity(release.slug,release.packageName,release.signers);}catch(Exception bad){return null;}
        SharedPreferences prefs=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        String name=prefs.getString("file","");
        if(!VerifiedApkFiles.allowed(name) || !release.slug.equals(prefs.getString("slug",""))
                || !release.packageName.equals(prefs.getString("pkg",""))
                || !release.sha256.equals(prefs.getString("sha",""))
                || release.versionCode!=prefs.getLong("version",-1)) return null;
        File file=new File(directory(c),name);
        return file.isFile() && file.length()==release.size?file:null;
    }
    static synchronized boolean record(Context c,AppLibrary.Entry pin,InstallContract release,File file,long run) {
        return run!=0 && activeRun==run && record(c,pin,release,file);
    }
    static synchronized boolean record(Context c,AppLibrary.Entry pin,InstallContract release,File file) {
        AppLibrary.Entry current;
        try {current=new AppLibraryStore(c).read().find(pin.slug);}catch(Exception bad){return false;}
        if(!enabled(c) || current==null
                || !VerifiedApkFiles.allowed(file.getName()) || !file.getParentFile().equals(directory(c))
                || file.length()!=release.size) return false;
        current.requireIdentity(release.slug,release.packageName,release.signers);
        boolean saved=c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit()
                .putString("slug",release.slug).putString("pkg",release.packageName)
                .putString("sha",release.sha256).putLong("version",release.versionCode)
                .putString("file",file.getName()).putLong("size",release.size)
                .putString("signers",String.join(",",release.signers)).putString("appName",release.appName)
                .putString("versionName",release.version).commit();
        if(saved) {
            File[] files=directory(c).listFiles();
            if(files!=null) for(File prior:files) if(prior.isFile() && VerifiedApkFiles.allowed(prior.getName())
                    && !prior.equals(file)) prior.delete();
        }
        return saved;
    }
    static boolean scheduled(Context c) {
        try {JobScheduler jobs=c.getSystemService(JobScheduler.class);
            return jobs!=null&&jobs.getPendingJob(PERIODIC_JOB)!=null;}
        catch(Exception e) {return false;}
    }
    static synchronized boolean reconcile(Context c) {
        JobScheduler jobs=c.getSystemService(JobScheduler.class);
        if(jobs==null)return false;
        boolean tracked=false;
        try {tracked=!new AppLibraryStore(c).read().entries().isEmpty();}
        catch(Exception ignored) {}
        if(!enabled(c)||!tracked) {jobs.cancel(PERIODIC_JOB);jobs.cancel(INITIAL_JOB);return true;}
        try {
            if(jobs.getPendingJob(PERIODIC_JOB)==null) {
                JobInfo periodic=base(c,PERIODIC_JOB).setPeriodic(PERIOD,60*60*1000L).build();
                if(jobs.schedule(periodic)!=JobScheduler.RESULT_SUCCESS)return false;
            }
            if(c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getBoolean("initialRequested",false)
                    && jobs.getPendingJob(INITIAL_JOB)==null) {
                if(jobs.schedule(base(c,INITIAL_JOB).setMinimumLatency(1).build())==JobScheduler.RESULT_SUCCESS)
                    c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putBoolean("initialRequested",false).apply();
            }
            return true;
        }catch(Exception rejected) {return false;}
    }
    private static JobInfo.Builder base(Context c,int id) {
        return new JobInfo.Builder(id,new ComponentName(c,DropPilotJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                .setRequiresCharging(true).setRequiresBatteryNotLow(true)
                .setPersisted(true);
    }
}
