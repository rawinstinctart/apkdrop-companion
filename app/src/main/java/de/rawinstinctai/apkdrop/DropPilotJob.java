package de.rawinstinctai.apkdrop;

import android.app.job.*;
import android.os.*;
import java.io.File;
import java.util.concurrent.*;

/** At most one consented Wi-Fi/charging prefetch per run; never launches installer. */
public final class DropPilotJob extends JobService {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private Future<?> active;
    private ApkDownloader.Cancellation cancellation;
    private volatile int generation;
    @Override public boolean onStartJob(JobParameters params) {
        if(!DropPilot.enabled(this))return false;
        int ticket=++generation;
        cancellation=new ApkDownloader.Cancellation();
        ApkDownloader.Cancellation cancel=cancellation;
        active=worker.submit(()->{
            boolean retry=false;
            try {
                for(AppLibrary.Entry pin:new AppLibraryStore(this).read().entries()) {
                    if(ticket!=generation || !DropPilot.enabled(this))return;
                    File file=null;
                    try {
                        InstallContract release=ContractClient.fetch(pin.slug);
                        pin.requireIdentity(release.slug,release.packageName,release.signers);
                        InstalledState installed=InstalledState.read(this,pin.packageName);
                        InstallPolicy.Result decision=InstallPolicy.evaluate(release,installed,Build.VERSION.SDK_INT,Build.SUPPORTED_ABIS);
                        if(decision.mode!=InstallPolicy.Mode.UPDATE || release.size>DropPilot.LIMIT)continue;
                        if(DropPilot.candidate(this,release)!=null)break;
                        android.os.StatFs disk=new android.os.StatFs(getCacheDir().getAbsolutePath());
                        if(disk.getAvailableBytes()<release.size*2+10L*1024*1024)continue;
                        File dir=DropPilot.directory(this);
                        file=ApkDownloader.downloadTo(this,release,pct->{},cancel,dir);
                        cancel.check();
                        InstallContract fresh=ContractClient.fetch(pin.slug);
                        release.requireSameArtifact(fresh);
                        InstalledState current=InstalledState.read(this,release.packageName);
                        if(InstallPolicy.evaluate(release,current,Build.VERSION.SDK_INT,Build.SUPPORTED_ABIS).mode
                                !=InstallPolicy.Mode.UPDATE)throw new SecurityException("Update-Status geändert.");
                        ApkVerifierUtil.verify(this,file,release,current);
                        cancel.check();
                        if(!DropPilot.record(this,pin,release,file))throw new SecurityException("DropPilot wurde deaktiviert.");
                        file=null;
                        break;
                    } catch(InterruptedException stopped) {return;}
                      catch(Exception fail) {retry=true;}
                    finally {if(file!=null)file.delete();}
                }
            } catch(Exception unavailable) {retry=true;}
            boolean wantsRetry=retry && DropPilot.enabled(this);
            main.post(()->{if(ticket==generation)jobFinished(params,wantsRetry);});
        });
        return true;
    }
    @Override public boolean onStopJob(JobParameters params) {
        generation++;
        if(cancellation!=null)cancellation.cancel();
        if(active!=null)active.cancel(true);
        return DropPilot.enabled(this);
    }
    @Override public void onDestroy() {
        generation++;
        if(cancellation!=null)cancellation.cancel();
        if(active!=null)active.cancel(true);
        worker.shutdownNow();
        super.onDestroy();
    }
}
