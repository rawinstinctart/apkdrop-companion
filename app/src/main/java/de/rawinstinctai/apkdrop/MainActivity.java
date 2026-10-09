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
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private static final int BACKUP_EXPORT=71, BACKUP_IMPORT=72;
    private char[] pendingBackupPassword;
    private boolean notesExpanded;
    private android.content.SharedPreferences pilotPreferences;
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener pilotListener=(prefs,key)->this.main.post(()->{if(!isDestroyed())updateDropPilotStatus();});
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
    private String verificationIssue="";
    private String pendingLink;
    private String deferredLink;
    private final Handler main=new Handler(Looper.getMainLooper());
    private boolean receiverRegistered,pilotReceiverRegistered;
    private final BroadcastReceiver pilotConditions=new BroadcastReceiver() {
        @Override public void onReceive(Context context,Intent intent) {updateDropPilotStatus();}
    };
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
        deferredLink=state==null?null:state.getString("deferredLink");
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
        store.restoreState(state);library.restoreState(state);
        radar=findViewById(R.id.radarText); queueStatus=findViewById(R.id.queueStatus);
        queue=new AppLibraryStore(this).queue(); awaitingInstaller=library.pendingLaunched();
        findViewById(R.id.queueSkip).setOnClickListener(v->{ if(!detailBusy) nextUpdate(); });
        findViewById(R.id.queueCancel).setOnClickListener(v->{ if(!detailBusy) { clearQueue(); toast("Update-Runde beendet."); } });
        backgroundStatus=findViewById(R.id.backgroundStatus);
        Switch background=findViewById(R.id.backgroundSwitch);
        background.setChecked(UpdateScheduler.enabled(this));
        background.setOnCheckedChangeListener((button,enabled)->{
            UpdateScheduler.enabled(this,enabled); updateBackgroundStatus(); updateSaveButton();
        });
        findViewById(R.id.notificationsButton).setOnClickListener(v->{
            if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    !=android.content.pm.PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},44);
            else startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName()));
        });
        Switch pilot=findViewById(R.id.dropPilotSwitch);
        pilot.setChecked(DropPilot.enabled(this));
        pilot.setOnCheckedChangeListener((button,enabled)->{
            DropPilot.enabled(this,enabled);
            updateDropPilotStatus();
        });
        pilotPreferences=DropPilot.preferences(this);
        pilotPreferences.registerOnSharedPreferenceChangeListener(pilotListener);
        findViewById(R.id.pilotOpen).setOnClickListener(v->{String slug=DropPilot.preparedSlug(this);if(slug!=null)selectSingle(slug);else updateDropPilotStatus();});
        findViewById(R.id.radarButton).setOnClickListener(v->{boolean open=radar.getVisibility()!=View.VISIBLE;radar.setVisibility(open?View.VISIBLE:View.GONE);((Button)v).setText(open?R.string.alpha12_radar_open:R.string.alpha12_radar_closed);});
        findViewById(R.id.notesButton).setOnClickListener(v->{notesExpanded=!notesExpanded;updateNotes();});
        UpdateScheduler.reconcile(this); DropPilot.reconcile(this);
        updateBackgroundStatus(); updateDropPilotStatus(); updateQueueUi();
        try {((TextView)findViewById(R.id.appVersion)).setText("APKDrop Companion · "+getPackageManager().getPackageInfo(getPackageName(),0).versionName);}
        catch(Exception unavailable) { /* Static version label remains a fallback. */ }
        findViewById(R.id.selfUpdate).setOnClickListener(v->{if(!detailBusy && !library.checking()) selectSingle("apkdrop-companion");});
        findViewById(R.id.backupExport).setOnClickListener(v->chooseBackup(true));
        findViewById(R.id.backupImport).setOnClickListener(v->chooseBackup(false));
        findViewById(R.id.historyButton).setOnClickListener(v->{
            TextView history=findViewById(R.id.historyText);boolean open=history.getVisibility()!=View.VISIBLE;
            history.setVisibility(open?View.VISIBLE:View.GONE);((Button)v).setText(open?"Beobachtete Versionen schließen −":"Beobachtete Versionen ansehen +");
        });

        checkButton.setOnClickListener(v->{
            importLink(input.getText().toString(),false);
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
            try {
                if(library.find(currentRelease.slug)==null) library.add(currentRelease,currentInstalled,currentDecision);
                UpdateScheduler.enabled(this,true);
                ((Switch)findViewById(R.id.backgroundSwitch)).setChecked(true);
                updateBackgroundStatus(); updateSaveButton();
                // The inline monitoring status is authoritative. Avoid an obscuring, duplicate Toast.
            }
            catch(Exception e) { toast(message(e)); }
        });
        if(state==null && !awaitingInstaller
                && isInstallIntent(getIntent())) handleIntent(getIntent());
        else {
            if(state==null&&awaitingInstaller&&isInstallIntent(getIntent()))handleIntent(getIntent());
            String link=state==null?null:state.getString("pendingLink");
            if(link!=null){importLink(link,true);return;}
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
        if(awaitingInstaller&&deferredLink!=null){
            awaitingInstaller=false;library.clearPendingInstaller();updateSaveButton();
        }
        store.resume();
        UpdateScheduler.reconcile(this); // Retry a previously rejected job after a manifest/app upgrade.
        DropPilot.reconcile(this);
        updateBackgroundStatus(); updateDropPilotStatus();
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
        IntentFilter conditions=new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        conditions.addAction(android.net.ConnectivityManager.CONNECTIVITY_ACTION);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(pilotConditions,conditions,Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(pilotConditions,conditions);
        pilotReceiverRegistered=true;
    }

    @Override protected void onPause(){store.pause();super.onPause();}

    @Override protected void onStop() {
        if(receiverRegistered) { unregisterReceiver(packageChanges); receiverRegistered=false; }
        if(pilotReceiverRegistered) {unregisterReceiver(pilotConditions);pilotReceiverRegistered=false;}
        super.onStop();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        if(currentRelease!=null) state.putString("activeSlug",currentRelease.slug);
        else if(pendingLink!=null) state.putString("pendingLink",pendingLink);
        if(deferredLink!=null)state.putString("deferredLink",deferredLink);
        store.saveState(state);library.saveState(state);
        super.onSaveInstanceState(state);
    }

    @Override protected void onDestroy() {
        if(pilotPreferences!=null)pilotPreferences.unregisterOnSharedPreferenceChangeListener(pilotListener);
        generation++;
        if(downloadCancellation!=null) downloadCancellation.pause();
        downloadCancellation=null;
        if(activeDownload!=null) activeDownload.cancel(true);
        eraseBackupPassword();
        store.close(); library.close(); io.shutdownNow(); super.onDestroy();
    }

    private void eraseBackupPassword() {
        if(pendingBackupPassword!=null)Arrays.fill(pendingBackupPassword,(char)0);
        pendingBackupPassword=null;
    }
    private void chooseBackup(boolean export) {
        if(detailBusy || library.checking()) {toast("Bitte erst die laufende Prüfung abschließen.");return;}
        EditText input=new EditText(this);
        input.setSingleLine(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint("Passwort · mindestens 10 Zeichen");
        new AlertDialog.Builder(this).setTitle(export?"Sicherung verschlüsseln":"Sicherung entschlüsseln")
                .setMessage("Nur du kennst dieses Passwort. Ohne Passwort lässt sich die Sicherung nicht wiederherstellen.")
                .setView(input).setNegativeButton("Abbrechen",null)
                .setPositiveButton("Weiter",(dialog,which)->{
                    eraseBackupPassword();
                    char[] pass=input.getText().toString().toCharArray();
                    input.setText("");
                    if(pass.length<10){Arrays.fill(pass,(char)0);toast("Mindestens 10 Zeichen erforderlich.");return;}
                    pendingBackupPassword=pass;
                    Intent intent=new Intent(export?Intent.ACTION_CREATE_DOCUMENT:Intent.ACTION_OPEN_DOCUMENT)
                            .addCategory(Intent.CATEGORY_OPENABLE).setType("application/json");
                    if(export)intent.putExtra(Intent.EXTRA_TITLE,"APKDrop-Sicherung.json");
                    try{startActivityForResult(intent,export?BACKUP_EXPORT:BACKUP_IMPORT);}
                    catch(Exception unavailable){eraseBackupPassword();toast("Dateiauswahl ist nicht verfügbar.");}
                }).show();
    }
    @Override @SuppressWarnings("deprecation")
    protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request!=BACKUP_EXPORT&&request!=BACKUP_IMPORT)return;
        char[] password=pendingBackupPassword;
        pendingBackupPassword=null;
        if(password==null)return;
        if(result!=RESULT_OK || data==null || data.getData()==null) {Arrays.fill(password,(char)0);return;}
        android.net.Uri uri=data.getData();
        io.execute(()->{
            try {
                if(request==BACKUP_EXPORT) {
                    String backup=BackupCodec.export(this,password);
                    try(OutputStream stream=getContentResolver().openOutputStream(uri,"w")) {
                        if(stream==null)throw new java.io.IOException("Keine Schreibberechtigung für die Sicherung.");
                        stream.write(backup.getBytes(StandardCharsets.UTF_8));
                    }
                    runOnUiThread(()->toast("Verschlüsselte Sicherung erstellt."));
                } else {
                    ByteArrayOutputStream buffer=new ByteArrayOutputStream();
                    try(InputStream stream=getContentResolver().openInputStream(uri)) {
                        if(stream==null)throw new java.io.IOException("Sicherung nicht lesbar.");
                        byte[] chunk=new byte[4096];int n;
                        while((n=stream.read(chunk))!=-1) {
                            if(buffer.size()+n>320000)throw new SecurityException("Sicherung ist zu groß.");
                            buffer.write(chunk,0,n);
                        }
                    }
                    BackupCodec.Plan plan=BackupCodec.preview(new String(buffer.toByteArray(),StandardCharsets.UTF_8),password);
                    runOnUiThread(()->{if(isDestroyed()||isFinishing())return;new AlertDialog.Builder(this)
                        .setTitle("Gerätewechsel bestätigen")
                        .setMessage(plan.apps+" gespeicherte Apps und "+plan.follows+" Entwickler-Follows übernehmen?\n\n"
                                +"Bestehende Identitäten werden nicht überschrieben. Keine APK wird installiert.")
                        .setNegativeButton("Abbrechen",null)
                        .setPositiveButton("Zusammenführen",(d,w)->io.execute(()->{
                            try {BackupCodec.apply(this,plan);
                                runOnUiThread(()->{
                                    try {library.reloadAfterImport();store.changed();updateSaveButton();toast("Sicherung übernommen.");}
                                    catch(Exception refresh){toast(message(refresh));}
                                });
                            }catch(Exception failed){runOnUiThread(()->toast(message(failed)));}
                        })).show();});
                }
            }catch(Exception e){runOnUiThread(()->toast("Sicherung fehlgeschlagen: "+message(e)));}
            finally {Arrays.fill(password,(char)0);}
        });
    }
    private static boolean isInstallIntent(Intent intent) {
        return intent!=null && (intent.getData()!=null || (Intent.ACTION_SEND.equals(intent.getAction())
                && "text/plain".equals(intent.getType()) && intent.hasExtra(Intent.EXTRA_TEXT)));
    }

    private void handleIntent(Intent intent) {
        if(!isInstallIntent(intent)) return;
        boolean shared=Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType());
        try {
            CharSequence text=shared?intent.getCharSequenceExtra(Intent.EXTRA_TEXT):null;
            String raw=shared?(text==null?null:text.toString()):intent.getDataString();
            importLink(raw,shared);
        } catch(Exception e) { toast(message(e)); }
    }

    private void importLink(String raw,boolean shared) {
        String slug=null;
        try {slug=shared?SlugParser.parseShared(raw):SlugParser.parse(raw);}
        catch(IllegalArgumentException invalid) {
            try {LinkImport.repository(raw);}catch(Exception unsupported){toast(message(unsupported));return;}
        }
        if(detailBusy || library.checking() || awaitingInstaller){
            deferredLink=raw;toast("Link vorgemerkt. Er öffnet sich nach der laufenden Prüfung oder Installation.");return;
        }
        if(slug!=null){selectSingle(slug);return;}
        pendingLink=raw;
        store.showDetail();final int ticket=++generation;resetCandidate();card.setVisibility(View.VISIBLE);
        setBusy(true,"Suche die bestätigte APKDrop-App zum GitHub-Repository …");
        io.execute(()->{
            try {String mappedSlug=LinkImport.resolve(raw);post(ticket,()->{
                setBusy(false,"");toast("Repository zugeordnet. Prüfe den aktuellen APKDrop-Release.");selectSingle(mappedSlug);
            });}catch(Exception failed){post(ticket,()->showError(message(failed)));}
        });
    }

    private void load(String slug) {
        pendingLink=queue.current()==null&&library.pendingInstaller()==null
                ?"https://apkdrop.rawinstinctai.de/install/"+slug:null;
        store.showDetail();
        final int ticket=++generation;
        final AppLibrary.Entry saved=library.find(slug);
        if(slug.equals(queue.current()) && saved==null) { showError("Diese App ist nicht mehr in Meine Apps. Überspringe sie in der Update-Runde."); return; }
        input.setText(slug);
        resetCandidate(); setBusy(true,"Prüfe Release-Vertrag …"); card.setVisibility(View.VISIBLE);
        io.execute(()->{
            try {
                InstallContract release=ContractClient.fetch(slug);
                if("apkdrop-companion".equals(slug)) CompanionIdentity.require(release,InstalledState.read(this,getPackageName()));
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
        if(fetched)verificationIssue="";
        currentRelease=release; currentInstalled=installed; currentDecision=decision; verifiedApk=null;
        findViewById(R.id.radarButton).setEnabled(true);
        ((TextView)findViewById(R.id.dropTrustStatus)).setText(DropTrust.describe(release,installed,library.previous(release),keepVerified));
        title.setText(release.appName);findViewById(R.id.historyButton).setVisibility(View.VISIBLE);findViewById(R.id.trustButton).setEnabled(true);findViewById(R.id.receiptButton).setEnabled(true);
        TextView history=findViewById(R.id.historyText);history.setText(ReleaseNotes.render(library.observedHistory(release)));
        if(fetched){history.setVisibility(View.GONE);((Button)findViewById(R.id.historyButton)).setText("Beobachtete Versionen ansehen +");}

        if(fetched) {
            notesExpanded=false;radar.setVisibility(View.GONE);((Button)findViewById(R.id.radarButton)).setText(R.string.alpha12_radar_closed);
            findViewById(R.id.trustSummary).setVisibility(View.GONE);
            proof.setVisibility(View.GONE);permissions.setVisibility(View.GONE);
            ((Button)findViewById(R.id.trustButton)).setText("APK Trust Center · Nachweise ansehen +");
        }
        if(fetched) store.releaseDetails(release.slug);
        radar.setText((decision.mode==InstallPolicy.Mode.CURRENT?"":ReleaseIntelligence.summary(release,installed,decision)+"\n\n")
                +ReleaseIntelligence.radar(release,installed,library.previous(release)));
        meta.setText((installed==null?"Nicht installiert":"Installiert: "+AppLibraryController.installedVersion(installed))
                +"\n"+(decision.mode==InstallPolicy.Mode.CURRENT?"Neueste Version: ":"Verfügbar: ")+DisplayText.version(release.version)+" · "+formatSize(release.size)+" · "+release.channel);
        String signer=String.join("\n",release.signers);
        proof.setText("Paket: "+release.packageName+"\nVersionscode: "+release.versionCode+"\n\nNachweise im Release-Vertrag (vor Download noch nicht lokal verifiziert)\n\nSHA-256  "+release.sha256
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
        updateNotes();

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
        if(actionable && prior==null && DropPilot.candidate(this,release)!=null)
            actionButton.setText("Vorbereitetes Update prüfen →");
        if(actionable && prior!=null && prior.isFile()) {
            verifiedApk=prior; actionButton.setText(getString(R.string.message_mainactivity_5));
            status.setText(getString(R.string.message_mainactivity_6));
        }
        updateTrust(verifiedApk!=null);
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
                            +"\n\nDie APK wird erst nach deiner Bestätigung verarbeitet und vor Androids Installer lokal geprüft."
                            +"\n\nDiese Bestätigung erteilt der App keine Android-Berechtigung.")
                    .setNegativeButton("Abbrechen",null)
                    .setPositiveButton("Prüfung starten",(dialog,which)->{
                        if(ticket==generation && release==currentRelease && !detailBusy) startAcquisition();
                    })
                    .show();
            return;
        }
        startAcquisition();
    }

    private void startAcquisition() {
        File prepared=DropPilot.candidate(this,currentRelease);
        if(prepared!=null && currentDecision.mode==InstallPolicy.Mode.UPDATE)verifyPrepared(prepared);
        else downloadAndVerify();
    }

    private void verifyPrepared(File prepared) {
        final int ticket=generation;
        final InstallContract release=currentRelease;
        setBusy(true,"DropPilot · Vorbereiteten Download erneut lokal prüfen …");
        actionButton.setEnabled(false);
        io.submit(()->{
            File candidate=null;
            try {
                requireFreshRelease(release);
                InstalledState installed=InstalledState.read(this,release.packageName);
                if(InstallPolicy.evaluate(release,installed,Build.VERSION.SDK_INT,Build.SUPPORTED_ABIS).mode!=InstallPolicy.Mode.UPDATE)
                    throw new SecurityException("Dieser Release ist kein aktuelles Update mehr.");
                File directory=new File(getCacheDir(),"apkdrop");
                if(!directory.isDirectory()&&!directory.mkdirs())throw new java.io.IOException("Privater APK-Speicher nicht verfügbar.");
                VerifiedApkFiles.prepare(directory);
                candidate=new File(directory,VerifiedApkFiles.newName());
                java.nio.file.Files.copy(prepared.toPath(),candidate.toPath());
                ApkVerifierUtil.verify(this,candidate,release,installed);
                requireFreshRelease(release);
                final File ready=candidate;
                runOnUiThread(()->{
                    if(ticket!=generation || isDestroyed() || isFinishing()) {ready.delete();return;}
                    verified(ready); // Still requires a separate tap for Android's installer.
                });
                candidate=null;
            }catch(Exception e){post(ticket,()->downloadError(message(e)));}
            finally {if(candidate!=null)candidate.delete();}
        });
    }

    private void downloadAndVerify() {
        final int ticket=generation;
        final InstallContract release=currentRelease;
        verificationIssue="";updateTrust(false);
        setBusy(true,"Schritt 1/3 · Release vor Download erneut prüfen …");
        actionButton.setEnabled(false);
        progress.setVisibility(View.VISIBLE); progress.setIndeterminate(false); progress.setProgress(0);
        status.setText("Schritt 1/3 · Release vor Download erneut prüfen …");
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
                    status.setText("Schritt 2/3 · Download "+pct+" % von "+formatSize(release.size)+"\nAnschließend folgen Hash-, Signatur- und Paketprüfung.");
                }),cancellation);
                cancellation.check();
                post(ticket,()->{progress.setIndeterminate(true);status.setText("Schritt 3/3 · APK lokal prüfen …");});
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
        verificationIssue="Abgebrochen · Keine verifizierte APK bereit.";updateTrust(false);
        status.setText("Download abgebrochen. Du kannst erneut beginnen.");
    }

    private void verified(File file) {
        downloadRunning=false; activeDownload=null; downloadCancellation=null;
        cancelDownloadButton.setVisibility(View.GONE);
        verifiedApk=file;verificationIssue="";updateTrust(true);
        setBusy(false,""); actionButton.setEnabled(true);
        actionButton.setText(getString(R.string.message_mainactivity_5));
        status.setText(getString(R.string.message_mainactivity_9));
    }

    private void downloadError(String text) {
        downloadRunning=false; activeDownload=null; downloadCancellation=null;
        cancelDownloadButton.setVisibility(View.GONE);
        verifiedApk=null;verificationIssue="Download oder APK-Prüfung fehlgeschlagen: "+text;updateTrust(false);
        setBusy(false,""); actionButton.setEnabled(true);
        actionButton.setText(getString(R.string.message_mainactivity_10)); status.setText(text);
    }

    private void resetCandidate() {
        if(downloadCancellation!=null) downloadCancellation.cancel();
        downloadCancellation=null;
        if(activeDownload!=null) activeDownload.cancel(true);
        activeDownload=null; downloadRunning=false;
        if(cancelDownloadButton!=null) cancelDownloadButton.setVisibility(View.GONE);
        if(store!=null)store.clearReleaseDetails();
        findViewById(R.id.notesPanel).setVisibility(View.GONE);notesExpanded=false;
        findViewById(R.id.trustSummary).setVisibility(View.GONE);
        ((Button)findViewById(R.id.trustButton)).setText("APK Trust Center · Nachweise ansehen +");
        ((Button)findViewById(R.id.radarButton)).setText(R.string.alpha12_radar_closed);
        findViewById(R.id.radarButton).setEnabled(false);radar.setVisibility(View.GONE);
        title.setText("App wird geprüft …");badge.setText("PRÜFUNG AUSSTEHEND");meta.setText("");radar.setText("");notes.setVisibility(View.GONE);
        proof.setText("");permissions.setText("");notes.setText("");
        proof.setVisibility(View.GONE);permissions.setVisibility(View.GONE);findViewById(R.id.historyText).setVisibility(View.GONE);
        ((TextView)findViewById(R.id.historyText)).setText("");findViewById(R.id.historyButton).setVisibility(View.GONE);
        findViewById(R.id.trustButton).setEnabled(false);findViewById(R.id.receiptButton).setEnabled(false);
        currentRelease=null; currentInstalled=null; currentDecision=null; verifiedApk=null;verificationIssue="";updateTrust(false);
        actionButton.setVisibility(View.GONE);
        addButton.setVisibility(View.GONE);
    }

    private void updateTrust(boolean verified) {
        ((TextView)findViewById(R.id.dropTrustStatus)).setText(DropTrust.describe(currentRelease,currentInstalled,currentRelease==null?null:library.previous(currentRelease),verified));
        ((TextView)findViewById(R.id.trustSummary)).setText(TrustSummary.describe(currentRelease,currentDecision,verified)+(verificationIssue.isEmpty()?"":"\n\n"+verificationIssue));
        if(currentRelease!=null) proof.setText((verified?"Nachweise der lokal verifizierten APK":"Nachweise im Release-Vertrag (vor Download noch nicht lokal verifiziert)")
                +"\n\nPaket: "+currentRelease.packageName+"\nSHA-256  "+currentRelease.sha256+"\nSIGNER   "+String.join("\n",currentRelease.signers)
                +"\nSDK      "+currentRelease.minSdk+" → "+(currentRelease.targetSdk==0?"—":currentRelease.targetSdk));
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
        pendingLink=null;
        resetCandidate(); setBusy(false,""); card.setVisibility(View.VISIBLE); badge.setText(getString(R.string.message_mainactivity_11));
        radar.setText(getString(R.string.message_mainactivity_12)); title.setText(getString(R.string.message_mainactivity_13)); meta.setText(getString(R.string.message_mainactivity_12)); proof.setText(getString(R.string.message_mainactivity_12)); permissions.setText(getString(R.string.message_mainactivity_12)); notes.setVisibility(View.GONE);findViewById(R.id.historyText).setVisibility(View.GONE); status.setText(text);
    }

    private void updateSaveButton() {
        if(store!=null) store.changed();
        if(library==null || addButton==null) return;
        boolean saved=currentRelease!=null && library.find(currentRelease.slug)!=null;
        addButton.setVisibility(currentRelease==null?View.GONE:View.VISIBLE);
        boolean monitored=saved&&UpdateScheduler.enabled(this)&&UpdateScheduler.scheduled(this);
        addButton.setText(saved?(monitored?"App übernommen ✓":"Update-Überwachung aktivieren ↻"):"Übernehmen & Updates überwachen +");
        addButton.setEnabled(currentRelease!=null&&!monitored&&!detailBusy&&!library.checking()&&library.writable());
        TextView tracking=findViewById(R.id.monitoringStatus);
        tracking.setVisibility(currentRelease==null?View.GONE:View.VISIBLE);
        tracking.setText(!saved?"Ein Tipp speichert diese App dauerhaft in Meine Apps und aktiviert automatische Update-Prüfungen."
                :monitored?"Update-Überwachung aktiv · etwa alle 6 Stunden, wenn Android Netzwerk und Akku freigibt."
                :UpdateScheduler.enabled(this)?"App gespeichert. Automatische Prüfung noch nicht geplant. Tippe oben, um es erneut zu versuchen."
                :"App gespeichert. Automatische Prüfungen sind ausgeschaltet. Mit einem Tipp wieder aktivieren.");
        if(deferredLink!=null&&!detailBusy&&!library.checking()&&!awaitingInstaller){
            final String next=deferredLink;
            main.post(()->{
                if(!isDestroyed()&&!isFinishing()&&next.equals(deferredLink)&&!detailBusy&&!library.checking()&&!awaitingInstaller){
                    deferredLink=null;importLink(next,true);
                }
            });
        }
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
                        setBusy(false,""); actionButton.setEnabled(true);updateTrust(true);
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
        queueStatus.setText("Update-Runde · "+queue.size()+" verbleibend\n"+(awaitingInstaller?"Installation ausstehend · Android bestätigen oder erneut versuchen.":detailBusy?"Prüfung läuft …":"Bereit · Jede Installation bestätigst du in Android."));
        findViewById(R.id.queueSkip).setEnabled(!detailBusy);
        findViewById(R.id.queueCancel).setEnabled(!detailBusy);
    }
    private void updateNotes() {
        // Show the same developer-facing summary as the public APKDrop website.
        // Raw GitHub notes may contain internal QA details and are never displayed.
        String summary=currentRelease==null?"":currentRelease.notesSummary.trim();
        boolean present=!summary.isEmpty();
        findViewById(R.id.notesPanel).setVisibility(present?View.VISIBLE:View.GONE);
        notes.setVisibility(present?View.VISIBLE:View.GONE);
        findViewById(R.id.notesButton).setVisibility(View.GONE);
        if(present) notes.setText(summary);
    }
    private void updateDropPilotStatus() {
        ((TextView)findViewById(R.id.dropPilotStatus)).setText(DropPilot.dashboard(this));
        ((TextView)findViewById(R.id.homePilotStatus)).setText("DropPilot · "+DropPilot.headline(this));
        findViewById(R.id.pilotOpen).setVisibility(DropPilot.preparedSlug(this)!=null?View.VISIBLE:View.GONE);
    }
    private void updateBackgroundStatus() {
        if(backgroundStatus==null) return;
        String text=UpdateScheduler.enabled(this)
                ?(UpdateScheduler.scheduled(this)?"Update-Überwachung eingeplant · etwa alle 6 Stunden, wenn Android Netzwerk und Akku freigibt.":"Update-Überwachung noch nicht eingeplant. Erneut aktivieren oder APKDrop neu öffnen.")
                +" Nur gespeicherte Apps; keine APK-Downloads."
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
        return UpdateFailure.message(e);
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

