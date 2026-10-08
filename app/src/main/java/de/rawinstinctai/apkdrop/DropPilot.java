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
    private DropPilot() {}

    static boolean enabled(Context c) {return c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getBoolean("enabled",false);}
    static synchronized void enabled(Context c,boolean value) {
        boolean was=enabled(c);
        c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putBoolean("enabled",value)
                .putBoolean("initialRequested",value && !was).apply();
        if(!value) clear(c);
        reconcile(c);
    }
    static File directory(Context c) {return new File(c.getCacheDir(),"apkdrop-pilot");}
    static synchronized void clear(Context c) {
        c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit()
                .remove("slug").remove("sha").remove("pkg").remove("version").remove("file").apply();
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
                .putString("file",file.getName()).commit();
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
