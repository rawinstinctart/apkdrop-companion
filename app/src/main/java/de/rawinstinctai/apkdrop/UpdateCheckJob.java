package de.rawinstinctai.apkdrop;

import android.app.job.*;
import android.os.*;
import java.util.concurrent.*;

/** Fetches metadata only for explicit pins. No downloader, crypto bypass, inventory scan or installer. */
public final class UpdateCheckJob extends JobService {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private Future<?> task;
    private volatile long generation;

    @Override public boolean onStartJob(JobParameters params) {
        if(!UpdateScheduler.enabled(this)) return false;
        final long ticket=++generation;
        task=worker.submit(()->{
            boolean retry=false;
            long deadline=SystemClock.elapsedRealtime()+180000;
            try {
                AppLibraryStore library=new AppLibraryStore(this);
                ReleaseSnapshotStore snapshots=new ReleaseSnapshotStore(this);
                for(AppLibrary.Entry entry:library.read().entries()) {
                    if(stopped(ticket)) return;
                    if(SystemClock.elapsedRealtime()>deadline) { retry=true; break; }
                    long started=System.currentTimeMillis();
                    try {
                        InstallContract release=ContractClient.fetch(entry.slug);
                        entry.requireIdentity(release.slug,release.packageName,release.signers);
                        InstalledState installed=InstalledState.read(this,entry.packageName);
                        InstallPolicy.Result result=InstallPolicy.evaluate(release,installed,Build.VERSION.SDK_INT,Build.SUPPORTED_ABIS);
                        synchronized(ReleaseSnapshotStore.LOCK) {
                            if(stopped(ticket)) return;
                            AppLibrary.Entry current=library.read().find(entry.slug);
                            if(current==null) continue;
                            current.requireIdentity(release.slug,release.packageName,release.signers);
                            snapshots.success(entry,release,started);
                            if(result.mode==InstallPolicy.Mode.UPDATE) UpdateNotifications.post(this,entry,release,result);
                            else UpdateNotifications.remove(this,entry.slug);
                        }
                    } catch(Exception e) {
                        retry |= UpdateFailure.retryable(e);
                        synchronized(ReleaseSnapshotStore.LOCK) {
                            if(stopped(ticket)) return;
                            snapshots.failure(entry,e.getMessage(),e instanceof SecurityException,started);
                        }
                    }
                }
            } catch(Exception unavailable) { retry=true; }
            final boolean reschedule=retry;
            main.post(()->{ if(ticket==generation) jobFinished(params,reschedule); });
        });
        return true;
    }
    private boolean stopped(long ticket) {
        return ticket!=generation || Thread.currentThread().isInterrupted() || !UpdateScheduler.enabled(this);
    }
    @Override public boolean onStopJob(JobParameters params) {
        synchronized(ReleaseSnapshotStore.LOCK) { generation++; if(task!=null) task.cancel(true); }
        return UpdateScheduler.enabled(this);
    }
    @Override public void onDestroy() {
        synchronized(ReleaseSnapshotStore.LOCK) { generation++; if(task!=null) task.cancel(true); }
        worker.shutdownNow(); super.onDestroy();
    }
}
