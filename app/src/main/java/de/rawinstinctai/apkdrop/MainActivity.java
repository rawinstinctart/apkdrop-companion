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
    private TextView badge,title,meta,proof,permissions,status,notes;
    private ProgressBar progress;
    private AppLibraryController library;
    private int generation;
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
        input=findViewById(R.id.urlInput); checkButton=findViewById(R.id.checkButton); actionButton=findViewById(R.id.actionButton);
        addButton=findViewById(R.id.addButton); notes=findViewById(R.id.notesText);
        card=findViewById(R.id.releaseCard); badge=findViewById(R.id.statusBadge);
        title=findViewById(R.id.titleText); meta=findViewById(R.id.metaText); proof=findViewById(R.id.proofText);
        permissions=findViewById(R.id.permissionsText); status=findViewById(R.id.statusText); progress=findViewById(R.id.progress);
        library=new AppLibraryController(this,io,this::load,this::updateSaveButton);

        checkButton.setOnClickListener(v->{
            try { load(SlugParser.parse(input.getText().toString())); }
            catch(Exception e) { toast(message(e)); }
        });
        actionButton.setOnClickListener(v->onAction());
        addButton.setOnClickListener(v->{
            if(currentRelease==null || detailBusy || library.checking()) return;
            try { library.add(currentRelease,currentInstalled,currentDecision); toast("In Meine Apps gespeichert."); }
            catch(Exception e) { toast(message(e)); }
        });
        if(getIntent()!=null && getIntent().getData()!=null) handleIntent(getIntent());
        else {
            String slug=state==null?null:state.getString("activeSlug");
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
        super.onSaveInstanceState(state);
    }

    @Override protected void onDestroy() {
        generation++; library.close(); io.shutdownNow(); super.onDestroy();
    }

    private void handleIntent(Intent intent) {
        if(intent==null || intent.getData()==null) return;
        String raw=intent.getDataString();
        try { String slug=SlugParser.parse(raw); input.setText(raw); load(slug); }
        catch(Exception e) { toast(message(e)); }
    }

    private void load(String slug) {
        final int ticket=++generation;
        final AppLibrary.Entry saved=library.find(slug);
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
                    library.clearPendingInstaller();
                });
            } catch(Exception e) { post(ticket,()->showError(message(e))); }
        });
    }

    private void show(InstallContract release,InstalledState installed,InstallPolicy.Result decision,boolean keepVerified,boolean fetched) {
        File prior=keepVerified?verifiedApk:null;
        currentRelease=release; currentInstalled=installed; currentDecision=decision; verifiedApk=null;
        title.setText(release.appName);
        meta.setText((installed==null?"Nicht installiert":"Installiert: "+AppLibraryController.installedVersion(installed))
                +"\nVerfügbar: v"+release.version+" · "+formatSize(release.size)+" · "+release.channel+"\n"+release.packageName);
        String signer=release.signers.iterator().next();
        proof.setText("SHA-256  "+release.sha256.substring(0,16)+"…"
                +"\nSIGNER   "+signer.substring(0,16)+"…"
                +"\nSDK      "+release.minSdk+" → "+(release.targetSdk==0?"—":release.targetSdk));

        StringBuilder p=new StringBuilder();
        if(installed==null) p.append("Deklarierte Berechtigungen: ").append(release.permissions.size());
        else if(decision.addedPermissions.isEmpty()) p.append("✓ Keine neuen Berechtigungen gegenüber der installierten Version.");
        else {
            p.append("Neue Berechtigungen (+").append(decision.addedPermissions.size()).append("):\n");
            for(String permission:decision.addedPermissions) p.append("• ").append(human(permission)).append("\n");
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
        notes.setText("Was ändert sich?\n"+release.notes);

        boolean actionable=false;
        switch(decision.mode) {
            case INSTALL -> { badge.setText("NEUE APP"); actionable=true; }
            case UPDATE -> { badge.setText("UPDATE"); actionable=true; }
            case CURRENT -> badge.setText("AKTUELL");
            case BLOCKED -> badge.setText("BLOCKIERT");
        }
        actionButton.setVisibility(actionable?View.VISIBLE:View.GONE);
        actionButton.setEnabled(actionable);
        actionButton.setText("APK herunterladen & lokal prüfen →");
        if(actionable && prior!=null && prior.isFile()) {
            verifiedApk=prior; actionButton.setText("Android-Installation öffnen →");
            status.setText("APK lokal geprüft. Tippe auf Android-Installation öffnen, um fortzufahren.");
        }
        if(fetched) library.rememberChecked(release,installed,decision);
        setBusy(false,"");
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
        status.setText("APK wird in privaten App-Speicher geladen …");

        io.execute(()->{
            try {
                File file=ApkDownloader.download(this,release,pct->post(ticket,()->progress.setProgress(pct)));
                post(ticket,()->status.setText("Prüfe SHA-256, APK-Signatur und Android-Identität lokal …"));
                InstalledState fresh=InstalledState.read(this,release.packageName);
                InstallPolicy.Result freshDecision=InstallPolicy.evaluate(release,fresh,Build.VERSION.SDK_INT,Build.SUPPORTED_ABIS);
                if(freshDecision.mode==InstallPolicy.Mode.BLOCKED || freshDecision.mode==InstallPolicy.Mode.CURRENT)
                    throw new SecurityException(freshDecision.reason);
                ApkVerifierUtil.verify(this,file,release,fresh);
                post(ticket,()->verified(file));
            } catch(Exception e) { post(ticket,()->downloadError(message(e))); }
        });
    }

    private void verified(File file) {
        verifiedApk=file;
        setBusy(false,""); actionButton.setEnabled(true);
        actionButton.setText("Android-Installation öffnen →");
        status.setText("✓ Lokal verifiziert: Hash, APK-Signatur, Package, Version, SDK und Berechtigungen stimmen.");
    }

    private void downloadError(String text) {
        verifiedApk=null;
        setBusy(false,""); actionButton.setEnabled(true);
        actionButton.setText("Erneut prüfen →"); status.setText(text);
    }

    private void resetCandidate() {
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
        updateSaveButton();
        if(!busy && refreshOnIdle) { refreshOnIdle=false; refreshCurrent(); }
    }

    private void showError(String text) {
        resetCandidate(); setBusy(false,""); card.setVisibility(View.VISIBLE); badge.setText("STOP");
        title.setText("Nicht verfügbar"); meta.setText(""); proof.setText(""); permissions.setText(""); notes.setVisibility(View.GONE); status.setText(text);
    }

    private void updateSaveButton() {
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
                        library.clearPendingInstaller();
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
                        setBusy(false,""); actionButton.setEnabled(true);
                        status.setText(launched?"Android übernimmt jetzt die Installation. Der Status wird bei deiner Rückkehr aktualisiert."
                                :"Erlaube APKDrop einmal als Installationsquelle und kehre danach zurück.");
                    } catch(Exception e) { library.clearPendingInstaller(); downloadError(message(e)); }
                });
            } catch(Exception e) { post(ticket,()->downloadError(message(e))); }
        });
    }

    private void post(int ticket,Runnable callback) {
        runOnUiThread(()->{ if(ticket==generation&&!isFinishing()&&!isDestroyed()) callback.run(); });
    }

    private static String human(String permission) {
        if("android.permission.RECORD_AUDIO".equals(permission)) return "Mikrofonzugriff (Audio aufnehmen)";
        int i=permission.lastIndexOf('.'); return (i>=0?permission.substring(i+1):permission).replace('_',' ');
    }
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
    private void toast(String text) { Toast.makeText(this,text,Toast.LENGTH_LONG).show(); }
}
