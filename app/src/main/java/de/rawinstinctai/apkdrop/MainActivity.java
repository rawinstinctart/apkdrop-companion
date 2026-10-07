package de.rawinstinctai.apkdrop;

import android.app.Activity;
import android.content.Intent;
import android.os.*;
import android.view.View;
import android.widget.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private EditText input;
    private Button checkButton;
    private LinearLayout card;
    private TextView badge,title,meta,proof,permissions,status;
    private ProgressBar progress;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_main);
        input=findViewById(R.id.urlInput); checkButton=findViewById(R.id.checkButton);
        card=findViewById(R.id.releaseCard); badge=findViewById(R.id.statusBadge);
        title=findViewById(R.id.titleText); meta=findViewById(R.id.metaText); proof=findViewById(R.id.proofText);
        permissions=findViewById(R.id.permissionsText); status=findViewById(R.id.statusText); progress=findViewById(R.id.progress);

        checkButton.setOnClickListener(v->{
            try { load(SlugParser.parse(input.getText().toString())); }
            catch(Exception e) { toast(message(e)); }
        });
        handleIntent(getIntent());
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
        setBusy(true,"Prüfe Release-Vertrag …"); card.setVisibility(View.VISIBLE);
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
            p.append("\n\n⚠ Neu bzw. sensibel:\n");
            for(String permission:decision.sensitiveAdded) p.append("• ").append(human(permission)).append("\n");
        }
        permissions.setText(p.toString().trim()); status.setText(decision.reason);
        switch(decision.mode) {
            case INSTALL -> badge.setText("NEUE APP");
            case UPDATE -> badge.setText("UPDATE");
            case CURRENT -> badge.setText("AKTUELL");
            case BLOCKED -> badge.setText("BLOCKIERT");
        }
    }

    private void setBusy(boolean busy,String text) {
        checkButton.setEnabled(!busy);
        if(busy) { progress.setVisibility(View.VISIBLE); progress.setIndeterminate(true); status.setText(text); }
        else { progress.setIndeterminate(false); progress.setVisibility(View.GONE); }
    }

    private void showError(String text) {
        setBusy(false,""); card.setVisibility(View.VISIBLE); badge.setText("STOP");
        title.setText("Nicht verfügbar"); meta.setText(""); proof.setText(""); permissions.setText(""); status.setText(text);
    }

    private static String human(String permission) {
        int i=permission.lastIndexOf('.'); return (i>=0?permission.substring(i+1):permission).replace('_',' ');
    }
    private static String formatSize(long bytes) {
        return String.format(java.util.Locale.GERMANY,"%.1f MiB",bytes/1048576.0);
    }
    private static String message(Exception e) {
        String value=e.getMessage(); return value==null||value.isBlank()?"Vorgang fehlgeschlagen. Bitte erneut versuchen.":value;
    }
    private void toast(String text) { Toast.makeText(this,text,Toast.LENGTH_LONG).show(); }
}
