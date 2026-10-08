package de.rawinstinctai.apkdrop;

import android.app.*;
import android.content.Intent;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.os.*;
import android.view.View;
import android.view.WindowInsets;
import android.view.DisplayCutout;
import android.widget.*;
import java.io.File;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private EditText input;
    private Button checkButton,actionButton,addButton;
    private LinearLayout card;
    private TextView badge,title,meta,proof,permissions,status,notes,radar,queueStatus,backgroundStatus;
    private UpdateQueue queue=new UpdateQueue(java.util.Collections.emptyList());
    private boolean awaitingInstaller;
    private ProgressBar progress;
    private AppLibraryController library;
    private StoreController store;
    private volatile int generation;
    private volatile boolean downloadRunning;
    private Future<?> activeDownload;
    private ApkDownloader.Cancellation downloadCancellation;
    private Button cancelDownloadButton;
    private boolean detailBusy,refreshOnIdle;
    private boolean receiverRegistered;
    private final BroadcastReceiver packageChanges=new BroadcastReceiver() {
        @Override public void onReceive(Context context,Intent intent) {
            if(intent==null || intent.getData()==null || (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())
                    && intent.getBooleanExtra(Intent.EXTRA_REPLACING,false))) return;
            String packageName=intent.getData().getSchemeSpecificPart();
            if(!library.tracksPackage(packageName) && (currentRelease==null || !currentRelease.packageName.equals(packageName))) return;
            library.refreshInstalled();
            if(detailBusy) refreshOnIdle=true;
            else refreshCurrent();
        }
    };

    private InstallContract currentRelease;
    private InstalledState currentInstalled;
    private InstallPolicy.Result currentDecision;
    private File verifiedApk;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_main);
        applySystemInsets();
        if(Build.VERSION.SDK_INT>=33) getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,this::backAction);
        input=findViewById(R.id.urlInput); checkButton=findViewById(R.id.checkButton); actionButton=findViewById(R.id.actionButton);
        addButton=findViewById(R.id.addButton); notes=findViewById(R.id.notesText);
        card=findViewById(R.id.releaseCard); badge=findViewById(R.id.statusBadge);
        title=findViewById(R.id.titleText); meta=findViewById(R.id.metaText); proof=findViewById(R.id.proofText);
        permissions=findViewById(R.id.permissionsText); status=findViewById(R.id.statusText); progress=findViewById(R.id.progress);
        library=new AppLibraryController(this,io,this::selectSingle,this::updateSaveButton);
        library.onUpdates(this::startUpdates);
        store=new StoreController(this,library,this::selectSingle);
        store.restoreTab(state==null?0:state.getInt("storeTab",0));
        radar=findViewById(R.id.radarText); queueStatus=findViewById(R.id.queueStatus);
        queue=new AppLibraryStore(this).queue(); awaitingInstaller=library.pendingLaunched();
        findViewById(R.id.queueSkip).setOnClickListener(v->{ if(!detailBusy) nextUpdate(); });
        findViewById(R.id.queueCancel).setOnClickListener(v->{ if(!detailBusy) { clearQueue(); toast("Update-Runde beendet."); } });
        backgroundStatus=findViewById(R.id.backgroundStatus);
        Switch background=findViewById(R.id.backgroundSwitch);
        background.setChecked(UpdateScheduler.enabled(this));
        background.setOnCheckedChangeListener((button,enabled)->{
            UpdateScheduler.enabled(this,enabled); updateBackgroundStatus();
        });
        findViewById(R.id.notificationsButton).setOnClickListener(v->{
            if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    !=android.content.pm.PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},44);
            else startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName()));
        });
        UpdateScheduler.reconcile(this); updateBackgroundStatus(); updateQueueUi();

        checkButton.setOnClickListener(v->{
            try { selectSingle(SlugParser.parse(input.getText().toString())); }
            catch(Exception e) { toast(message(e)); }
        });
        actionButton.setOnClickListener(v->onAction());
        cancelDownloadButton=findViewById(R.id.cancelDownload);
        cancelDownloadButton.setOnClickListener(v->cancelDownload());
        findViewById(R.id.receiptButton).setOnClickListener(v->{
            if(currentRelease==null || detailBusy) return;
            try { startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(currentRelease.receiptUrl))); }
            catch(Exception unavailable) { toast("Der Release-Beleg konnte nicht geöffnet werden."); }
        });
        addButton.setOnClickListener(v->{
            if(currentRelease==null || detailBusy || library.checking()) return;
            try { library.add(currentRelease,currentInstalled,currentDecision); toast("In Meine Apps gespeichert."); }
            catch(Exception e) { toast(message(e)); }
        });
        if(state==null && queue.current()==null && library.pendingInstaller()==null
                && isInstallIntent(getIntent())) handleIntent(getIntent());
        else {
            String slug=state==null?null:state.getString("activeSlug");
            if(slug==null) slug=queue.current();
            if(slug==null) slug=library.pendingInstaller();
            if(slug!=null) load(slug);
        }
    }

    private void applySystemInsets() {
        View root=findViewById(R.id.pageRoot);
        final int left=root.getPaddingLeft(),top=root.getPaddingTop();
        final int right=root.getPaddingRight(),bottom=root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((view,insets)->{
            int l,t,r,b;
            if(Build.VERSION.SDK_INT>=30) {
                android.graphics.Insets safe=insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                l=safe.left; t=safe.top; r=safe.right; b=safe.bottom;
            } else {
                l=insets.getSystemWindowInsetLeft(); t=insets.getSystemWindowInsetTop();
                r=insets.getSystemWindowInsetRight(); b=insets.getSystemWindowInsetBottom();
                if(Build.VERSION.SDK_INT>=28) {
                    DisplayCutout cutout=insets.getDisplayCutout();
                    if(cutout!=null) {
                        l=Math.max(l,cutout.getSafeInsetLeft()); t=Math.max(t,cutout.getSafeInsetTop());
                        r=Math.max(r,cutout.getSafeInsetRight()); b=Math.max(b,cutout.getSafeInsetBottom());
                    }
                }
            }
            // Always start from original padding, so repeat dispatches cannot accumulate it.
            view.setPadding(left+l,top+t,right+r,bottom+b);
            return Build.VERSION.SDK_INT>=30 ? WindowInsets.CONSUMED : insets.consumeSystemWindowInsets();
        });
        root.requestApplyInsets();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent); handleIntent(intent);
    }

    @Override protected void onResume() {
        super.onResume();
        store.resume();
        updateBackgroundStatus();
        library.refreshInstalled();
        if(detailBusy) refreshOnIdle=true;
        else refreshCurrent();
    }

    @Override protected void onStart() {
        super.onStart();
        IntentFilter filter=new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED); filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED); filter.addDataScheme("package");
        if(Build.VERSION.SDK_INT>=33) registerReceiver(packageChanges,filter,Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(packageChanges,filter);
        receiverRegistered=true;
    }

    @Override protected void onStop() {
        if(receiverRegistered) { unregisterReceiver(packageChanges); receiverRegistered=false; }
        super.onStop();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        if(currentRelease!=null) state.putString("activeSlug",currentRelease.slug);
        state.putInt("storeTab",store.tab());
        super.onSaveInstanceState(state);
    }

    @Override protected void onDestroy() {
        generation++;
        if(downloadCancellation!=null) downloadCancellation.cancel();
        downloadCancellation=null;
        if(activeDownload!=null) activeDownload.cancel(true);
        store.close(); library.close(); io.shutdownNow(); super.onDestroy();
    }

    private static boolean isInstallIntent(Intent intent) {
        return intent!=null && (intent.getData()!=null || (Intent.ACTION_SEND.equals(intent.getAction())
                && "text/plain".equals(intent.getType()) && intent.hasExtra(Intent.EXTRA_TEXT)));
    }

    private void handleIntent(Intent intent) {
        if(!isInstallIntent(intent)) return;
        boolean shared=Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType());
        try {
            String raw=shared?intent.getStringExtra(Intent.EXTRA_TEXT):intent.getDataString();
            String slug=shared?SlugParser.parseShared(raw):SlugParser.parse(raw);
            input.setText(slug);
            selectSingle(slug);
        } catch(Exception e) { toast(message(e)); }
    }

    private void load(String slug) {
        store.showDetail();
        final int ticket=++generation;
        final AppLibrary.Entry saved=library.find(slug);
        if(slug.equals(queue.current()) && saved==null) { showError("Diese App ist nicht mehr in Meine Apps. Überspringe sie in der Update-Runde."); return; }
        input.setText(slug);
        resetCandidate(); setBusy(true,"Prüfe Release-Vertrag …"); card.setVisibility(View.VISIBLE);
        io.execute(()->{
            try {
                InstallContract release=ContractClient.fetch(slug);
                if(saved!=null) saved.requireIdentity(release.slug,release.packageName,release.signers);
                InstalledState installed=InstalledState.read(this,release.packageName);
                InstallPolicy.Result decision=InstallPolicy.evaluate(release,installed,Build.VERSION.SDK_INT,Build.SUPPORTED_ABIS);
                post(ticket,()->{
                    show(release,installed,decision,false,true);
                    if(!awaitingInstaller) library.clearPendingInstaller();
                });
            } catch(Exception e) { post(ticket,()->showError(message(e))); }
        });
    }

    private void show(InstallContract release,InstalledState installed,InstallPolicy.Result decision,boolean keepVerified,boolean fetched) {
        if(awaitingInstaller && installed!=null && installed.versionCode>=release.versionCode
                && installed.signers.equals(release.signers) && decision.mode==InstallPolicy.Mode.CURRENT) {
            awaitingInstaller=false; library.clearPendingInstaller();
            if(release.slug.equals(queue.current())) { boolean more=queue.size()>1; nextUpdate(); if(more) return; }
        }
        File prior=keepVerified?verifiedApk:null;
        currentRelease=release; currentInstalled=installed; currentDecision=decision; verifiedApk=null;
        title.setText(release.appName);
        if(fetched) store.releaseDetails(release.slug);
        radar.setText(ReleaseIntelligence.summary(release,installed,decision)+"\n\n"
                +ReleaseIntelligence.radar(release,installed,library.previous(release)));
        meta.setText((installed==null?"Nicht installiert":"Installiert: "+AppLibraryController.installedVersion(installed))
                +"\nVerfügbar: v"+release.version+" · "+formatSize(release.size)+" · "+release.channel+"\n"+release.packageName);
        String signer=String.join("\n",release.signers);
        proof.setText("Nachweise im Release-Vertrag (vor Download noch nicht lokal verifiziert)\n\nSHA-256  "+release.sha256
                +"\nSIGNER   "+signer
                +"\nSDK      "+release.minSdk+" → "+(release.targetSdk==0?"—":release.targetSdk));

        StringBuilder p=new StringBuilder();
        if(installed==null) {
            p.append("Deklarierte Berechtigungen: ").append(release.permissions.size()).append("\n");
            for(String permission:release.permissions) p.append("• ").append(human(permission)).append("\n");
        }
        else if(release.permissions.equals(installed.permissions)) p.append("✓ Keine neuen Berechtigungen gegenüber der installierten Version.");
        else {
            java.util.Set<String> added=new java.util.LinkedHashSet<>(release.permissions); added.removeAll(installed.permissions);
            p.append("Neue Berechtigungen (+").append(added.size()).append("):\n");
            for(String permission:added) p.append("• ").append(human(permission)).append("\n");
        }
        if(installed!=null) {
            java.util.Set<String> removed=new java.util.LinkedHashSet<>(installed.permissions);
            removed.removeAll(release.permissions);
            if(!removed.isEmpty()) {
                p.append("\n\nEntfallende Berechtigungen (−").append(removed.size()).append("):\n");
                for(String permission:removed) p.append("• ").append(human(permission)).append("\n");
            }
        }
        if(!decision.sensitiveAdded.isEmpty()) {
            p.append(installed==null ? "\n\n⚠ Sensible Berechtigungen der App:\n"
                    : "\n\n⚠ Neue sensible Berechtigungen:\n");
            for(String permission:decision.sensitiveAdded) p.append("• ").append(human(permission)).append("\n");
        }
        permissions.setText(p.toString().trim()); status.setText(decision.reason);
        notes.setVisibility(release.notes.trim().isEmpty()?View.GONE:View.VISIBLE);
        notes.setText("Änderungen laut Entwickler\n"+release.notes);

        boolean actionable=false;
        switch(decision.mode) {
            case INSTALL -> { badge.setText(getString(R.string.message_mainactivity_1)); actionable=true; }
            case UPDATE -> { badge.setText(getString(R.string.message_mainactivity_2)); actionable=true; }
            case CURRENT -> badge.setText(getString(R.string.message_mainactivity_3));
            case BLOCKED -> badge.setText(getString(R.string.message_mainactivity_4));
        }
        actionButton.setVisibility(actionable?View.VISIBLE:View.GONE);
        actionButton.setEnabled(actionable);
        actionButton.setText(getString(R.string.ui_activity_main_30));
        if(actionable && prior!=null && prior.isFile()) {
            verifiedApk=prior; actionButton.setText(getString(R.string.message_mainactivity_5));
            status.setText(getString(R.string.message_mainactivity_6));
        }
        if(fetched) library.rememberChecked(release,installed,decision);
        setBusy(false,"");
        updateQueueUi();
    }

    private void onAction() {
        if(currentRelease==null || currentDecision==null) return;
        if(verifiedApk!=null && verifiedApk.isFile()) {
            handoffVerified();
            return;
        }
        if(!(currentDecision.mode==InstallPolicy.Mode.INSTALL || currentDecision.mode==InstallPolicy.Mode.UPDATE)) return;

        if(!currentDecision.sensitiveAdded.isEmpty()) {
            final int ticket=generation;
            final InstallContract release=currentRelease;
            new AlertDialog.Builder(this)
                    .setTitle("Sensible Berechtigungen erkannt")
                    .setMessage(currentRelease.appName+" deklariert folgende sensible Berechtigung(en):\n\n"+join(currentDecision.sensitiveAdded)
                            +"\n\nDie APK wird erst nach deiner Bestätigung geladen und anschließend lokal geprüft."
                            +"\n\nDiese Bestätigung erteilt der App keine Android-Berechtigung.")
                    .setNegativeButton("Abbrechen",null)
                    .setPositiveButton("Prüfung starten",(dialog,which)->{
                        if(ticket==generation && release==currentRelease && !detailBusy) downloadAndVerify();
                    })
                    .show();
            return;
        }
        downloadAndVerify();
    }

    private void downloadAndVerify() {
        final int ticket=generation;
        final InstallContract release=currentRelease;
        setBusy(true,"APK wird in privaten App-Speicher geladen …");
        actionButton.setEnabled(false);
        progress.setVisibility(View.VISIBLE); progress.setIndeterminate(false); progress.setProgress(0);
        status.setText(getString(R.string.message_mainactivity_7));
        downloadRunning=true;
        downloadCancellation=new ApkDownloader.Cancellation();
        final ApkDownloader.Cancellation cancellation=downloadCancellation;
        cancelDownloadButton.setVisibility(View.VISIBLE);

        activeDownload=io.submit(()->{
            File file=null;
            try {
                requireFreshRelease(release);
                cancellation.check();
                file=ApkDownloader.download(this,release,pct->post(ticket,()->{
                    progress.setProgress(pct);
                    status.setText(pct+" % heruntergeladen · lokale Prüfung folgt");
                }),cancellation);
                cancellation.check();
                post(ticket,()->status.setText(getString(R.string.message_mainactivity_8)));
                InstalledState fresh=InstalledState.read(this,release.packageName);
                InstallPolicy.Result freshDecision=InstallPolicy.evaluate(release,fresh,Build.VERSION.SDK_INT,Build.SUPPORTED_ABIS);
                if(freshDecision.mode==InstallPolicy.Mode.BLOCKED || freshDecision.mode==InstallPolicy.Mode.CURRENT)
                    throw new SecurityException(freshDecision.reason);
                ApkVerifierUtil.verify(this,file,release,fresh);
                cancellation.check();
                final File verifiedFile=file;
                runOnUiThread(()->{
                    if(ticket!=generation || isFinishing() || isDestroyed() || !downloadRunning
                            || cancellation!=downloadCancellation) {
                        verifiedFile.delete(); return;
                    }
                    verified(verifiedFile);
                });
            } catch(Exception e) {
                if(file!=null) file.delete();
                post(ticket,()->downloadError(message(e)));
            }
        });
    }

    private void cancelDownload() {
        if(!downloadRunning) return;
        generation++;
        downloadRunning=false;
        java.util.concurrent.Future<?> pending=activeDownload;
        ApkDownloader.Cancellation cancellation=downloadCancellation;
        activeDownload=null;
        downloadCancellation=null;
        if(cancellation!=null) cancellation.cancel();
        if(pending!=null) pending.cancel(true);
        cancelDownloadButton.setVisibility(View.GONE);
        setBusy(false,"");
        actionButton.setEnabled(true);
        actionButton.setText(getString(R.string.ui_activity_main_30));
        status.setText("Download abgebrochen. Du kannst erneut beginnen.");
    }

    private void verified(File file) {
        downloadRunning=false; activeDownload=null; downloadCancellation=null;
        cancelDownloadButton.setVisibility(View.GONE);
        verifiedApk=file;
        setBusy(false,""); actionButton.setEnabled(true);
        actionButton.setText(getString(R.string.message_mainactivity_5));
        status.setText(getString(R.string.message_mainactivity_9));
    }

    private void downloadError(String text) {
        downloadRunning=false; activeDownload=null; downloadCancellation=null;
        cancelDownloadButton.setVisibility(View.GONE);
        verifiedApk=null;
        setBusy(false,""); actionButton.setEnabled(true);
        actionButton.setText(getString(R.string.message_mainactivity_10)); status.setText(text);
    }

    private void resetCandidate() {
        if(downloadCancellation!=null) downloadCancellation.cancel();
        downloadCancellation=null;
        if(activeDownload!=null) activeDownload.cancel(true);
        activeDownload=null; downloadRunning=false;
        if(cancelDownloadButton!=null) cancelDownloadButton.setVisibility(View.GONE);
        currentRelease=null; currentInstalled=null; currentDecision=null; verifiedApk=null;
        actionButton.setVisibility(View.GONE);
        addButton.setVisibility(View.GONE);
    }

    private void setBusy(boolean busy,String text) {
        detailBusy=busy;
        checkButton.setEnabled(!busy);
        input.setEnabled(!busy);
        library.setDetailBusy(busy);
        if(busy) { progress.setVisibility(View.VISIBLE); progress.setIndeterminate(true); status.setText(text); }
        else { progress.setIndeterminate(false); progress.setVisibility(View.GONE); }
        updateSaveButton(); updateQueueUi();
        if(!busy && refreshOnIdle) { refreshOnIdle=false; refreshCurrent(); }
    }

    private void showError(String text) {
        resetCandidate(); setBusy(false,""); card.setVisibility(View.VISIBLE); badge.setText(getString(R.string.message_mainactivity_11));
        radar.setText(getString(R.string.message_mainactivity_12)); title.setText(getString(R.string.message_mainactivity_13)); meta.setText(getString(R.string.message_mainactivity_12)); proof.setText(getString(R.string.message_mainactivity_12)); permissions.setText(getString(R.string.message_mainactivity_12)); notes.setVisibility(View.GONE); status.setText(text);
    }

    private void updateSaveButton() {
        if(store!=null) store.changed();
        if(library==null || addButton==null) return;
        boolean saved=currentRelease!=null && library.find(currentRelease.slug)!=null;
        addButton.setVisibility(currentRelease==null?View.GONE:View.VISIBLE);
        addButton.setText(saved?"In Meine Apps gespeichert ✓":"Zu meinen Apps hinzufügen +");
        addButton.setEnabled(currentRelease!=null&&!saved&&!detailBusy&&!library.checking()&&library.writable());
    }

    private void refreshCurrent() {
        if(currentRelease==null || detailBusy) return;
        final InstallContract release=currentRelease;
        final int ticket=generation;
        io.execute(()->{
            try {
                InstalledState installed=InstalledState.read(this,release.packageName);
                InstallPolicy.Result decision=InstallPolicy.evaluate(release,installed,Build.VERSION.SDK_INT,Build.SUPPORTED_ABIS);
                post(ticket,()->{
                    if(!detailBusy && currentRelease==release) {
                        show(release,installed,decision,true,false);
                        if(!awaitingInstaller) library.clearPendingInstaller();
                    }
                });
            } catch(Exception e) { post(ticket,()->{ if(!detailBusy) showError(message(e)); }); }
        });
    }

    private void handoffVerified() {
        final InstallContract release=currentRelease;
        final File file=verifiedApk;
        final int ticket=generation;
        setBusy(true,"Prüfe den aktuellen Installationsstand …"); actionButton.setEnabled(false);
        io.execute(()->{
            try {
                requireFreshRelease(release);
                InstalledState fresh=InstalledState.read(this,release.packageName);
                InstallPolicy.Result decision=InstallPolicy.evaluate(release,fresh,Build.VERSION.SDK_INT,Build.SUPPORTED_ABIS);
                if(decision.mode==InstallPolicy.Mode.CURRENT || decision.mode==InstallPolicy.Mode.BLOCKED) {
                    post(ticket,()->show(release,fresh,decision,false,false)); return;
                }
                ApkVerifierUtil.verify(this,file,release,fresh);
                post(ticket,()->{
                    try {
                        library.pendingInstaller(release.slug);
                        boolean launched=InstallerHandoff.open(this,file);
                        awaitingInstaller=launched; library.pendingLaunched(launched);
                        setBusy(false,""); actionButton.setEnabled(true);
                        status.setText(launched?"Android übernimmt jetzt die Installation. Der Status wird bei deiner Rückkehr aktualisiert."
                                :"Erlaube APKDrop einmal als Installationsquelle und kehre danach zurück.");
                    } catch(Exception e) { library.clearPendingInstaller(); downloadError(message(e)); }
                });
            } catch(Exception e) { post(ticket,()->downloadError(message(e))); }
        });
    }

    private void requireFreshRelease(InstallContract expected) throws Exception {
        InstallContract fresh=ContractClient.fetch(expected.slug);
        AppLibrary.Entry pin=library.find(expected.slug);
        if(expected.slug.equals(queue.current()) && pin==null) throw new SecurityException("Diese App wurde aus Meine Apps entfernt.");
        if(pin!=null) pin.requireIdentity(fresh.slug,fresh.packageName,fresh.signers);
        expected.requireSameArtifact(fresh);
    }

    private void selectSingle(String slug) { clearQueue(); load(slug); }
    private void startUpdates(java.util.List<String> slugs) {
        queue=new UpdateQueue(slugs); new AppLibraryStore(this).queue(queue);
        awaitingInstaller=false; library.clearPendingInstaller(); updateQueueUi(); load(queue.current());
    }
    private void nextUpdate() {
        if(queue.current()==null) return;
        queue=queue.next(); awaitingInstaller=false; library.clearPendingInstaller();
        new AppLibraryStore(this).queue(queue); updateQueueUi();
        if(queue.current()==null) { toast("Update-Runde beendet. Dein Installationsstand wird neu eingelesen."); library.refreshInstalled(); }
        else load(queue.current());
    }
    private void clearQueue() {
        queue=new UpdateQueue(java.util.Collections.emptyList()); new AppLibraryStore(this).queue(queue);
        awaitingInstaller=false; library.clearPendingInstaller(); updateQueueUi();
    }
    private void updateQueueUi() {
        if(queueStatus==null) return;
        findViewById(R.id.queueCard).setVisibility(queue.current()==null?View.GONE:View.VISIBLE);
        queueStatus.setText("Update-Runde · "+queue.size()+" verbleibend\nJede Installation bestätigst du in Android.");
        findViewById(R.id.queueSkip).setEnabled(!detailBusy);
        findViewById(R.id.queueCancel).setEnabled(!detailBusy);
    }
    private void updateBackgroundStatus() {
        if(backgroundStatus==null) return;
        String text=UpdateScheduler.enabled(this)?"Automatische Prüfungen etwa alle 6 Stunden, sobald Android Netzwerk und Akku freigibt. Nur gespeicherte Apps; keine APK-Downloads."
                :"Automatische Prüfungen sind ausgeschaltet.";
        text+=UpdateNotifications.allowed(this)?"\nUpdate-Benachrichtigungen erlaubt.":"\nBenachrichtigungen sind aus. Updates bleiben in der App sichtbar.";
        backgroundStatus.setText(text);
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants) {
        super.onRequestPermissionsResult(request,permissions,grants); updateBackgroundStatus();
    }

    private void post(int ticket,Runnable callback) {
        runOnUiThread(()->{ if(ticket==generation&&!isFinishing()&&!isDestroyed()) callback.run(); });
    }

    private static String human(String permission) { return ReleaseIntelligence.human(permission); }
    private static String join(java.util.Set<String> permissions) {
        StringBuilder out=new StringBuilder();
        for(String permission:permissions) out.append("• ").append(human(permission)).append("\n");
        return out.toString().trim();
    }
    private static String formatSize(long bytes) {
        return String.format(java.util.Locale.GERMANY,"%.1f MiB",bytes/1048576.0);
    }
    private static String message(Exception e) {
        String value=e.getMessage(); return value==null||value.trim().isEmpty()?"Vorgang fehlgeschlagen. Bitte erneut versuchen.":value;
    }
    // Android 13+ uses the registered gesture callback; older Android uses this method.
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() {backAction();}
    private void backAction() {
        if(detailBusy) {toast("Die laufende Prüfung bitte kurz abschließen lassen.");return;}
        if(!store.back()) finish();
    }
    private void toast(String text) { Toast.makeText(this,text,Toast.LENGTH_LONG).show(); }
}

