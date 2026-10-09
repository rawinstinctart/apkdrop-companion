package de.rawinstinctai.apkdrop;

import android.app.*;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.util.concurrent.*;

/** Private owner radar. Optional for readers; approved GitHub access stays on APKDrop. */
final class GitHubRadarController {
    private final Activity activity;
    private final RadarConnection storage;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(android.os.Looper.getMainLooper());
    private final LinearLayout panel;
    private JSONObject connection=new JSONObject(),result;
    private JSONArray ownApps=new JSONArray();
    private final java.util.function.Consumer<String> select;
    private long appsAt;
    private final java.util.LinkedHashMap<String,JSONObject> proposals=new java.util.LinkedHashMap<>();
    private final Runnable changed;
    private String message="";
    private boolean busy,closed,resumed;
    private int request,nextOffset=-1;
    private long checkedAt;
    private final Runnable poll=()->resume();
    GitHubRadarController(Activity activity,Runnable changed,java.util.function.Consumer<String> select){
        this.select=select;
        this.activity=activity;this.changed=changed;storage=new RadarConnection(activity);panel=activity.findViewById(R.id.githubRadarPanel);
        try{connection=storage.read();}catch(Exception bad){message="Die lokale Verbindung ist nicht lesbar. Bitte neu verbinden.";}
        render();
    }
    boolean connected(){return connection.optBoolean("connected")&&connection.optLong("expires")>System.currentTimeMillis()/1000;}
    int proposalCount(){return proposals.size();}
    boolean hasChecked(){return checkedAt>0;}
    void homeActions(LinearLayout home){
        if(!connected()){home.addView(button("GitHub verbinden · eigene APKs entdecken →",this::start));return;}
        int shown=0;for(JSONObject item:proposals.values()){if(shown++==3)break;home.addView(button(item.optString("name")+" · APK gefunden →",()->preview(item)));}
        home.addView(button("GitHub Radar · "+(checkedAt>0?DisplayText.proposals(proposals.size()):"Prüfung ausstehend")+" →",()->{activity.findViewById(R.id.homeGitHubRadar).performClick();}));
    }
    String summary(){return !connected()?"GitHub Radar · Eigene APKs entdecken →":busy?"GitHub Radar · Prüft deine Repositories …":proposals.isEmpty()?"GitHub Radar · @"+connection.optString("login")+" →":"GitHub Radar · "+DisplayText.proposals(proposals.size())+" →";}
    void resume(){
        resumed=true;main.removeCallbacks(poll);
        if(busy||closed)return;
        if(connection.has("id")&&!connection.optBoolean("connected")){
            if(connection.optLong("pairExpires")<=System.currentTimeMillis()/1000){storage.clear();connection=new JSONObject();message="Verbindung abgelaufen. Bitte erneut starten.";render();return;}
            status();
        }else if(connected()){if(System.currentTimeMillis()-checkedAt>300000)refresh(0);else if(System.currentTimeMillis()-appsAt>30000)loadApps();}
    }
    void pause(){resumed=false;main.removeCallbacks(poll);}
    void close(){closed=true;request++;main.removeCallbacks(poll);io.shutdownNow();}
    void show(){render();resume();}
    private void start(){
        if(busy)return;final int ticket=++request;busy=true;message="Verbindung wird vorbereitet …";render();
        io.execute(()->{try{
            String pollSecret=RadarConnection.secret(),deviceSecret=RadarConnection.secret();
            JSONObject data=RadarClient.request("/api/companion/pair",new JSONObject().put("pollSecret",pollSecret).put("deviceSecret",deviceSecret).put("privateImport",true),null);
            String id=data.getString("id");RadarClient.verificationPath(id);
            JSONObject next=new JSONObject().put("id",id).put("pollSecret",pollSecret).put("deviceSecret",deviceSecret).put("pairExpires",data.getLong("expires"));
            post(ticket,()->{try{storage.save(next);connection=next;proposals.clear();ownApps=new JSONArray();checkedAt=0;appsAt=0;busy=false;message="Vergleichscode: "+id.substring(0,8).toUpperCase(java.util.Locale.ROOT)+" · Im Browser bestätigen, dann zurückkehren.";render();open(RadarClient.verificationPath(id));}catch(Exception e){failure(e);}});
        }catch(Exception e){post(ticket,()->failure(e));}});
    }
    private void status(){
        final int ticket=++request;busy=true;render();JSONObject saved=connection;
        io.execute(()->{try{
            JSONObject data=RadarClient.request("/api/companion/pair/status",new JSONObject().put("id",saved.getString("id")).put("pollSecret",saved.getString("pollSecret")),null);
            post(ticket,()->{busy=false;try{
                if(data.optBoolean("connected")){
                    JSONObject next=new JSONObject().put("deviceSecret",saved.getString("deviceSecret")).put("connected",true).put("expires",data.getLong("expires")).put("login",data.getString("login")).put("privateImport",data.optBoolean("privateImport"));
                    storage.save(next);connection=next;message="GitHub verbunden ✓";render();refresh(0);
                }else{message="Vergleichscode: "+saved.getString("id").substring(0,8).toUpperCase(java.util.Locale.ROOT)+" · Im Browser bestätigen.";render();if(resumed)main.postDelayed(poll,3000);}
            }catch(Exception e){failure(e);}});
        }catch(Exception e){post(ticket,()->failure(e));}});
    }
    private void refresh(int offset){
        if(busy||!connected())return;final int ticket=++request;busy=true;message="Autorisierte Repositories werden geprüft …";render();String token=connection.optString("deviceSecret");
        io.execute(()->{try{
            JSONObject data=RadarClient.request("/api/companion/radar?offset="+offset,null,token);JSONArray list=data.getJSONArray("suggestions");
            if(list.length()>12)throw new SecurityException("Radar-Liste zu groß.");
            for(int i=0;i<list.length();i++)RadarClient.importPath(list.getJSONObject(i).getString("fullName"));
            post(ticket,()->{busy=false;checkedAt=System.currentTimeMillis();result=data;if(offset==0)proposals.clear();
                for(int i=0;i<list.length();i++){JSONObject item=list.optJSONObject(i);if(item!=null&&proposals.size()<120)proposals.put(item.optString("fullName"),item);}
                nextOffset=data.isNull("nextOffset")?-1:data.optInt("nextOffset",-1);if(nextOffset<0||nextOffset>9999)nextOffset=-1;
                message=data.optBoolean("needsGitHub")?"GitHub bitte im Dashboard neu verbinden.":data.optBoolean("needsAccess")?"Gib der APKDrop GitHub App Zugriff auf deine Repositories.":proposals.isEmpty()?"Noch keine neue Release-APK gefunden.":DisplayText.proposals(proposals.size())+" gefunden.";
                if(data.optInt("unavailable")>0)message+=" Einige Repositories waren nicht erreichbar.";if(data.optBoolean("repositoriesTruncated"))message+=" Es konnten nicht alle Repositories geprüft werden.";render();loadApps();});
        }catch(Exception e){post(ticket,()->{if(e instanceof RadarClient.Unauthorized){storage.clear();connection=new JSONObject();result=null;proposals.clear();}failure(e);});}});
    }
    private void disconnect(){
        new AlertDialog.Builder(activity).setTitle("GitHub Radar trennen?").setMessage("Der Zugriff dieses Geräts auf deine privaten APK-Vorschläge wird widerrufen.")
                .setNegativeButton("Abbrechen",null).setPositiveButton("Trennen",(d,w)->{
                    if(busy)return;final int ticket=++request;busy=true;render();String token=connection.optString("deviceSecret");
                    io.execute(()->{try{RadarClient.request("/api/companion/disconnect",new JSONObject(),token);post(ticket,this::clear);}catch(Exception e){post(ticket,()->{if(e instanceof RadarClient.Unauthorized)clear();else failure(e);});}});
                }).show();
    }
    private void clear(){storage.clear();connection=new JSONObject();result=null;proposals.clear();ownApps=new JSONArray();appsAt=0;nextOffset=-1;checkedAt=0;busy=false;message="Verbindung getrennt.";render();}
    private void failure(Exception e){busy=false;if(e instanceof RadarClient.Unauthorized){storage.clear();connection=new JSONObject();proposals.clear();ownApps=new JSONArray();}message=e.getMessage()==null?"Radar nicht erreichbar. Bitte erneut versuchen.":e.getMessage();render();}
    private void render(){
        panel.removeAllViews();panel.addView(label("GitHub Radar",22));panel.addView(label("Neue APKs aus deinen freigegebenen Repositories. Auch aus Repos, die du später anlegst.",14));
        if(!message.isEmpty())panel.addView(label(message,13));
        if(!connected()){
            if(connection.has("id")){panel.addView(button("Bestätigung im Browser öffnen →",()->open(RadarClient.verificationPath(connection.optString("id")))));panel.addView(button("Verbindung prüfen ↻",this::status));}
            panel.addView(button(connection.has("id")?"Verbindung neu starten":"GitHub verbinden →",this::start));
            panel.addView(label("Einmal im Browser anmelden und dieses Gerät bestätigen. Deine GitHub-Zugangsdaten bleiben dort.",12));
        }else{
            panel.addView(label("Verbunden mit @"+connection.optString("login"),14));panel.addView(button("Repositories jetzt prüfen ↻",()->refresh(0)));
            if(checkedAt>0)panel.addView(label("Zuletzt geprüft: "+java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT).format(new java.util.Date(checkedAt)),12));
            for(JSONObject item:proposals.values()){
                LinearLayout row=new LinearLayout(activity);row.setOrientation(LinearLayout.VERTICAL);row.setBackgroundResource(R.drawable.bg_card);row.setPadding(dp(16),dp(16),dp(16),dp(16));
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(12);panel.addView(row,lp);
                row.addView(label(item.optString("name"),20));row.addView(label(item.optString("fullName")+(item.optBoolean("private")?" · Privat":""),12));
                row.addView(label(item.optString("description"),14));row.addView(label(DisplayText.version(item.optString("version"))+" · "+(item.optBoolean("prerelease")?"Beta":"Stable")+"\n"+item.optString("filename"),12));
                row.addView(button("App hinzufügen →",()->preview(item)));
            }
            if(nextOffset>=0&&proposals.size()<120)panel.addView(button("Weitere Repositories prüfen →",()->refresh(nextOffset)));
            renderOwnApps();
            panel.addView(button("GitHub-Zugriff verwalten ↗",()->open("/dashboard")));panel.addView(button("Verbindung trennen",this::disconnect));
        }
        panel.addView(label("Unter GitHub „All repositories“ wählen, um neue Repos automatisch freizugeben. Bei einer Auswahl neue Repos einzeln autorisieren. Erkannt werden APKs in GitHub Releases. Privaten Import bestätigst du hier in der App. Öffentliche Veröffentlichung bleibt im Dashboard.",12));
        changed.run();
    }
    private void loadApps(){
        if(busy||!connected())return;final int ticket=++request;busy=true;String token=connection.optString("deviceSecret");
        io.execute(()->{try{JSONObject data=RadarClient.request("/api/companion/apps",null,token);JSONArray apps=data.getJSONArray("apps");if(apps.length()>20)throw new SecurityException("App-Liste zu groß.");
            post(ticket,()->{busy=false;ownApps=apps;appsAt=System.currentTimeMillis();render();if(resumed){boolean pending=false;for(int i=0;i<apps.length();i++){JSONObject app=apps.optJSONObject(i);if(app!=null&&java.util.Set.of("syncing","pending").contains(app.optJSONObject("progress")==null?"":app.optJSONObject("progress").optString("state")))pending=true;}if(pending)main.postDelayed(poll,30000);}});
        }catch(Exception e){post(ticket,()->{busy=false;if(e instanceof RadarClient.Unauthorized){clear();return;}message="Deine importierten Apps konnten nicht aktualisiert werden. Erneut prüfen.";render();});}});
    }
    private void renderOwnApps(){
        if(ownApps.length()==0)return;panel.addView(label("Deine GitHub-Apps",20));
        for(int i=0;i<ownApps.length();i++){JSONObject app=ownApps.optJSONObject(i);if(app==null)continue;LinearLayout row=new LinearLayout(activity);row.setOrientation(LinearLayout.VERTICAL);row.setPadding(dp(14),dp(10),dp(14),dp(10));row.setBackgroundResource(R.drawable.bg_card);panel.addView(row);
            LinearLayout header=new LinearLayout(activity);header.setGravity(android.view.Gravity.CENTER_VERTICAL);ImageView icon=new ImageView(activity);icon.setImageDrawable(new AppPlaceholder(app.optString("name")));header.addView(icon,new LinearLayout.LayoutParams(dp(44),dp(44)));
            String image=app.optString("iconBase64","");if(image.length()>0&&image.length()<=90000)try{byte[] bytes=android.util.Base64.decode(image,android.util.Base64.DEFAULT);android.graphics.BitmapFactory.Options bounds=new android.graphics.BitmapFactory.Options();bounds.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);if(bounds.outWidth>0&&bounds.outWidth<=1024&&bounds.outHeight>0&&bounds.outHeight<=1024){android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.length);if(bitmap!=null)icon.setImageBitmap(bitmap);}}catch(Exception ignored){}
            TextView title=label(app.optString("name"),18);title.setPadding(dp(12),0,0,0);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));row.addView(header);row.addView(label(app.optString("description"),13));JSONObject progress=app.optJSONObject("progress");row.addView(label((app.optBoolean("published")?"Veröffentlicht":"Privat hinzugefügt")+" · "+DisplayText.version(app.optString("version",""))+"\n"+(progress==null?"":progress.optString("message")),12));row.addView(label("Quelle: "+app.optString("repo"),11));
            if(app.optBoolean("published"))row.addView(button("App ansehen & überwachen →",()->{try{select.accept(StoreClient.slug(app.getString("slug")));}catch(Exception e){failure(e);}}));
            else row.addView(button("Privaten Entwurf im Dashboard öffnen ↗",()->open("/onboarding")));
        }
    }
    private void preview(JSONObject suggestion){
        if(busy||!connected())return;final int ticket=++request;busy=true;message="APK-Vorschau wird frisch geladen …";render();String token=connection.optString("deviceSecret");
        io.execute(()->{try{JSONObject data=RadarClient.request("/api/companion/import/preview",new JSONObject().put("repo",suggestion.getString("fullName")).put("includeBeta",suggestion.optBoolean("prerelease")),token);
            post(ticket,()->{busy=false;render();try{JSONObject selected=data.getJSONObject("selection"),repo=data.getJSONObject("repository");if(!"ready".equals(data.optString("state")))throw new SecurityException("Keine passende APK verfügbar. Bitte Radar erneut prüfen.");
                String text=repo.optString("description")+"\n\n"+DisplayText.version(selected.optString("version"))+" · "+selected.optString("filename")+"\nEntwickler: @"+suggestion.getString("fullName").split("/")[0]+"\nQuelle: github.com/"+suggestion.getString("fullName")+"\n\nDie APK wird privat übernommen und geprüft. Das echte App-Icon wird aus der APK gelesen. Veröffentlichung und Installation bleiben eigene Entscheidungen.";
                if(!connection.optBoolean("privateImport")){new AlertDialog.Builder(activity).setTitle("Privaten Import freigeben").setMessage("Deine ältere Verbindung erlaubt nur das Lesen. Bestätige dieses Gerät einmal im Browser für private Imports.").setNegativeButton("Abbrechen",null).setPositiveButton("Neu verbinden",(d,w)->start()).show();return;}
                new AlertDialog.Builder(activity).setTitle(repo.optString("name","APK gefunden")).setMessage(text).setNegativeButton("Abbrechen",null).setPositiveButton("Privat hinzufügen",(d,w)->importApp(suggestion,selected)).show();
            }catch(Exception e){failure(e);}});
        }catch(Exception e){post(ticket,()->failure(e));}});
    }
    private void importApp(JSONObject suggestion,JSONObject selected){
        if(busy)return;final int ticket=++request;busy=true;message="Privater Import wird gestartet …";render();String token=connection.optString("deviceSecret");
        io.execute(()->{try{RadarClient.request("/api/companion/import",new JSONObject().put("repo",suggestion.getString("fullName")).put("version",selected.getString("version")).put("filename",selected.getString("filename")).put("includeBeta",selected.optBoolean("prerelease")).put("importConsent",true),token);
            post(ticket,()->{busy=false;proposals.remove(suggestion.optString("fullName"));message="Privat hinzugefügt ✓ · APK und App-Icon werden geprüft.";render();loadApps();});
        }catch(Exception e){post(ticket,()->failure(e));}});
    }
    private TextView label(String value,int size){TextView t=new TextView(activity);t.setText(value);t.setTextSize(size);t.setTextColor(activity.getColor(size>=20?R.color.text:R.color.muted));t.setPadding(0,dp(5),0,dp(5));return t;}
    private Button button(String text,Runnable action){Button b=new Button(activity);b.setText(text);b.setAllCaps(false);b.setMinHeight(dp(48));b.setTextColor(activity.getColor(R.color.lime));b.setBackgroundResource(R.drawable.bg_compact);b.setEnabled(!busy);b.setOnClickListener(v->action.run());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(8);b.setLayoutParams(lp);return b;}
    private int dp(int n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
    private void open(String path){try{activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(StoreClient.ORIGIN+path)));}catch(Exception e){message="Browser nicht verfügbar. Bitte erneut versuchen.";render();}}
    private void post(int ticket,Runnable callback){main.post(()->{if(!closed&&ticket==request&&!activity.isDestroyed())callback.run();});}
}
