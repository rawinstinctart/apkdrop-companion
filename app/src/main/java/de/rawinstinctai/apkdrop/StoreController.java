package de.rawinstinctai.apkdrop;

import android.app.*;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Native public catalog and DropID UI. Display metadata never bypasses the install gate. */
final class StoreController {
    private final Activity activity;
    private final AppLibraryController library;
    private final Consumer<String> select;
    private final ExecutorService network=Executors.newSingleThreadExecutor();
    private final ExecutorService media=Executors.newFixedThreadPool(2);
    private final DeveloperFollows follows;
    private final LinearLayout results;
    private final TextView resultStatus,sectionTitle;
    private final ScrollView scroll;
    private int tab,request,detailRequest;
    private boolean detail,closed,started,following;
    private String category="",query="";
    private int page=1;
    private static final int[] NAV={R.id.navHome,R.id.navDiscover,R.id.navApps,R.id.navUpdates,R.id.navSettings};
    private static final String[] TITLES={"Home","Entdecken","Meine Apps","Updates","Einstellungen"};

    StoreController(Activity activity,AppLibraryController library,Consumer<String> select) {
        this.activity=activity;this.library=library;this.select=select;follows=new DeveloperFollows(activity);
        results=activity.findViewById(R.id.discoverList);resultStatus=activity.findViewById(R.id.discoverStatus);
        sectionTitle=activity.findViewById(R.id.sectionTitle);scroll=activity.findViewById(R.id.contentScroll);
        for(int i=0;i<NAV.length;i++){final int index=i;activity.findViewById(NAV[i]).setOnClickListener(v->navigate(index));}
        activity.findViewById(R.id.detailBack).setOnClickListener(v->back());
        activity.findViewById(R.id.homeUpdates).setOnClickListener(v->{
            if(library.count()==0) navigate(1);
            else { navigate(3); if(!library.checking()) library.checkAll(); }
        });
        activity.findViewById(R.id.homeDiscover).setOnClickListener(v->navigate(1));
        activity.findViewById(R.id.homeLibrary).setOnClickListener(v->navigate(2));
        activity.findViewById(R.id.addLinkButton).setOnClickListener(v->{
            activity.findViewById(R.id.linkPanel).setVisibility(View.VISIBLE);
            activity.findViewById(R.id.urlInput).requestFocus();scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));
        });
        activity.findViewById(R.id.trustButton).setOnClickListener(v->{
            View proof=activity.findViewById(R.id.proofText),permissions=activity.findViewById(R.id.permissionsText);
            boolean open=proof.getVisibility()!=View.VISIBLE;proof.setVisibility(open?View.VISIBLE:View.GONE);
            permissions.setVisibility(open?View.VISIBLE:View.GONE);
            ((Button)v).setText(open?"APK Trust Center · Nachweise schließen −":"APK Trust Center · Nachweise ansehen +");
        });
        activity.findViewById(R.id.developerOnboarding).setOnClickListener(v->openWeb("/onboarding"));
        activity.findViewById(R.id.discoverSearch).setOnClickListener(v->{
            query=((EditText)activity.findViewById(R.id.discoverInput)).getText().toString().trim();page=1;following=false;catalog();
        });
        ((EditText)activity.findViewById(R.id.discoverInput)).setOnEditorActionListener((v,id,event)->{
            activity.findViewById(R.id.discoverSearch).performClick();return true;
        });
        activity.findViewById(R.id.followingButton).setOnClickListener(v->{following=true;feed();});
        activity.findViewById(R.id.discoverFilter).setOnClickListener(v->filters(v));
        restoreTab(0);
    }
    void restoreTab(int value) {tab=value>=0&&value<5?value:0;renderNavigation();}
    int tab() {return tab;}
    void resume() {if(tab==1&&!detail){if(following)feed();else if(!started)catalog();}}
    void close() {closed=true;request++;detailRequest++;network.shutdownNow();media.shutdownNow();}
    void changed() {
        int count=library.count(),updates=library.updateCount();
        ((Button)activity.findViewById(R.id.navUpdates)).setText(updates>0?"Updates ("+updates+")":"Updates");
        ((TextView)activity.findViewById(R.id.homeAppCount)).setText(String.valueOf(count));
        ((TextView)activity.findViewById(R.id.homeUpdateCount)).setText(String.valueOf(updates));
        ((TextView)activity.findViewById(R.id.homePreview)).setText(library.homePreview());
        ((TextView)activity.findViewById(R.id.homeStatus)).setText(library.homeStatus());
        ((Button)activity.findViewById(R.id.homeUpdates)).setText(count==0?"Apps entdecken →":"Jetzt Updates prüfen →");
    }
    private void navigate(int value) {
        if(activity.findViewById(R.id.progress).getVisibility()==View.VISIBLE) {
            Toast.makeText(activity,"Die laufende Prüfung bitte kurz abschließen lassen.",Toast.LENGTH_SHORT).show();return;
        }
        tab=value;detail=false;activity.findViewById(R.id.detailPanel).setVisibility(View.GONE);
        renderNavigation();scroll.scrollTo(0,0);if(tab==1){if(following)feed();else if(!started)catalog();}
    }
    private void renderNavigation() {
        sectionTitle.setText(TITLES[tab]);
        activity.findViewById(R.id.homePanel).setVisibility(!detail&&tab==0?View.VISIBLE:View.GONE);
        activity.findViewById(R.id.discoverPanel).setVisibility(!detail&&tab==1?View.VISIBLE:View.GONE);
        activity.findViewById(R.id.libraryPanel).setVisibility(!detail&&(tab==2||tab==3)?View.VISIBLE:View.GONE);
        activity.findViewById(R.id.settingsPanel).setVisibility(!detail&&tab==4?View.VISIBLE:View.GONE);
        activity.findViewById(R.id.linkPanel).setVisibility(View.GONE);
        library.updatesOnly(tab==3);
        for(int i=0;i<NAV.length;i++){View v=activity.findViewById(NAV[i]);v.setSelected(i==tab);((Button)v).setTextColor(activity.getColor(i==tab?R.color.lime:R.color.muted));}
        changed();
    }
    void showDetail() {
        detail=true;renderNavigation();sectionTitle.setText(activity.getString(R.string.message_storecontroller_17));
        activity.findViewById(R.id.detailPanel).setVisibility(View.VISIBLE);scroll.scrollTo(0,0);
    }
    boolean back() {if(!detail)return false;detail=false;detailRequest++;renderNavigation();activity.findViewById(R.id.detailPanel).setVisibility(View.GONE);return true;}

    private void filters(View anchor) {
        PopupMenu menu=new PopupMenu(activity,anchor);
        String[] values={"","communication","productivity","tools","privacy","media","games","education","other"};
        String[] names={"Alle Kategorien","Kommunikation","Produktivität","Tools","Privacy","Medien","Spiele","Lernen","Andere"};
        for(int i=0;i<values.length;i++){final String value=values[i],name=names[i];menu.getMenu().add(name).setOnMenuItemClickListener(item->{
            category=value;page=1;following=false;((Button)anchor).setText(name+" · App-Standard");catalog();return true;
        });}menu.show();
    }
    private void catalog() {
        started=true;final int ticket=++request;((ThreadPoolExecutor)media).getQueue().clear();results.removeAllViews();resultStatus.setText(activity.getString(R.string.message_storecontroller_18));
        String q=encode(query.substring(0,Math.min(100,query.length())));
        final String path="/api/discover?q="+q+"&category="+category+"&page="+page;
        submit(()->{
            JSONObject data=StoreClient.get(path);if(!"apkdrop.discover.v1".equals(data.optString("schema")))throw new SecurityException("Unbekannter App-Katalog.");
            JSONArray apps=data.getJSONArray("apps");if(apps.length()>24)throw new SecurityException("App-Katalog zu groß.");
            post(ticket,()->renderCatalog(data));
        },ticket);
    }
    void renderCatalog(JSONObject data) {
        JSONArray apps=data.optJSONArray("apps");
        if(!"apkdrop.discover.v1".equals(data.optString("schema")) || apps==null || apps.length()>24)throw new SecurityException("Ungültiger App-Katalog.");
        results.removeAllViews();
                resultStatus.setText(data.optInt("total")+" Apps · Seite "+data.optInt("page",1)+" / "+data.optInt("pages",1));
                for(int i=0;i<apps.length();i++)appCard(apps.optJSONObject(i),results);
                if(apps.length()==0) {results.addView(label("Hier ist noch kein Treffer. Versuche einen anderen Begriff oder eine andere Kategorie.",16));}
                int current=data.optInt("page",1),pages=data.optInt("pages",1);
                if(current>1)results.addView(button("← Vorherige Seite",()->{page=current-1;catalog();}));
                if(current<pages)results.addView(button("Weitere Apps →",()->{page=current+1;catalog();}));
    }
    private void appCard(JSONObject app,LinearLayout parent) {
        if(app==null)return;
        try {
            String slug=StoreClient.slug(app.getString("slug")),name=bounded(app.optString("name",slug),160);
            LinearLayout card=card();LinearLayout heading=new LinearLayout(activity);heading.setGravity(Gravity.CENTER_VERTICAL);
            ImageView icon=icon(52);heading.addView(icon);TextView title=label(name,20);title.setPadding(dp(12),0,0,0);heading.addView(title,new LinearLayout.LayoutParams(0,-2,1));card.addView(heading);
            loadImage(app.optString("iconUrl",""),icon);
            String description=bounded(app.optString("description"),400);if(!description.isEmpty())card.addView(label(description,14));
            JSONObject latest=app.optJSONObject("latest");String version=latest!=null?latest.optString("version"):app.optString("version");
            if(!version.isEmpty())card.addView(label("v"+bounded(version,120)+" · "+app.optString("channel","App-Standard"),12));
            JSONObject developer=app.optJSONObject("developer");
            if(developer!=null){String handle=StoreClient.handle(developer.getString("handle"));card.addView(button("@"+handle+" · DropID ↗",()->profile(handle)));}
            card.addView(button("App ansehen & prüfen →",()->select.accept(slug)));parent.addView(card);
        } catch(Exception ignored) {parent.addView(label("Ein App-Eintrag konnte nicht angezeigt werden.",13));}
    }
    void profile(String handle) {
        try {StoreClient.handle(handle);}catch(Exception e){error(e);return;}
        tab=1;detail=false;activity.findViewById(R.id.detailPanel).setVisibility(View.GONE);renderNavigation();
        final int ticket=++request;((ThreadPoolExecutor)media).getQueue().clear();results.removeAllViews();resultStatus.setText(activity.getString(R.string.message_storecontroller_19));
        submit(()->{
            JSONObject p=StoreClient.get("/api/dropid/"+handle+".json");JSONObject github=p.getJSONObject("github");String id=github.getString("id");
            if(!"apkdrop.dropid.v1".equals(p.optString("schema")) || !p.optBoolean("published") || !handle.equals(p.optString("handle")) || !DeveloperFollows.validId(id))throw new SecurityException("Entwicklerprofil nicht verfügbar.");
            final String name=bounded(p.getString("name"),160);final boolean saved=follows.contains(id);
            post(ticket,()->{
                resultStatus.setText("DropID · @"+handle);results.addView(label(name,28));results.addView(label(bounded(p.optString("bio"),400),14));
                results.addView(label("GitHub-Identität: "+("verified".equals(github.optString("status"))?"bestätigt":"Prüfung abgelaufen")+"\nBestätigt am: "+bounded(github.optString("verifiedAt"),80)+"\nDies bestätigt keine amtliche Identität oder Malware-Sicherheit.",12));
                results.addView(button(saved?"Entwickler nicht mehr folgen":"Entwickler folgen +",()->{try{follows.toggle(id,handle,name);profile(handle);}catch(Exception e){error(e);}}));
                results.addView(button("← Alle Apps entdecken",()->{following=false;catalog();}));results.addView(label("Veröffentlichte Apps",22));
                JSONArray apps=p.optJSONArray("apps");if(apps!=null)for(int i=0;i<Math.min(100,apps.length());i++)appCard(apps.optJSONObject(i),results);
            });
        },ticket);
    }
    private void feed() {
        final int ticket=++request;((ThreadPoolExecutor)media).getQueue().clear();results.removeAllViews();resultStatus.setText(activity.getString(R.string.message_storecontroller_20));
        try {
            JSONObject local=follows.read();java.util.Iterator<String> it=local.keys();
            while(it.hasNext()){String id=it.next();JSONObject item=local.getJSONObject(id);String handle=item.getString("handle");LinearLayout row=card();
                row.addView(button(item.getString("name")+" · @"+handle,()->profile(handle)));
                row.addView(button("Nicht mehr folgen",()->{try{follows.remove(id);feed();}catch(Exception e){error(e);}}));results.addView(row);
            }
            results.addView(button("← Apps entdecken",()->{following=false;catalog();}));
            JSONArray ids=follows.ids();if(ids.length()==0){resultStatus.setText(activity.getString(R.string.message_storecontroller_21));return;}
            submit(()->{
                JSONObject data=StoreClient.following(ids);JSONArray releases=data.getJSONArray("feed");
                if(releases.length()>60)throw new SecurityException("Release-Liste zu groß.");
                post(ticket,()->{
                    resultStatus.setText(releases.length()+" aktuelle öffentliche Releases"+(data.optBoolean("truncated")?" · App-Liste begrenzt":""));
                    if(releases.length()==0)results.addView(label("Aktuell keine öffentlichen Releases. Deine gespeicherten Follows kannst du jederzeit entfernen.",14));
                    for(int i=0;i<releases.length();i++){JSONObject r=releases.optJSONObject(i);if(r==null)continue;
                        try{String slug=StoreClient.slug(r.getString("slug"));LinearLayout c=card();c.addView(label(bounded(r.optString("name"),160),20));
                            c.addView(label(bounded(r.optString("version"),120)+" · "+bounded(r.optString("channel"),20)+"\n"+bounded(r.optString("publishedAt"),80),13));
                            c.addView(button("App-Standard prüfen →",()->select.accept(slug)));results.addView(c);
                        }catch(Exception ignored){}
                    }
                });
            },ticket);
        } catch(Exception e) {resultStatus.setText(message(e));}
    }
    void releaseDetails(String slug) {
        final int ticket=++detailRequest;((ThreadPoolExecutor)media).getQueue().clear();
        TextView publisher=activity.findViewById(R.id.developerText);publisher.setText(activity.getString(R.string.message_storecontroller_22));
        activity.findViewById(R.id.developerButton).setVisibility(View.GONE);
        LinearLayout gallery=activity.findViewById(R.id.screenshotList);gallery.removeAllViews();activity.findViewById(R.id.screenshotScroll).setVisibility(View.GONE);
        ImageView icon=activity.findViewById(R.id.detailIcon);icon.setImageResource(R.drawable.ic_apkdrop);
        network.execute(()->{
            try {
                JSONObject data=StoreClient.get("/api/"+StoreClient.slug(slug)+"/store.json");
                if(!"apkdrop.store.v1".equals(data.optString("schema")) || !slug.equals(data.optString("slug")))throw new SecurityException("App-Details stimmen nicht überein.");
                activity.runOnUiThread(()->{if(closed||ticket!=detailRequest)return;
                    loadImage(data.optString("iconUrl"),icon);JSONObject developer=data.optJSONObject("publisher");
                    publisher.setText(developer==null?"Kein öffentliches Entwicklerprofil verfügbar.":bounded(developer.optString("name"),160)+" · @"+bounded(developer.optString("handle"),39)+"\nGitHub-Identität: "+(developer.optBoolean("githubVerified")?"bestätigt":"Prüfung abgelaufen")+"\nRepository-Zuordnung: "+(developer.optJSONObject("repository")!=null&&"verified".equals(developer.optJSONObject("repository").optString("status"))?"bestätigt":"nicht aktuell bestätigt"));
                    if(developer!=null)try{String handle=StoreClient.handle(developer.getString("handle"));View b=activity.findViewById(R.id.developerButton);b.setVisibility(View.VISIBLE);b.setOnClickListener(v->profile(handle));}catch(Exception ignored){}
                    JSONArray screenshots=data.optJSONArray("screenshots");if(screenshots!=null)for(int i=0;i<Math.min(6,screenshots.length());i++){
                        ImageView shot=new ImageView(activity);shot.setContentDescription("Entwickler-Screenshot "+(i+1));shot.setAdjustViewBounds(true);shot.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(160),dp(285));lp.setMargins(0,0,dp(12),0);gallery.addView(shot,lp);loadImage(screenshots.optString(i),shot);
                    }
                    activity.findViewById(R.id.screenshotScroll).setVisibility(gallery.getChildCount()>0?View.VISIBLE:View.GONE);
                });
            } catch(Exception e) {activity.runOnUiThread(()->{if(!closed&&ticket==detailRequest)publisher.setText(activity.getString(R.string.message_storecontroller_23));});}
        });
    }
    private void loadImage(String url,ImageView view) {
        if(url==null||url.isEmpty()||url.equals("null"))return;
        try{StoreClient.imageUri(url);}catch(Exception invalid){return;}
        view.setTag(url);media.execute(()->{
            try{byte[] bytes=StoreClient.image(url);BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
                if(bounds.outWidth<1||bounds.outHeight<1||bounds.outWidth>12000||bounds.outHeight>12000)return;
                BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;
                while(Math.max(bounds.outWidth,bounds.outHeight)/options.inSampleSize>1200)options.inSampleSize*=2;
                Bitmap image=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);if(image==null)return;
                activity.runOnUiThread(()->{if(!closed&&url.equals(view.getTag()))view.setImageBitmap(image);});
            }catch(Exception unavailable){/* Optional media cannot authorize or block installation. */}
        });
    }
    private interface Work {void run() throws Exception;}
    private void submit(Work work,int ticket){network.execute(()->{try{work.run();}catch(Exception e){post(ticket,()->{resultStatus.setText(message(e));results.addView(button("Erneut versuchen",()->{if(following)feed();else catalog();}));});}});}
    private void post(int ticket,Runnable fn){activity.runOnUiThread(()->{if(!closed&&ticket==request&&!activity.isDestroyed())fn.run();});}
    private LinearLayout card(){LinearLayout c=new LinearLayout(activity);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(18),dp(18),dp(18),dp(18));c.setBackgroundResource(R.drawable.bg_card);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(14);c.setLayoutParams(p);return c;}
    private TextView label(String value,int size){TextView t=new TextView(activity);t.setText(value);t.setTextSize(size);t.setTextColor(activity.getColor(size>=20?R.color.text:R.color.muted));t.setPadding(0,dp(8),0,dp(8));if(size>=20)t.setTypeface(null,android.graphics.Typeface.BOLD);return t;}
    private Button button(String value,Runnable action){Button b=new Button(activity);b.setText(value);b.setTextSize(14);b.setAllCaps(false);b.setMinHeight(dp(48));b.setTextColor(activity.getColor(R.color.lime));b.setBackgroundResource(R.drawable.bg_input);b.setOnClickListener(v->action.run());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(10);b.setLayoutParams(p);return b;}
    private ImageView icon(int size){ImageView v=new ImageView(activity);v.setImageResource(R.drawable.ic_apkdrop);v.setLayoutParams(new LinearLayout.LayoutParams(dp(size),dp(size)));v.setContentDescription("App-Icon");return v;}
    private void openWeb(String path){try{activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(StoreClient.ORIGIN+path)));}catch(Exception e){error(e);}}
    private void error(Exception e){Toast.makeText(activity,message(e),Toast.LENGTH_LONG).show();}
    private static String encode(String value){try{return URLEncoder.encode(value,"UTF-8");}catch(java.io.UnsupportedEncodingException impossible){throw new AssertionError(impossible);}}
    private static String bounded(String value,int max){return value==null?"":value.substring(0,Math.min(max,value.length()));}
    private static String message(Exception e){return e.getMessage()==null?"Verbindung fehlgeschlagen. Bitte erneut versuchen.":e.getMessage();}
    private int dp(int n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
}
