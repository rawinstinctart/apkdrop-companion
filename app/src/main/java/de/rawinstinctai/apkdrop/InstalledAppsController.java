package de.rawinstinctai.apkdrop;

import android.app.*;
import android.os.*;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** User-started catalog-to-device matching. No installed inventory is enumerated or uploaded. */
final class InstalledAppsController {
    private final Activity activity;
    private final AppLibraryController library;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final LinearLayout panel;
    private boolean closed,busy;
    private int request;
    InstalledAppsController(Activity a,AppLibraryController l){activity=a;library=l;panel=a.findViewById(R.id.installedSuggestions);a.findViewById(R.id.findInstalledApps).setOnClickListener(v->consent());}
    void close(){closed=true;request++;io.shutdownNow();}
    private void consent(){if(busy)return;new AlertDialog.Builder(activity).setTitle("Installierte Apps erkennen?").setMessage("APKDrop lädt den öffentlichen App-Katalog und prüft lokal, welche unterstützten Apps installiert sind. Paket und Signatur werden verglichen. Deine installierten Apps werden nicht hochgeladen. Hinzufügen bestätigst du anschließend selbst.").setNegativeButton("Abbrechen",null).setPositiveButton("Lokal abgleichen",(d,w)->scan()).show();}
    private void scan(){final int ticket=++request;busy=true;panel.removeAllViews();panel.addView(label("Unterstützte Apps werden lokal abgeglichen …"));activity.findViewById(R.id.findInstalledApps).setEnabled(false);
        io.execute(()->{try{List<JSONObject> matches=new ArrayList<>();Set<String> seen=new HashSet<>();int pages=1;boolean limited=false;
            for(int page=1;page<=pages&&page<=50;page++){if(Thread.currentThread().isInterrupted())return;JSONObject data=StoreClient.get("/api/discover?page="+page);CatalogCache.validate(data);pages=data.optInt("pages",1);limited=pages>50;
                JSONArray apps=data.getJSONArray("apps");for(int i=0;i<apps.length();i++){JSONObject app=apps.getJSONObject(i);String pkg=app.optString("packageName");if(!pkg.matches("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+"))continue;InstalledState installed=InstalledState.read(activity,pkg);if(installed==null)continue;
                    ReleaseRadar.State state=ReleaseRadar.compare(app,installed);if((state==ReleaseRadar.State.UPDATE||state==ReleaseRadar.State.INSTALLED)&&seen.add(pkg))matches.add(app);
                }
            }
            final boolean truncated=limited;post(ticket,()->{finish();panel.removeAllViews();panel.addView(label(matches.isEmpty()?"Keine passende installierte App im öffentlichen Katalog gefunden.":matches.size()+" unterstützte installierte Apps erkannt."));if(truncated)panel.addView(label("Die ersten 1.200 Katalogeinträge wurden geprüft."));for(JSONObject app:matches)row(app);});
        }catch(Exception e){post(ticket,()->{finish();panel.removeAllViews();panel.addView(label("Abgleich fehlgeschlagen. Bitte erneut versuchen."));});}});
    }
    private void row(JSONObject app){String slug=app.optString("slug");if(library.find(slug)!=null){panel.addView(label(app.optString("name")+" · bereits in Meine Apps"));return;}
        Button add=new Button(activity);add.setAllCaps(false);add.setText(app.optString("name")+" · Updatequelle hinzufügen +");add.setTextColor(activity.getColor(R.color.lime));add.setBackgroundResource(R.drawable.bg_compact);add.setMinHeight(dp(48));panel.addView(add);add.setOnClickListener(v->{if(busy)return;busy=true;final int ticket=++request;add.setEnabled(false);add.setText("Quelle wird frisch geprüft …");io.execute(()->{try{InstallContract release=ContractClient.fetch(slug);InstalledState installed=InstalledState.read(activity,release.packageName);
            if(installed==null||!app.optString("packageName").equals(release.packageName)||!installed.signers.equals(release.signers))throw new SecurityException("App-Identität hat sich geändert.");InstallPolicy.Result decision=InstallPolicy.evaluate(release,installed,Build.VERSION.SDK_INT,Build.SUPPORTED_ABIS);if(decision.mode==InstallPolicy.Mode.BLOCKED)throw new SecurityException(decision.reason);
            post(ticket,()->{finish();try{library.add(release,installed,decision);if(library.find(slug)==null)throw new IllegalStateException();add.setText(release.appName+" · hinzugefügt ✓");}catch(Exception e){add.setText("Hinzufügen fehlgeschlagen · erneut versuchen");add.setEnabled(true);}});
        }catch(Exception e){post(ticket,()->{finish();add.setText("Quelle nicht bestätigt · erneut versuchen");add.setEnabled(true);});}});});}
    private void finish(){busy=false;activity.findViewById(R.id.findInstalledApps).setEnabled(true);}
    private TextView label(String s){TextView t=new TextView(activity);t.setText(s);t.setTextSize(13);t.setTextColor(activity.getColor(R.color.muted));t.setPadding(0,dp(8),0,dp(8));return t;}
    private int dp(int n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
    private void post(int ticket,Runnable action){main.post(()->{if(!closed&&ticket==request&&!activity.isDestroyed())action.run();});}
}
