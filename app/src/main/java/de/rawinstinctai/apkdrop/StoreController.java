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
    private final CatalogCache cache;
    private final FollowReadState readState;
    private final android.util.LruCache<String,Bitmap> images=new android.util.LruCache<String,Bitmap>(12*1024*1024) {
        @Override protected int sizeOf(String key,Bitmap image) {return image.getByteCount();}
    };
    private JSONObject catalogData;
    private final java.util.concurrent.ConcurrentHashMap<String,ReleaseRadar.State> radarStates=new java.util.concurrent.ConcurrentHashMap<>();
    private boolean catalogCached,sortByName,newDiscover,developersOnly;
    private final InstalledAppsController installedApps;
    private JSONArray homeReleases=new JSONArray();
    private boolean homeFeedKnown;
    private long catalogAt;
    private String activeProfile;
    private boolean newOnly=true,githubRadar;
    private GitHubRadarController radar;
    private final LinearLayout results;
    private final TextView resultStatus,sectionTitle;
    private final ScrollView scroll;
    private int tab,request,detailRequest,homeRequest;
    private long homeFeedAt;
    private String homeFeedIds="";
    private volatile boolean closed;
    private boolean detail,started,following;
    private String category="",query="";
    private int page=1;
    private static final int[] NAV={R.id.navHome,R.id.navDiscover,R.id.navApps,R.id.navUpdates,R.id.navSettings};
    private static final String[] TITLES={"Home","Entdecken","Meine Apps","Updates","Einstellungen"};

    StoreController(Activity activity,AppLibraryController library,Consumer<String> select) {
        this.activity=activity;this.library=library;this.select=select;follows=new DeveloperFollows(activity);cache=new CatalogCache(activity);readState=new FollowReadState(activity);
        results=activity.findViewById(R.id.discoverList);resultStatus=activity.findViewById(R.id.discoverStatus);
        sectionTitle=activity.findViewById(R.id.sectionTitle);scroll=activity.findViewById(R.id.contentScroll);
        for(int i=0;i<NAV.length;i++){final int index=i;activity.findViewById(NAV[i]).setOnClickListener(v->navigate(index));}
        activity.findViewById(R.id.detailBack).setOnClickListener(v->back());
        activity.findViewById(R.id.homeUpdates).setOnClickListener(v->{
            if(library.count()==0) navigate(1);
            else if(library.checking()) library.cancelChecks();
            else { navigate(3); if(library.updateCount()>0)library.updateOverview();else library.checkAll(); }
        });
        activity.findViewById(R.id.homeDiscover).setOnClickListener(v->navigate(1));
        activity.findViewById(R.id.homeLibrary).setOnClickListener(v->navigate(2));
        activity.findViewById(R.id.homeAddLink).setOnClickListener(v->{navigate(2);activity.findViewById(R.id.addLinkButton).performClick();});
        activity.findViewById(R.id.homeFollowing).setOnClickListener(v->{activeProfile=null;following=true;navigate(1);});
        activity.findViewById(R.id.addLinkButton).setOnClickListener(v->{
            activity.findViewById(R.id.linkPanel).setVisibility(View.VISIBLE);
            activity.findViewById(R.id.urlInput).requestFocus();scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));
        });
        activity.findViewById(R.id.trustButton).setOnClickListener(v->{
            View proof=activity.findViewById(R.id.proofText),permissions=activity.findViewById(R.id.permissionsText);
            boolean open=proof.getVisibility()!=View.VISIBLE;proof.setVisibility(open?View.VISIBLE:View.GONE);
            permissions.setVisibility(open?View.VISIBLE:View.GONE);
            activity.findViewById(R.id.trustSummary).setVisibility(open?View.VISIBLE:View.GONE);
            ((Button)v).setText(open?"APK Trust Center · Nachweise schließen −":"APK Trust Center · Nachweise ansehen +");
        });
        activity.findViewById(R.id.developerOnboarding).setOnClickListener(v->openWeb("/onboarding"));
        activity.findViewById(R.id.discoverSearch).setOnClickListener(v->{
            hideKeyboard();activeProfile=null;query=((EditText)activity.findViewById(R.id.discoverInput)).getText().toString().trim();page=1;following=false;renderCategories();catalog();
        });
        ((EditText)activity.findViewById(R.id.discoverInput)).setOnEditorActionListener((v,id,event)->{
            activity.findViewById(R.id.discoverSearch).performClick();return true;
        });
        activity.findViewById(R.id.followingButton).setOnClickListener(v->{activeProfile=null;following=true;feed();});
        activity.findViewById(R.id.discoverRefresh).setOnClickListener(v->{if(activeProfile!=null)profile(activeProfile);else if(following)feed();else catalog();});
        activity.findViewById(R.id.discoverReset).setOnClickListener(v->{
            query="";category="";page=1;following=false;activeProfile=null;sortByName=false;newDiscover=false;developersOnly=false;renderCategories();
            ((EditText)activity.findViewById(R.id.discoverInput)).setText("");
            ((Button)activity.findViewById(R.id.discoverFilter)).setText("Alle Kategorien");
            updateSortLabel();catalog();
        });
        activity.findViewById(R.id.discoverSort).setOnClickListener(v->{
            PopupMenu options=new PopupMenu(activity,v);
            options.getMenu().add("Neueste zuerst").setCheckable(true).setChecked(!sortByName).setOnMenuItemClickListener(item->{chooseSort(false);return true;});
            options.getMenu().add("Name A–Z").setCheckable(true).setChecked(sortByName).setOnMenuItemClickListener(item->{chooseSort(true);return true;});
            options.show();
        });
        activity.findViewById(R.id.discoverFilter).setOnClickListener(v->filters(v));
        radar=new GitHubRadarController(activity,()->{if(radar!=null)renderActionCenter();},select);
        installedApps=new InstalledAppsController(activity,library);
        activity.findViewById(R.id.homeGitHubRadar).setOnClickListener(v->showGitHubRadar());
        activity.findViewById(R.id.settingsGitHubRadar).setOnClickListener(v->showGitHubRadar());
        activity.findViewById(R.id.radarGitHub).setOnClickListener(v->showGitHubRadar());
        activity.findViewById(R.id.radarReleases).setOnClickListener(v->{githubRadar=false;activeProfile=null;following=true;renderNavigation();feed();});
        activity.findViewById(R.id.radarApps).setOnClickListener(v->{githubRadar=false;activeProfile=null;following=false;renderNavigation();catalog();});
        activity.findViewById(R.id.libraryDiscover).setOnClickListener(v->navigate(1));
        renderCategories();
        restoreTab(0);
    }
    void restoreTab(int value) {tab=value>=0&&value<5?value:0;renderNavigation();}
    int tab() {return tab;}
    void saveState(android.os.Bundle state) {
        state.putBoolean("newDiscover",newDiscover);state.putBoolean("developersOnly",developersOnly);state.putBoolean("githubRadar",githubRadar);state.putInt("storeTab",tab);state.putString("discoverQuery",bounded(query,100));state.putString("discoverCategory",category);
        state.putInt("discoverPage",page);state.putBoolean("discoverSortByName",sortByName);state.putBoolean("following",following);
        state.putBoolean("followingNewOnly",newOnly);state.putString("discoverProfile",activeProfile);
    }
    void restoreState(android.os.Bundle state) {
        if(state!=null) {
            newDiscover=state.getBoolean("newDiscover");developersOnly=state.getBoolean("developersOnly");query=bounded(state.getString("discoverQuery",""),100);category=state.getString("discoverCategory","");
            if(!java.util.Set.of("","communication","productivity","tools","privacy","media","games","education","other").contains(category))category="";
            page=Math.max(1,Math.min(100000,state.getInt("discoverPage",1)));sortByName=state.getBoolean("discoverSortByName");
            following=state.getBoolean("following");newOnly=state.getBoolean("followingNewOnly",true);githubRadar=state.getBoolean("githubRadar");activeProfile=state.getString("discoverProfile");
            if(activeProfile!=null)try {StoreClient.handle(activeProfile);}catch(Exception bad){activeProfile=null;}
        }
        ((EditText)activity.findViewById(R.id.discoverInput)).setText(query);
        ((Button)activity.findViewById(R.id.discoverFilter)).setText(categoryName(category));
        updateSortLabel();
        renderCategories();restoreTab(state==null?0:state.getInt("storeTab",0));
    }
    void pause(){if(radar!=null)radar.pause();}
    void showGitHubRadar(){githubRadar=true;following=false;activeProfile=null;navigate(1);radar.show();}
    void resume() {radarStates.clear();started=false;radar.resume();if(tab==0&&!detail)homeFeed();if(tab==1&&!detail){if(githubRadar)radar.show();else if(activeProfile!=null)profile(activeProfile);else if(following)feed();else if(!started)catalog();}}
    void close() {installedApps.close();radar.close();closed=true;request++;detailRequest++;homeRequest++;network.shutdownNow();media.shutdownNow();images.evictAll();}
    void changed() {
        renderActionCenter();
        int count=library.count(),updates=library.updateCount();
        ((Button)activity.findViewById(R.id.navUpdates)).setText(updates>0?"Updates ("+updates+")":"Updates");
        ((TextView)activity.findViewById(R.id.homeAppCount)).setText(String.valueOf(count));
        ((TextView)activity.findViewById(R.id.homeUpdateCount)).setText(String.valueOf(updates));
        ((TextView)activity.findViewById(R.id.homePreview)).setText(library.homePreview());
        ((TextView)activity.findViewById(R.id.homeHeadline)).setText(count==0?"Deine Apps.\nDeine Freiheit.":updates>0?"Deine Updates.\nDeine Entscheidung.":"Dein App-Radar.");
        ((TextView)activity.findViewById(R.id.homeStatus)).setText(library.homeStatus());
        TextView pilot=activity.findViewById(R.id.homePilotStatus);
        pilot.setText(DropPilot.preparedSlug(activity)!=null?"DropPilot · Update bereit zur Installation →":"DropPilot · "+DropPilot.headline(activity));
        pilot.setOnClickListener(v->{String slug=DropPilot.preparedSlug(activity);if(slug!=null)select.accept(slug);else navigate(4);});
        pilot.setVisibility(DropPilot.enabled(activity)?View.VISIBLE:View.GONE);
        ((Button)activity.findViewById(R.id.homeUpdates)).setText(library.checking()?"Prüfung abbrechen":count==0?"Apps entdecken →":updates>0?updates+" Updates gemeinsam prüfen →":"Jetzt Updates prüfen →");
        LinearLayout actions=activity.findViewById(R.id.homeAppActions);actions.removeAllViews();
        for(AppLibrary.Entry entry:library.homeEntries()) actions.addView(homeCard(entry));
        LinearLayout checks=activity.findViewById(R.id.homeActivity);checks.removeAllViews();checks.addView(label(library.recentChecks(),12));
        ((TextView)activity.findViewById(R.id.homePreview)).setVisibility(count==0?View.VISIBLE:View.GONE);
        try {((Button)activity.findViewById(R.id.homeFollowing)).setText("Entwickler & Releases · "+follows.ids().length()+" gefolgt →");}catch(Exception unavailable){}
        activity.findViewById(R.id.homeUpdates).setEnabled(activity.findViewById(R.id.progress).getVisibility()!=View.VISIBLE);
    }
    private void navigate(int value) {
        if(activity.findViewById(R.id.progress).getVisibility()==View.VISIBLE) {
            Toast.makeText(activity,"Die laufende Prüfung bitte kurz abschließen lassen.",Toast.LENGTH_SHORT).show();return;
        }
        hideKeyboard();tab=value;detail=false;activity.findViewById(R.id.detailPanel).setVisibility(View.GONE);
        renderNavigation();scroll.scrollTo(0,0);if(tab==0)homeFeed();if(tab==1){if(githubRadar)radar.show();else if(activeProfile!=null)profile(activeProfile);else if(following)feed();else if(!started)catalog();}
    }
    private void renderNavigation() {
        sectionTitle.setText(TITLES[tab]);sectionTitle.setVisibility(!detail&&tab==0?View.GONE:View.VISIBLE);
        activity.findViewById(R.id.homeHeadline).setVisibility(tab==0?View.VISIBLE:View.GONE);
        activity.findViewById(R.id.homePanel).setVisibility(!detail&&tab==0?View.VISIBLE:View.GONE);
        activity.findViewById(R.id.discoverPanel).setVisibility(!detail&&tab==1?View.VISIBLE:View.GONE);
        activity.findViewById(R.id.discoverControls).setVisibility(!githubRadar&&!following?View.VISIBLE:View.GONE);
        activity.findViewById(R.id.discoverList).setVisibility(!githubRadar?View.VISIBLE:View.GONE);
        activity.findViewById(R.id.discoverStatus).setVisibility(!githubRadar?View.VISIBLE:View.GONE);
        activity.findViewById(R.id.githubRadarPanel).setVisibility(githubRadar?View.VISIBLE:View.GONE);
        activity.findViewById(R.id.radarApps).setSelected(!githubRadar&&!following);
        activity.findViewById(R.id.radarReleases).setSelected(!githubRadar&&following);
        activity.findViewById(R.id.radarGitHub).setSelected(githubRadar);
        for(int id:new int[]{R.id.radarApps,R.id.radarReleases,R.id.radarGitHub}){Button chip=activity.findViewById(id);chip.setTextColor(activity.getColor(chip.isSelected()?R.color.lime_dark:R.color.lime));}
        activity.findViewById(R.id.libraryPanel).setVisibility(!detail&&(tab==2||tab==3)?View.VISIBLE:View.GONE);
        activity.findViewById(R.id.settingsPanel).setVisibility(!detail&&tab==4?View.VISIBLE:View.GONE);
        activity.findViewById(R.id.linkPanel).setVisibility(View.GONE);
        library.updatesOnly(tab==3);
        activity.findViewById(R.id.findInstalledApps).setVisibility(tab==2?View.VISIBLE:View.GONE);activity.findViewById(R.id.installedSuggestions).setVisibility(tab==2?View.VISIBLE:View.GONE);
        for(int i=0;i<NAV.length;i++){View v=activity.findViewById(NAV[i]);v.setSelected(i==tab);((Button)v).setTextColor(activity.getColor(i==tab?R.color.lime:R.color.muted));
            for(android.graphics.drawable.Drawable d:((Button)v).getCompoundDrawables())if(d!=null)d.mutate().setTint(activity.getColor(i==tab?R.color.lime:R.color.muted));}
        changed();
    }
    void showDetail() {
        hideKeyboard();detail=true;renderNavigation();sectionTitle.setText(activity.getString(R.string.message_storecontroller_17));
        activity.findViewById(R.id.detailPanel).setVisibility(View.VISIBLE);scroll.scrollTo(0,0);
    }
    boolean back() {if(!detail)return false;if(activity.findViewById(R.id.progress).getVisibility()==View.VISIBLE){Toast.makeText(activity,"Die laufende Prüfung bitte kurz abschließen lassen.",Toast.LENGTH_SHORT).show();return true;}detail=false;detailRequest++;renderNavigation();activity.findViewById(R.id.detailPanel).setVisibility(View.GONE);return true;}

    private static String categoryName(String category) {
        return switch(category) {
            case "communication" -> "Kommunikation";case "productivity" -> "Produktivität";case "tools" -> "Tools";
            case "privacy" -> "Privacy";case "media" -> "Medien";case "games" -> "Spiele";case "education" -> "Lernen";
            case "other" -> "Andere";default -> "Alle Kategorien";
        };
    }
    private void updateSortLabel() {
        Button sort=activity.findViewById(R.id.discoverSort);
        sort.setText(sortByName?"Name A–Z ▾":"Neueste ▾");
        sort.setContentDescription(sortByName?"Sortierung: Name A bis Z":"Sortierung: Neueste zuerst");
    }
    private void chooseSort(boolean byName) {
        sortByName=byName;updateSortLabel();
        if(catalogData!=null)renderCatalogState();
    }
    private void filters(View anchor) {
        PopupMenu menu=new PopupMenu(activity,anchor);
        String[] values={"","communication","productivity","tools","privacy","media","games","education","other"};
        String[] names={"Alle Kategorien","Kommunikation","Produktivität","Tools","Privacy","Medien","Spiele","Lernen","Andere"};
        for(int i=0;i<values.length;i++){final String value=values[i],name=names[i];menu.getMenu().add(name).setOnMenuItemClickListener(item->{
            activeProfile=null;category=value;page=1;following=false;((Button)anchor).setText(name);renderCategories();catalog();return true;
        });}menu.show();
    }
    private static String radarKey(JSONObject release){return release.optString("packageName")+"/"+release.optLong("versionCode")+"/"+String.valueOf(release.optJSONArray("signers"));}
    private ReleaseRadar.State radarState(JSONObject release){return radarStates.getOrDefault(radarKey(release),ReleaseRadar.State.UNKNOWN);}
    private void prepareRadar(JSONArray releases) {
        if(releases==null)return;
        java.util.Map<String,InstalledState> installed=new java.util.HashMap<>();
        java.util.Set<String> unavailable=new java.util.HashSet<>();
        if(radarStates.size()>500)radarStates.clear();
        for(int i=0;i<releases.length();i++){
            JSONObject release=releases.optJSONObject(i);if(release==null)continue;
            String pkg=release.optString("packageName");ReleaseRadar.State state=ReleaseRadar.State.UNKNOWN;
            if(pkg.matches("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+")){
                if(!installed.containsKey(pkg)&&!unavailable.contains(pkg))try{installed.put(pkg,InstalledState.read(activity,pkg));}catch(Exception e){unavailable.add(pkg);}
                if(!unavailable.contains(pkg))state=ReleaseRadar.compare(release,installed.get(pkg));
            }
            radarStates.put(radarKey(release),state);
        }
    }
    private void catalog() {
        githubRadar=false;activeProfile=null;following=false;started=true;catalogControls(true);
        final int ticket=++request;((ThreadPoolExecutor)media).getQueue().clear();
        results.removeAllViews();skeletons();catalogData=null;
        activity.findViewById(R.id.discoverCount).setVisibility(View.GONE);
        resultStatus.setVisibility(View.VISIBLE);resultStatus.setText(activity.getString(R.string.message_storecontroller_18));
        String q=encode(query.substring(0,Math.min(100,query.length())));
        final String path="/api/discover?q="+q+"&category="+category+"&page="+page+"&sort="+(newDiscover?"new":"updated");
        network.execute(()->{
            CatalogCache.Entry saved=cache.read(path);
            if(saved!=null){prepareRadar(saved.data.optJSONArray("apps"));}if(saved!=null)post(ticket,()->{catalogData=saved.data;catalogCached=true;catalogAt=saved.at;renderCatalogState();});
            try {
                JSONObject data=StoreClient.get(path);CatalogCache.validate(data);cache.save(path,data);prepareRadar(data.optJSONArray("apps"));
                post(ticket,()->{catalogData=data;catalogCached=false;catalogAt=System.currentTimeMillis();renderCatalogState();});
            } catch(Exception e) {
                if(!(e instanceof java.io.IOException))cache.remove(path);
                post(ticket,()->{
                    // Malformed/currently revoked metadata is not replaced with stale success.
                    if(!(e instanceof java.io.IOException) || saved==null) {results.removeAllViews();catalogData=null;resultStatus.setText(message(e));}
                    else resultStatus.append("\nAktualisierung fehlgeschlagen. Gespeicherter Katalog bleibt lesbar.");
                    results.addView(button("Erneut versuchen",this::catalog));
                });
            }
        });
    }
    private void catalogControls(boolean visible) {
        activity.findViewById(R.id.categoryScroll).setVisibility(visible?View.VISIBLE:View.GONE);
        activity.findViewById(R.id.discoverFilter).setVisibility(View.GONE);
        if(!visible)activity.findViewById(R.id.discoverCount).setVisibility(View.GONE);
        activity.findViewById(R.id.discoverSort).setVisibility(visible?View.VISIBLE:View.GONE);
        activity.findViewById(R.id.discoverReset).setVisibility(visible&&(!query.isEmpty()||!category.isEmpty()||sortByName)?View.VISIBLE:View.GONE);
    }
    void renderCatalog(JSONObject data) {
        catalogData=data;catalogCached=false;catalogAt=System.currentTimeMillis();renderCatalogState();
    }
    private void renderCatalogState() {
        JSONObject data=catalogData;
        try {CatalogCache.validate(data);}catch(Exception bad){throw new SecurityException("Ungültiger App-Katalog.",bad);}
        JSONArray apps=data.optJSONArray("apps");results.removeAllViews();catalogControls(true);
        int total=data.optInt("total"),pages=data.optInt("pages",1);
        TextView count=activity.findViewById(R.id.discoverCount);
        count.setText(total+(total==1?" App":" Apps")+(pages>1?" · Seite "+data.optInt("page",1)+"/"+pages:""));
        count.setVisibility(View.VISIBLE);
        resultStatus.setText(catalogCached?"Gespeicherter Katalog · "+java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT).format(new java.util.Date(catalogAt))+"\nApp-Details und Downloads brauchen eine neue Online-Prüfung.":"");
        resultStatus.setVisibility(catalogCached?View.VISIBLE:View.GONE);
        if(query.isEmpty()&&category.isEmpty()){renderCollections();}
        java.util.List<JSONObject> entries=new java.util.ArrayList<>();for(int i=0;i<apps.length();i++)entries.add(apps.optJSONObject(i));
        if(sortByName)entries.sort(java.util.Comparator.comparing(app->app.optString("name",app.optString("slug")),String.CASE_INSENSITIVE_ORDER));
        if(developersOnly){renderCatalogDevelopers(entries);}else{results.addView(label(newDiscover?"Neu entdeckt":"Frisch aktualisiert",20));results.addView(label(sortByName?"Alphabetisch nach App-Namen sortiert"+(pages>1?" (diese Seite)":""):newDiscover?"Nach öffentlichem App-Launch sortiert":"Nach dem Datum des neuesten Releases sortiert",12));for(JSONObject app:entries)appCard(app,results);}
        if(apps.length()==0) results.addView(label("Hier ist noch kein Treffer. Versuche einen anderen Begriff oder setze Suche & Filter zurück.",16));
        int current=data.optInt("page",1);
        if(current>1)results.addView(button("← Vorherige Seite",()->{page=current-1;catalog();scroll.scrollTo(0,0);}));
        if(current<pages)results.addView(button("Weitere Apps →",()->{page=current+1;catalog();scroll.scrollTo(0,0);}));
    }
    private void appCard(JSONObject app,LinearLayout parent) {
        if(app==null)return;
        try {
            String slug=StoreClient.slug(app.getString("slug")),name=bounded(app.optString("name",slug),160);
            LinearLayout card=card();card.setTag(slug);LinearLayout heading=new LinearLayout(activity);heading.setGravity(Gravity.CENTER_VERTICAL);
            ImageView icon=icon(52);icon.setImageDrawable(new AppPlaceholder(name));heading.addView(icon);TextView title=label(name,20);title.setPadding(dp(12),0,0,0);heading.addView(title,new LinearLayout.LayoutParams(0,-2,1));card.addView(heading);
            loadImage(app.optString("iconUrl",""),icon);
            AppLibrary.Entry saved=library.find(slug);if(saved!=null)icon.setImageDrawable(library.appIcon(saved));
            boolean unseen=readState.unseen(app);ReleaseRadar.State state=radarState(app);
            TextView signal=label(ReleaseRadar.label(state,unseen),12);signal.setTextColor(activity.getColor(state==ReleaseRadar.State.DIFFERENT_SIGNER?R.color.danger:R.color.lime));card.addView(signal);
            String description=bounded(app.optString("description"),400);if(!description.isEmpty()){TextView summary=label(description,13);summary.setMaxLines(3);summary.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(summary);}
            JSONObject latest=app.optJSONObject("latest");String version=latest!=null?latest.optString("version"):app.optString("version");
            if(!version.isEmpty())card.addView(label(DisplayText.version(bounded(version,120))+" · "+app.optString("channel","App-Standard"),12));
            card.addView(label(categoryName(app.optString("category"))+" · "+DisplayText.date(app.optString("publishedAt")),11));
            TextView trust=label(DropTrust.catalog(app,state),11);trust.setTextColor(activity.getColor(state==ReleaseRadar.State.DIFFERENT_SIGNER?R.color.danger:R.color.muted));card.addView(trust);
            JSONObject developer=app.optJSONObject("developer");
            if(developer!=null){String handle=StoreClient.handle(developer.getString("handle"));card.addView(textAction("@"+handle+" · DropID ↗",()->profile(handle)));}
            card.addView(button(state==ReleaseRadar.State.INSTALLED?"App ansehen →":state==ReleaseRadar.State.UPDATE?"Update ansehen & prüfen →":"App ansehen & prüfen →",()->{try{readState.mark(new JSONArray().put(app));}catch(Exception e){error(e);}select.accept(slug);}));parent.addView(card);
        } catch(Exception ignored) {parent.addView(label("Ein App-Eintrag konnte nicht angezeigt werden.",13));}
    }
    void profile(String handle) {
        try {StoreClient.handle(handle);}catch(Exception e){error(e);return;}
        githubRadar=false;activeProfile=handle;following=false;catalogControls(false);tab=1;detail=false;activity.findViewById(R.id.detailPanel).setVisibility(View.GONE);renderNavigation();
        final int ticket=++request;((ThreadPoolExecutor)media).getQueue().clear();results.removeAllViews();resultStatus.setVisibility(View.VISIBLE);resultStatus.setText(activity.getString(R.string.message_storecontroller_19));
        submit(()->{
            JSONObject p=StoreClient.get("/api/dropid/"+handle+".json");JSONObject github=p.getJSONObject("github");String id=github.getString("id");
            if(!"apkdrop.dropid.v1".equals(p.optString("schema")) || !p.optBoolean("published") || !handle.equals(p.optString("handle")) || !DeveloperFollows.validId(id))throw new SecurityException("Entwicklerprofil nicht verfügbar.");
            prepareRadar(p.optJSONArray("apps"));
            final String name=bounded(p.getString("name"),160);final boolean saved=follows.contains(id);
            post(ticket,()->{
                resultStatus.setText("DropID · @"+handle);
                renderDeveloperHeader(name,handle,id,p.optString("avatarUrl",""));
                results.addView(label(bounded(p.optString("bio"),400),14));
                results.addView(label("GitHub-Identität: "+("verified".equals(github.optString("status"))?"bestätigt":"Prüfung abgelaufen")+"\nBestätigt am: "+bounded(github.optString("verifiedAt"),80)+"\nDies bestätigt keine amtliche Identität oder Malware-Sicherheit.",12));
                results.addView(button(saved?"Entwickler nicht mehr folgen":"Entwickler folgen +",()->{try{follows.toggle(id,handle,name);profile(handle);}catch(Exception e){error(e);}}));
                results.addView(button("← Alle Apps entdecken",()->{following=false;catalog();}));results.addView(label("Veröffentlichte Apps",22));
                JSONArray apps=p.optJSONArray("apps");if(apps!=null)for(int i=0;i<Math.min(100,apps.length());i++)appCard(apps.optJSONObject(i),results);
            });
        },ticket);
    }
    /** Profile picture is display-only and cannot influence DropID verification or installs. */
    void renderDeveloperHeader(String name,String handle,String githubId,String avatarUrl) {
        LinearLayout header=new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0,dp(10),0,dp(12));
        FrameLayout portrait=new FrameLayout(activity);
        LinearLayout.LayoutParams portraitSize=new LinearLayout.LayoutParams(dp(76),dp(76));
        portraitSize.rightMargin=dp(16);
        android.graphics.drawable.GradientDrawable background=new android.graphics.drawable.GradientDrawable();
        background.setColor(0xFF242D1C);
        background.setCornerRadius(dp(22));
        background.setStroke(dp(1),0xFF37432D);
        portrait.setBackground(background);
        portrait.setClipToOutline(true);
        portrait.setContentDescription("Entwicklerprofil von "+name);
        TextView initial=new TextView(activity);
        String first=name.trim().isEmpty()?"?":name.trim().substring(0,1).toUpperCase(java.util.Locale.ROOT);
        initial.setText(first);
        initial.setTextColor(activity.getColor(R.color.lime));
        initial.setTextSize(30);
        initial.setGravity(Gravity.CENTER);
        initial.setTypeface(null,android.graphics.Typeface.BOLD);
        portrait.addView(initial,new FrameLayout.LayoutParams(-1,-1));
        ImageView avatar=new ImageView(activity);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.setVisibility(View.GONE);
        avatar.setContentDescription("GitHub-Profilbild von "+name);
        portrait.addView(avatar,new FrameLayout.LayoutParams(-1,-1));
        header.addView(portrait,portraitSize);
        LinearLayout info=new LinearLayout(activity);
        info.setOrientation(LinearLayout.VERTICAL);
        info.addView(label(name,24));
        info.addView(label("@"+handle+" · GitHub-Profil",12));
        header.addView(info,new LinearLayout.LayoutParams(0,-2,1));
        results.addView(header);
        loadDeveloperAvatar(avatarUrl,githubId,avatar);
    }
    private void feed() {
        githubRadar=false;activeProfile=null;following=true;catalogControls(false);
        final int ticket=++request;((ThreadPoolExecutor)media).getQueue().clear();results.removeAllViews();resultStatus.setVisibility(View.VISIBLE);resultStatus.setText(activity.getString(R.string.message_storecontroller_20));
        try {
            JSONArray ids=follows.ids();if(ids.length()==0){resultStatus.setText(activity.getString(R.string.message_storecontroller_21));return;}
            submit(()->{
                JSONObject data=StoreClient.following(ids);JSONArray releases=data.getJSONArray("feed");
                if(releases.length()>60)throw new SecurityException("Release-Liste zu groß.");
                prepareRadar(releases);post(ticket,()->renderFeed(releases,data.optBoolean("truncated")));

            },ticket);
        } catch(Exception e) {resultStatus.setText(message(e));}
    }
    private void renderFeed(JSONArray releases,boolean truncated) {
        resultStatus.setVisibility(View.VISIBLE);results.removeAllViews();
        int unseen=0;for(int i=0;i<releases.length();i++)if(releases.optJSONObject(i)!=null&&readState.unseen(releases.optJSONObject(i))&&radarState(releases.optJSONObject(i))!=ReleaseRadar.State.INSTALLED)unseen++;
        resultStatus.setText(unseen+" neu für dich · "+releases.length()+" öffentliche Releases"+(truncated?" · Liste begrenzt":""));
        LinearLayout feedRows=new LinearLayout(activity);feedRows.setOrientation(LinearLayout.VERTICAL);results.addView(feedRows);
        feedRows.addView(button(newOnly?"Alle Releases anzeigen":"Nur neue Releases anzeigen",()->{newOnly=!newOnly;renderFeed(releases,truncated);}));
        if(unseen>0) feedRows.addView(button("Diese Releases als gelesen markieren",()->{
            try {readState.mark(releases);homeFeedAt=0;renderActionCenter();renderFeed(releases,truncated);}catch(Exception e){error(e);}
        }));
        int shown=0;
        for(int i=0;i<releases.length();i++) {
            JSONObject r=releases.optJSONObject(i);if(r==null)continue;
            try {
                String slug=StoreClient.slug(r.getString("slug"));boolean isNew=readState.unseen(r);ReleaseRadar.State installed=radarState(r);if(newOnly&&(!isNew||installed==ReleaseRadar.State.INSTALLED))continue;shown++;
                LinearLayout c=card();LinearLayout heading=new LinearLayout(activity);heading.setGravity(Gravity.CENTER_VERTICAL);ImageView releaseIcon=icon(44);releaseIcon.setImageDrawable(new AppPlaceholder(r.optString("name")));loadImage(r.optString("iconUrl"),releaseIcon);heading.addView(releaseIcon);TextView title=label(bounded(r.optString("name"),160),20);title.setPadding(dp(12),0,0,0);heading.addView(title,new LinearLayout.LayoutParams(0,-2,1));c.addView(heading);
                c.addView(label((ReleaseRadar.label(installed,isNew)+" · ")+DisplayText.version(bounded(r.optString("version"),120))+" · "+bounded(r.optString("channel"),20)+"\n"+DisplayText.date(r.optString("publishedAt")),13));
                String changes=bounded(r.optString("notes"),12000);TextView excerpt=label(changes.isBlank()?"Für diesen Release gibt es keine Änderungsnotizen.":ReleaseNotes.render(changes).toString(),13);excerpt.setMaxLines(3);excerpt.setEllipsize(android.text.TextUtils.TruncateAt.END);c.addView(excerpt);if(!changes.isBlank())c.addView(textAction("Was ist neu? +",()->new AlertDialog.Builder(activity).setTitle("Was ist neu? · "+DisplayText.version(r.optString("version"))).setMessage(ReleaseNotes.render(changes)).setPositiveButton("Schließen",null).show()));JSONObject developer=r.optJSONObject("developer");if(developer!=null){String handle=developer.optString("handle");c.addView(textAction("@"+handle+" · Entwickler ↗",()->profile(handle)));}c.addView(label(DropTrust.catalog(r,installed),11));
                c.addView(button("App-Standard prüfen →",()->{
                    try {readState.mark(new JSONArray().put(r));homeFeedAt=0;renderActionCenter();}catch(Exception e){error(e);}
                    select.accept(slug);
                }));feedRows.addView(c);
            } catch(Exception ignored){}
        }
        results.addView(textAction("Gefolgte Entwickler verwalten →",this::manageFollows));
        if(shown==0) feedRows.addView(label(newOnly?"Alles angesehen. Neue Releases erscheinen hier beim nächsten Aktualisieren.":"Aktuell keine öffentlichen Releases. Deine Follows bleiben lokal gespeichert.",14));
    }
    void clearReleaseDetails() {
        detailRequest++;
        ((ImageView)activity.findViewById(R.id.detailIcon)).setImageDrawable(new AppPlaceholder("App"));
        activity.findViewById(R.id.detailIcon).setTag(null);
        ((TextView)activity.findViewById(R.id.developerText)).setText("");
        activity.findViewById(R.id.developerButton).setVisibility(View.GONE);
        ((LinearLayout)activity.findViewById(R.id.screenshotList)).removeAllViews();
        activity.findViewById(R.id.screenshotScroll).setVisibility(View.GONE);activity.findViewById(R.id.mediaCaption).setVisibility(View.GONE);
    }
    void releaseDetails(String slug) {
        final int ticket=++detailRequest;((ThreadPoolExecutor)media).getQueue().clear();
        TextView publisher=activity.findViewById(R.id.developerText);publisher.setText(activity.getString(R.string.message_storecontroller_22));
        activity.findViewById(R.id.developerButton).setVisibility(View.GONE);
        LinearLayout gallery=activity.findViewById(R.id.screenshotList);gallery.removeAllViews();activity.findViewById(R.id.screenshotScroll).setVisibility(View.GONE);activity.findViewById(R.id.mediaCaption).setVisibility(View.GONE);
        ImageView icon=activity.findViewById(R.id.detailIcon);icon.setTag(null);icon.setImageDrawable(new AppPlaceholder(((TextView)activity.findViewById(R.id.titleText)).getText().toString()));
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
                        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(160),dp(285));lp.setMargins(0,0,dp(12),0);gallery.addView(shot,lp);String imageUrl=screenshots.optString(i);loadImage(imageUrl,shot);shot.setOnClickListener(v->showScreenshot(imageUrl));
                    }
                    activity.findViewById(R.id.screenshotScroll).setVisibility(gallery.getChildCount()>0?View.VISIBLE:View.GONE);
                    activity.findViewById(R.id.mediaCaption).setVisibility(gallery.getChildCount()>0?View.VISIBLE:View.GONE);
                });
            } catch(Exception e) {activity.runOnUiThread(()->{if(!closed&&ticket==detailRequest)publisher.setText(activity.getString(R.string.message_storecontroller_23));});}
        });
    }
    private void loadImage(String url,ImageView view) {loadImage(url,null,view);}
    private void loadDeveloperAvatar(String url,String githubId,ImageView view) {loadImage(url,githubId,view);}
    private void loadImage(String url,String githubId,ImageView view) {
        if(url==null||url.isEmpty()||url.equals("null"))return;
        try {if(githubId==null)StoreClient.imageUri(url);else StoreClient.avatarUri(url,githubId);}
        catch(Exception invalid){return;}
        view.setTag(url);Bitmap cached=images.get(url);
        if(cached!=null){view.setImageBitmap(cached);view.setVisibility(View.VISIBLE);return;}
        media.execute(()->{
            try{byte[] bytes=githubId==null?StoreClient.image(url):StoreClient.avatar(url,githubId);
                BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
                if(bounds.outWidth<1||bounds.outHeight<1||bounds.outWidth>12000||bounds.outHeight>12000)return;
                BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;
                while(Math.max(bounds.outWidth,bounds.outHeight)/options.inSampleSize>1200)options.inSampleSize*=2;
                Bitmap image=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);if(image==null)return;
                if(closed)return;images.put(url,image);
                activity.runOnUiThread(()->{if(!closed&&url.equals(view.getTag())){view.setImageBitmap(image);view.setVisibility(View.VISIBLE);}});
            }catch(Exception unavailable){/* Optional media cannot authorize or block installation. */}
        });
    }
    private void homeFeed() {
        LinearLayout panel=activity.findViewById(R.id.homeFeed);
        try {
            JSONArray ids=follows.ids();
            String identity=ids.toString();if(!identity.equals(homeFeedIds)){homeRequest++;homeFeedAt=0;homeFeedIds=identity;homeReleases=new JSONArray();homeFeedKnown=false;renderActionCenter();}
            if(ids.length()==0){homeRequest++;homeFeedAt=0;homeReleases=new JSONArray();homeFeedKnown=true;panel.removeAllViews();panel.addView(textAction("Entwickler entdecken & folgen →",()->{developersOnly=true;githubRadar=false;following=false;activeProfile=null;started=false;navigate(1);}));renderActionCenter();return;}
            if(System.currentTimeMillis()-homeFeedAt<5*60*1000)return;
            homeFeedAt=System.currentTimeMillis();final int ticket=++homeRequest;
            panel.removeAllViews();panel.addView(label("Releases werden geladen …",12));
            network.execute(()->{
                try {
                    JSONArray feed=StoreClient.following(ids).getJSONArray("feed");if(feed.length()>60)throw new SecurityException("Release-Liste zu groß.");
                    prepareRadar(feed);activity.runOnUiThread(()->{if(closed||ticket!=homeRequest)return;homeReleases=feed;homeFeedKnown=true;renderActionCenter();panel.removeAllViews();
                        int shown=0;for(int i=0;i<feed.length()&&shown<2;i++){
                            JSONObject item=feed.optJSONObject(i);if(item==null)continue;
                            try {String slug=StoreClient.slug(item.getString("slug"));if(!readState.unseen(item)||radarState(item)==ReleaseRadar.State.INSTALLED)continue;shown++;
                                panel.addView(textAction(bounded(item.optString("name"),160)+" · "+bounded(item.optString("version"),120)
                                        +(readState.unseen(item)?" · Neu →":" →"),()->{
                                    try{readState.mark(new JSONArray().put(item));homeFeedAt=0;}catch(Exception e){error(e);}select.accept(slug);
                                }));
                            }catch(Exception invalid){}
                        }
                        if(shown==0){panel.addView(label("Keine ungesehenen Releases bei dieser Prüfung.",12));panel.addView(textAction("Alle Releases ansehen →",()->{githubRadar=false;activeProfile=null;following=true;newOnly=false;navigate(1);}));}
                    });
                }catch(Exception e){activity.runOnUiThread(()->{if(closed||ticket!=homeRequest)return;homeFeedAt=0;panel.removeAllViews();panel.addView(textAction("Releases nicht erreichbar · Erneut versuchen ↻",this::homeFeed));});}
            });
        }catch(Exception e){panel.removeAllViews();panel.addView(label("Gefolgte Entwickler konnten nicht gelesen werden.",12));}
    }
    private void renderActionCenter(){
        if(radar==null)return;LinearLayout panel=activity.findViewById(R.id.actionCenter);panel.removeAllViews();int releases=0;
        for(int i=0;i<homeReleases.length();i++){JSONObject item=homeReleases.optJSONObject(i);if(item!=null&&readState.unseen(item)&&radarState(item)!=ReleaseRadar.State.INSTALLED)releases++;}
        ((TextView)activity.findViewById(R.id.actionSummary)).setText(library.updateCount()+" Updates · "+(radar.hasChecked()?DisplayText.proposals(radar.proposalCount()):"APK-Prüfung ausstehend")+" · "+(homeFeedKnown?releases+" neue Releases":"Release-Prüfung ausstehend"));
        if(library.updateCount()>0)panel.addView(textAction(library.updateCount()+" Updates · jetzt prüfen →",()->{navigate(3);library.updateOverview();}));
        String prepared=DropPilot.preparedSlug(activity);if(prepared!=null)panel.addView(textAction("Vorbereitetes Update · Installation prüfen →",()->select.accept(prepared)));
        radar.homeActions(panel);
        int shown=0;for(int i=0;i<homeReleases.length()&&shown<2;i++){JSONObject item=homeReleases.optJSONObject(i);if(item==null||!readState.unseen(item)||radarState(item)==ReleaseRadar.State.INSTALLED)continue;shown++;panel.addView(textAction(item.optString("name")+" · "+DisplayText.version(item.optString("version"))+" · Release →",()->{try{readState.mark(new JSONArray().put(item));homeFeedAt=0;renderActionCenter();select.accept(StoreClient.slug(item.getString("slug")));}catch(Exception e){error(e);}}));}
        panel.addView(textAction("Release Radar öffnen →",()->{githubRadar=false;following=true;activeProfile=null;navigate(1);}));
    }
    private void manageFollows(){try{JSONObject saved=follows.read();java.util.List<String> ids=new java.util.ArrayList<>(),labels=new java.util.ArrayList<>();java.util.Iterator<String> it=saved.keys();while(it.hasNext()){String id=it.next();ids.add(id);labels.add(saved.getJSONObject(id).optString("name")+" · nicht mehr folgen");}new AlertDialog.Builder(activity).setTitle("Gefolgte Entwickler").setItems(labels.toArray(new String[0]),(d,w)->new AlertDialog.Builder(activity).setTitle("Nicht mehr folgen?").setNegativeButton("Abbrechen",null).setPositiveButton("Entfernen",(dialog,which)->{try{follows.remove(ids.get(w));homeFeedAt=0;feed();}catch(Exception e){error(e);}}).show()).setPositiveButton("Schließen",null).show();}catch(Exception e){error(e);}}
    private void renderCollections(){
        LinearLayout row=new LinearLayout(activity);row.setOrientation(LinearLayout.HORIZONTAL);
        for(String title:new String[]{"Frisch aktualisiert","Neu entdeckt","Entwickler"}){Button choice=button(title,()->{newDiscover=title.equals("Neu entdeckt");developersOnly=title.equals("Entwickler");page=1;catalog();});choice.setTextSize(11);choice.setPadding(dp(4),0,dp(4),0);choice.setSelected(developersOnly?title.equals("Entwickler"):newDiscover?title.equals("Neu entdeckt"):title.equals("Frisch aktualisiert"));choice.setBackgroundResource(R.drawable.bg_chip);choice.setTextColor(activity.getColor(choice.isSelected()?R.color.lime_dark:R.color.lime));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.rightMargin=dp(4);row.addView(choice,lp);}results.addView(row);
    }
    private void renderCatalogDevelopers(java.util.List<JSONObject> apps){
        results.addView(label("Entwickler entdecken",20));java.util.Set<String> seen=new java.util.HashSet<>();for(JSONObject app:apps){JSONObject developer=app.optJSONObject("developer");if(developer==null)continue;String handle=developer.optString("handle");if(!seen.add(handle))continue;results.addView(textAction(developer.optString("name")+" · @"+handle+" →",()->profile(handle)));}if(seen.isEmpty())results.addView(label("Hier gibt es noch keine öffentlichen Entwicklerprofile.",13));
    }
    private void hideKeyboard() {
        View focus=activity.getCurrentFocus();if(focus!=null){
            ((android.view.inputmethod.InputMethodManager)activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focus.getWindowToken(),0);focus.clearFocus();}
    }
    private void renderCategories() {
        LinearLayout chips=activity.findViewById(R.id.categoryChips);chips.removeAllViews();
        for(String value:new String[]{"","communication","productivity","tools","privacy","media","games","education","other"}) {
            Button chip=button(value.isEmpty()?"Alle":categoryName(value),()->{
                category=value;page=1;following=false;activeProfile=null;
                ((Button)activity.findViewById(R.id.discoverFilter)).setText(categoryName(value));renderCategories();catalog();
            });
            chip.setMinWidth(dp(48));chip.setMinimumWidth(dp(48));chip.setTextSize(12);chip.setSelected(category.equals(value));chip.setTextColor(activity.getColor(category.equals(value)?R.color.lime_dark:R.color.muted));
            chip.setBackgroundResource(R.drawable.bg_chip);chip.setPadding(dp(14),0,dp(14),0);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(48));lp.rightMargin=dp(8);chips.addView(chip,lp);
        }
    }
    private Button textAction(String value,Runnable action) {
        Button b=button(value,action);b.setBackgroundResource(android.R.color.transparent);b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);b.setPadding(0,0,0,0);return b;
    }
    private LinearLayout homeCard(AppLibrary.Entry entry) {
        LinearLayout c=card();c.setOrientation(LinearLayout.HORIZONTAL);c.setGravity(Gravity.CENTER_VERTICAL);c.setPadding(dp(14),dp(14),dp(14),dp(14));
        ImageView icon=icon(42);icon.setImageDrawable(library.appIcon(entry));c.addView(icon);
        LinearLayout lines=new LinearLayout(activity);lines.setOrientation(LinearLayout.VERTICAL);lines.setPadding(dp(12),0,0,0);
        TextView name=label(library.displayName(entry),16);name.setTypeface(null,android.graphics.Typeface.BOLD);name.setTextColor(activity.getColor(R.color.text));name.setPadding(0,0,0,dp(4));lines.addView(name);
        TextView state=label(library.homeVersion(entry)+" · "+library.homeEntryStatus(entry),12);state.setPadding(0,0,0,0);lines.addView(state);c.addView(lines,new LinearLayout.LayoutParams(0,-2,1));
        if(library.canOpen(entry)){Button open=button("Öffnen ↗",()->library.openApp(entry));open.setTextSize(12);open.setMinWidth(0);open.setMinimumWidth(0);open.setLayoutParams(new LinearLayout.LayoutParams(-2,dp(48)));c.addView(open);}else c.addView(label("→",18));c.setFocusable(true);c.setContentDescription(library.displayName(entry)+" · "+library.homeEntryStatus(entry)+" · Details öffnen");
        c.setOnClickListener(v->{v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);select.accept(entry.slug);});return c;
    }
    private void skeletons() {
        for(int i=0;i<2;i++){LinearLayout c=card();c.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            View bar=new View(activity);bar.setBackgroundColor(activity.getColor(R.color.surface_alt));c.addView(bar,new LinearLayout.LayoutParams(-1,dp(52)));results.addView(c);}
    }
    private void showScreenshot(String url) {
        ImageView full=new ImageView(activity);full.setAdjustViewBounds(true);full.setScaleType(ImageView.ScaleType.FIT_CENTER);full.setContentDescription("Screenshot vom Entwickler");
        loadImage(url,full);new AlertDialog.Builder(activity).setView(full).setPositiveButton("Schließen",null).show();
    }
    private interface Work {void run() throws Exception;}
    private void submit(Work work,int ticket){network.execute(()->{try{work.run();}catch(Exception e){post(ticket,()->{resultStatus.setText(message(e));results.addView(button("Erneut versuchen",()->{if(activeProfile!=null)profile(activeProfile);else if(following)feed();else catalog();}));});}});}
    private void post(int ticket,Runnable fn){activity.runOnUiThread(()->{if(!closed&&ticket==request&&!activity.isDestroyed())fn.run();});}
    private LinearLayout card(){LinearLayout c=new LinearLayout(activity);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(18),dp(18),dp(18),dp(18));c.setBackgroundResource(R.drawable.bg_card);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(14);c.setLayoutParams(p);return c;}
    private TextView label(String value,int size){TextView t=new TextView(activity);t.setText(value);t.setTextSize(size);t.setTextColor(activity.getColor(size>=20?R.color.text:R.color.muted));t.setPadding(0,dp(4),0,dp(4));if(size>=20)t.setTypeface(null,android.graphics.Typeface.BOLD);return t;}
    private Button button(String value,Runnable action){Button b=new Button(activity);b.setText(value);b.setTextSize(14);b.setAllCaps(false);b.setMinHeight(dp(48));b.setTextColor(activity.getColor(R.color.lime));b.setBackgroundResource(R.drawable.bg_compact);b.setOnClickListener(v->action.run());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(10);b.setLayoutParams(p);return b;}
    private ImageView icon(int size){ImageView v=new ImageView(activity);v.setImageResource(R.drawable.ic_apkdrop);v.setLayoutParams(new LinearLayout.LayoutParams(dp(size),dp(size)));v.setContentDescription("App-Icon");return v;}
    private void openWeb(String path){try{activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(StoreClient.ORIGIN+path)));}catch(Exception e){error(e);}}
    private void error(Exception e){Toast.makeText(activity,message(e),Toast.LENGTH_LONG).show();}
    private static String encode(String value){try{return URLEncoder.encode(value,"UTF-8");}catch(java.io.UnsupportedEncodingException impossible){throw new AssertionError(impossible);}}
    private static String bounded(String value,int max){return value==null?"":value.substring(0,Math.min(max,value.length()));}
    private static String message(Exception e){return e.getMessage()==null?"Verbindung fehlgeschlagen. Bitte erneut versuchen.":e.getMessage();}
    private int dp(int n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
}
