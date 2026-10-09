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
    private final java.util.LinkedHashMap<String,JSONObject> proposals=new java.util.LinkedHashMap<>();
    private final Runnable changed;
    private String message="";
    private boolean busy,closed,resumed;
    private int request,nextOffset=-1;
    private long checkedAt;
    private final Runnable poll=()->resume();
    GitHubRadarController(Activity activity,Runnable changed){
        this.activity=activity;this.changed=changed;storage=new RadarConnection(activity);panel=activity.findViewById(R.id.githubRadarPanel);
        try{connection=storage.read();}catch(Exception bad){message="Die lokale Verbindung ist nicht lesbar. Bitte neu verbinden.";}
        render();
    }
    boolean connected(){return connection.optBoolean("connected")&&connection.optLong("expires")>System.currentTimeMillis()/1000;}
    String summary(){return !connected()?"GitHub Radar · Eigene APKs entdecken →":busy?"GitHub Radar · Prüft deine Repositories …":proposals.isEmpty()?"GitHub Radar · @"+connection.optString("login")+" →":"GitHub Radar · "+proposals.size()+" APK-Vorschläge →";}
    void resume(){
        resumed=true;main.removeCallbacks(poll);
        if(busy||closed)return;
        if(connection.has("id")&&!connection.optBoolean("connected")){
            if(connection.optLong("pairExpires")<=System.currentTimeMillis()/1000){storage.clear();connection=new JSONObject();message="Verbindung abgelaufen. Bitte erneut starten.";render();return;}
            status();
        }else if(connected()&&System.currentTimeMillis()-checkedAt>300000)refresh(0);
    }
    void pause(){resumed=false;main.removeCallbacks(poll);}
    void close(){closed=true;request++;main.removeCallbacks(poll);io.shutdownNow();}
    void show(){render();resume();}
    private void start(){
        if(busy)return;final int ticket=++request;busy=true;message="Verbindung wird vorbereitet …";render();
        io.execute(()->{try{
            String pollSecret=RadarConnection.secret(),deviceSecret=RadarConnection.secret();
            JSONObject data=RadarClient.request("/api/companion/pair",new JSONObject().put("pollSecret",pollSecret).put("deviceSecret",deviceSecret),null);
            String id=data.getString("id");RadarClient.verificationPath(id);
            JSONObject next=new JSONObject().put("id",id).put("pollSecret",pollSecret).put("deviceSecret",deviceSecret).put("pairExpires",data.getLong("expires"));
            post(ticket,()->{try{storage.save(next);connection=next;busy=false;message="Vergleichscode: "+id.substring(0,8).toUpperCase(java.util.Locale.ROOT)+" · Im Browser bestätigen, dann zurückkehren.";render();open(RadarClient.verificationPath(id));}catch(Exception e){failure(e);}});
        }catch(Exception e){post(ticket,()->failure(e));}});
    }
    private void status(){
        final int ticket=++request;busy=true;render();JSONObject saved=connection;
        io.execute(()->{try{
            JSONObject data=RadarClient.request("/api/companion/pair/status",new JSONObject().put("id",saved.getString("id")).put("pollSecret",saved.getString("pollSecret")),null);
            post(ticket,()->{busy=false;try{
                if(data.optBoolean("connected")){
                    JSONObject next=new JSONObject().put("deviceSecret",saved.getString("deviceSecret")).put("connected",true).put("expires",data.getLong("expires")).put("login",data.getString("login"));
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
                message=data.optBoolean("needsGitHub")?"GitHub bitte im Dashboard neu verbinden.":data.optBoolean("needsAccess")?"Gib der APKDrop GitHub App Zugriff auf deine Repositories.":proposals.isEmpty()?"Noch keine neue Release-APK gefunden.":proposals.size()+" APK-Vorschläge gefunden.";
                if(data.optInt("unavailable")>0)message+=" Einige Repositories waren nicht erreichbar.";render();});
        }catch(Exception e){post(ticket,()->{if(e instanceof RadarClient.Unauthorized){storage.clear();connection=new JSONObject();result=null;proposals.clear();}failure(e);});}});
    }
    private void disconnect(){
        new AlertDialog.Builder(activity).setTitle("GitHub Radar trennen?").setMessage("Der Zugriff dieses Geräts auf deine privaten APK-Vorschläge wird widerrufen.")
                .setNegativeButton("Abbrechen",null).setPositiveButton("Trennen",(d,w)->{
                    if(busy)return;final int ticket=++request;busy=true;render();String token=connection.optString("deviceSecret");
                    io.execute(()->{try{RadarClient.request("/api/companion/disconnect",new JSONObject(),token);post(ticket,this::clear);}catch(Exception e){post(ticket,()->{if(e instanceof RadarClient.Unauthorized)clear();else failure(e);});}});
                }).show();
    }
    private void clear(){storage.clear();connection=new JSONObject();result=null;proposals.clear();nextOffset=-1;checkedAt=0;busy=false;message="Verbindung getrennt.";render();}
    private void failure(Exception e){busy=false;message=e.getMessage()==null?"Radar nicht erreichbar. Bitte erneut versuchen.":e.getMessage();render();}
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
                row.addView(label(item.optString("description"),14));row.addView(label("v"+item.optString("version")+" · "+(item.optBoolean("prerelease")?"Beta":"Stable")+"\n"+item.optString("filename"),12));
                row.addView(button("App hinzufügen →",()->open(RadarClient.importPath(item.optString("fullName")))));
            }
            if(nextOffset>=0&&proposals.size()<120)panel.addView(button("Weitere Repositories prüfen →",()->refresh(nextOffset)));
            panel.addView(button("GitHub-Zugriff verwalten ↗",()->open("/dashboard")));panel.addView(button("Verbindung trennen",this::disconnect));
        }
        panel.addView(label("Unter GitHub „All repositories“ wählen, um neue Repos automatisch freizugeben. Bei einer Auswahl neue Repos einzeln autorisieren. Erkannt werden APKs in GitHub Releases. Den Import bestätigst du im Browser; Veröffentlichung bleibt separat.",12));
        changed.run();
    }
    private TextView label(String value,int size){TextView t=new TextView(activity);t.setText(value);t.setTextSize(size);t.setTextColor(activity.getColor(size>=20?R.color.text:R.color.muted));t.setPadding(0,dp(5),0,dp(5));return t;}
    private Button button(String text,Runnable action){Button b=new Button(activity);b.setText(text);b.setAllCaps(false);b.setMinHeight(dp(48));b.setTextColor(activity.getColor(R.color.lime));b.setBackgroundResource(R.drawable.bg_compact);b.setEnabled(!busy);b.setOnClickListener(v->action.run());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(8);b.setLayoutParams(lp);return b;}
    private int dp(int n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
    private void open(String path){try{activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(StoreClient.ORIGIN+path)));}catch(Exception e){message="Browser nicht verfügbar. Bitte erneut versuchen.";render();}}
    private void post(int ticket,Runnable callback){main.post(()->{if(!closed&&ticket==request&&!activity.isDestroyed())callback.run();});}
}
