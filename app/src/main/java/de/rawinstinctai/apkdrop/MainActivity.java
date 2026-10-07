package de.rawinstinctai.apkdrop;

import android.app.*;
import android.content.Intent;
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
    private Button checkButton,actionButton;
    private LinearLayout card;
    private TextView badge,title,meta,proof,permissions,status;
    private ProgressBar progress;

    private InstallContract currentRelease;
    private InstalledState currentInstalled;
    private InstallPolicy.Result currentDecision;
    private File verifiedApk;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_main);
        applySystemInsets();
        input=findViewById(R.id.urlInput); checkButton=findViewById(R.id.checkButton); actionButton=findViewById(R.id.actionButton);
        card=findViewById(R.id.releaseCard); badge=findViewById(R.id.statusBadge);
        title=findViewById(R.id.titleText); meta=findViewById(R.id.metaText); proof=findViewById(R.id.proofText);
        permissions=findViewById(R.id.permissionsText); status=findViewById(R.id.statusText); progress=findViewById(R.id.progress);

        checkButton.setOnClickListener(v->{
            try { load(SlugParser.parse(input.getText().toString())); }
            catch(Exception e) { toast(message(e)); }
        });
        actionButton.setOnClickListener(v->onAction());
        handleIntent(getIntent());
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

    @Override protected void onDestroy() { io.shutdownNow(); super.onDestroy(); }

    private void handleIntent(Intent intent) {
        if(intent==null || intent.getData()==null) return;
        String raw=intent.getDataString();
        try { String slug=SlugParser.parse(raw); input.setText(raw); load(slug); }
        catch(Exception e) { toast(message(e)); }
    }

    private void load(String slug) {
        resetCandidate(); setBusy(true,"Prüfe Release-Vertrag …"); card.setVisibility(View.VISIBLE);
        io.execute(()->{
            try {
                InstallContract release=ContractClient.fetch(slug);
                InstalledState installed=InstalledState.read(this,release.packageName);
                InstallPolicy.Result decision=InstallPolicy.evaluate(release,installed,Build.VERSION.SDK_INT,Build.SUPPORTED_ABIS);
                runOnUiThread(()->show(release,installed,decision));
            } catch(Exception e) { runOnUiThread(()->showError(message(e))); }
        });
    }

    private void show(InstallContract release,InstalledState installed,InstallPolicy.Result decision) {
        currentRelease=release; currentInstalled=installed; currentDecision=decision; verifiedApk=null;
        setBusy(false,"");
        title.setText(release.appName);
        meta.setText("v"+release.version+" · "+formatSize(release.size)+" · "+release.channel+"\n"+release.packageName);
        String signer=release.signers.iterator().next();
        proof.setText("SHA-256  "+release.sha256.substring(0,16)+"…"
                +"\nSIGNER   "+signer.substring(0,16)+"…"
                +"\nSDK      "+release.minSdk+" → "+(release.targetSdk==0?"—":release.targetSdk));

        StringBuilder p=new StringBuilder();
        if(installed==null) p.append("Deklarierte Berechtigungen: ").append(release.permissions.size());
        else if(decision.addedPermissions.isEmpty()) p.append("✓ Keine neuen Berechtigungen gegenüber der installierten Version.");
        else p.append("+").append(decision.addedPermissions.size()).append(" neue Berechtigung(en).");
        if(!decision.sensitiveAdded.isEmpty()) {
            p.append(installed==null ? "\n\n⚠ Sensible Berechtigungen der App:\n"
                    : "\n\n⚠ Neue sensible Berechtigungen:\n");
            for(String permission:decision.sensitiveAdded) p.append("• ").append(human(permission)).append("\n");
        }
        permissions.setText(p.toString().trim()); status.setText(decision.reason);

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
    }

    private void onAction() {
        if(currentRelease==null || currentDecision==null) return;
        if(verifiedApk!=null && verifiedApk.isFile()) {
            boolean launched=InstallerHandoff.open(this,verifiedApk);
            status.setText(launched
                    ? "Lokal geprüft. Android übernimmt jetzt die Installation."
                    : "Erlaube APKDrop einmal als Installationsquelle und kehre danach zurück.");
            return;
        }
        if(!(currentDecision.mode==InstallPolicy.Mode.INSTALL || currentDecision.mode==InstallPolicy.Mode.UPDATE)) return;

        if(!currentDecision.sensitiveAdded.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Sensible Berechtigungen erkannt")
                    .setMessage(currentRelease.appName+" deklariert folgende sensible Berechtigung(en):\n\n"+join(currentDecision.sensitiveAdded)
                            +"\n\nDie APK wird erst nach deiner Bestätigung geladen und anschließend lokal geprüft."
                            +"\n\nDiese Bestätigung erteilt der App keine Android-Berechtigung.")
                    .setNegativeButton("Abbrechen",null)
                    .setPositiveButton("Prüfung starten",(dialog,which)->downloadAndVerify())
                    .show();
            return;
        }
        downloadAndVerify();
    }

    private void downloadAndVerify() {
        final InstallContract release=currentRelease;
        final InstalledState installed=currentInstalled;
        actionButton.setEnabled(false); checkButton.setEnabled(false);
        progress.setVisibility(View.VISIBLE); progress.setIndeterminate(false); progress.setProgress(0);
        status.setText("APK wird in privaten App-Speicher geladen …");

        io.execute(()->{
            try {
                File file=ApkDownloader.download(this,release,pct->runOnUiThread(()->progress.setProgress(pct)));
                runOnUiThread(()->status.setText("Prüfe SHA-256, APK-Signatur und Android-Identität lokal …"));
                InstalledState fresh=InstalledState.read(this,release.packageName);
                InstallPolicy.Result freshDecision=InstallPolicy.evaluate(release,fresh,Build.VERSION.SDK_INT,Build.SUPPORTED_ABIS);
                if(freshDecision.mode==InstallPolicy.Mode.BLOCKED || freshDecision.mode==InstallPolicy.Mode.CURRENT)
                    throw new SecurityException(freshDecision.reason);
                ApkVerifierUtil.verify(this,file,release,fresh);
                runOnUiThread(()->verified(file));
            } catch(Exception e) { runOnUiThread(()->downloadError(message(e))); }
        });
    }

    private void verified(File file) {
        verifiedApk=file;
        progress.setVisibility(View.GONE); checkButton.setEnabled(true); actionButton.setEnabled(true);
        actionButton.setText("Android-Installation öffnen →");
        status.setText("✓ Lokal verifiziert: Hash, APK-Signatur, Package, Version, SDK und Berechtigungen stimmen.");
    }

    private void downloadError(String text) {
        verifiedApk=null;
        progress.setVisibility(View.GONE); checkButton.setEnabled(true); actionButton.setEnabled(true);
        actionButton.setText("Erneut prüfen →"); status.setText(text);
    }

    private void resetCandidate() {
        currentRelease=null; currentInstalled=null; currentDecision=null; verifiedApk=null;
        actionButton.setVisibility(View.GONE);
    }

    private void setBusy(boolean busy,String text) {
        checkButton.setEnabled(!busy);
        if(busy) { progress.setVisibility(View.VISIBLE); progress.setIndeterminate(true); status.setText(text); }
        else { progress.setIndeterminate(false); progress.setVisibility(View.GONE); }
    }

    private void showError(String text) {
        resetCandidate(); setBusy(false,""); card.setVisibility(View.VISIBLE); badge.setText("STOP");
        title.setText("Nicht verfügbar"); meta.setText(""); proof.setText(""); permissions.setText(""); status.setText(text);
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
