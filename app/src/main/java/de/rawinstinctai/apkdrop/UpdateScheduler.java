package de.rawinstinctai.apkdrop;

import android.app.job.*;
import android.content.*;

final class UpdateScheduler {
    static final int JOB_ID=404;
    private static final long INTERVAL=6L*60*60*1000;
    private UpdateScheduler() {}
    static boolean enabled(Context context) {
        return context.getSharedPreferences("apkdrop-updates",Context.MODE_PRIVATE).getBoolean("enabled",true);
    }
    static void enabled(Context context,boolean value) {
        context.getSharedPreferences("apkdrop-updates",Context.MODE_PRIVATE).edit().putBoolean("enabled",value).apply();
        reconcile(context);
    }
    static boolean reconcile(Context context) {
        JobScheduler scheduler=context.getSystemService(JobScheduler.class);
        if(scheduler==null) return false;
        boolean hasApps;
        try { hasApps=!new AppLibraryStore(context).read().entries().isEmpty(); }
        catch(Exception e) { scheduler.cancel(JOB_ID); return false; }
        if(!enabled(context) || !hasApps) { scheduler.cancel(JOB_ID); return true; }
        try {
            if(scheduler.getPendingJob(JOB_ID)!=null) return true;
            JobInfo job=new JobInfo.Builder(JOB_ID,new ComponentName(context,UpdateCheckJob.class))
                    .setPeriodic(INTERVAL,60L*60*1000).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setRequiresBatteryNotLow(true).setPersisted(true)
                    .setBackoffCriteria(60L*60*1000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build();
            return scheduler.schedule(job)==JobScheduler.RESULT_SUCCESS;
        } catch(Exception rejected) {
            // OEM JobScheduler policies may throw for otherwise valid AOSP job specs.
            // Background checks are optional: never take the launcher UI down over scheduling.
            return false;
        }
    }
}
