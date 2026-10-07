package de.rawinstinctai.apkdrop;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.widget.*;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/** Renders only explicitly saved apps; never scans the installed-app inventory. */
final class AppLibraryController {
    private final Activity activity;
    private final ExecutorService io;
    private final Consumer<String> select;
    private final Runnable changed;
    private final AppLibraryStore store;
    private final LinearLayout list;
    private final TextView summary,empty;
    private final Button checkAll;
    private final Map<String,State> states=new LinkedHashMap<>();
    private final Map<String,Drawable> icons=new HashMap<>();
    private volatile AppLibrary library=new AppLibrary();
    private volatile long generation;
    private boolean detailBusy,checking,readable=true,refreshPending;
    private String listError="";

    private static final class State {
        final InstalledState installed;
        final InstallContract release;
        final InstallPolicy.Result decision;
        final String error;
        final boolean blocked;
        final long checkedAt;
        State(InstalledState installed,InstallContract release,InstallPolicy.Result decision,String error,boolean blocked,long checkedAt) {
            this.installed=installed; this.release=release; this.decision=decision;
            this.error=error; this.blocked=blocked; this.checkedAt=checkedAt;
        }
    }

    AppLibraryController(Activity activity,ExecutorService io,Consumer<String> select,Runnable changed) {
        this.activity=activity; this.io=io; this.select=select; this.changed=changed;
        store=new AppLibraryStore(activity);
        list=activity.findViewById(R.id.libraryList); summary=activity.findViewById(R.id.librarySummary);
        empty=activity.findViewById(R.id.libraryEmpty); checkAll=activity.findViewById(R.id.checkAllButton);
        try { library=store.read(); }
        catch(Exception e) { readable=false; listError="Deine gespeicherte App-Liste konnte nicht gelesen werden. Einzelne Releases kannst du weiterhin prüfen."; }
        checkAll.setOnClickListener(v->{ if(checking) cancelChecks(); else checkAll(); });
        render();
    }

    AppLibrary.Entry find(String slug) { return library.find(slug); }
    boolean tracksPackage(String packageName) {
        for(AppLibrary.Entry entry:library.entries()) if(entry.packageName.equals(packageName)) return true;
        return false;
    }
    boolean writable() { return readable; }
    boolean checking() { return checking; }
    String pendingInstaller() { return store.pendingInstaller(); }
    void pendingInstaller(String slug) { store.pendingInstaller(slug); }
    void clearPendingInstaller() { store.clearPendingInstaller(); }

    void add(InstallContract release,InstalledState installed,InstallPolicy.Result decision) throws Exception {
        if(!readable) throw new IllegalStateException("Die lokale App-Liste kann gerade nicht gespeichert werden.");
        if(detailBusy || checking) return;
        AppLibrary next=library.add(new AppLibrary.Entry(release.slug,release.appName,release.packageName,release.signers));
        store.save(next); library=next; generation++;
        states.put(release.slug,new State(installed,release,decision,null,false,System.currentTimeMillis()));
        render();
    }

    void rememberChecked(InstallContract release,InstalledState installed,InstallPolicy.Result decision) {
        AppLibrary.Entry entry=library.find(release.slug);
        if(entry==null) return;
        entry.requireIdentity(release.slug,release.packageName,release.signers);
        states.put(entry.slug,new State(installed,release,decision,null,false,System.currentTimeMillis()));
        render();
    }

    void setDetailBusy(boolean busy) {
        detailBusy=busy;
        if(busy) { generation++; checking=false; }
        render();
        if(!busy && refreshPending) { refreshPending=false; refreshInstalled(); }
    }

    void cancelChecks() { generation++; checking=false; render(); }
    void close() { generation++; }

    void refreshInstalled() {
        if(detailBusy || checking) { refreshPending=true; return; }
        icons.clear();
        final long ticket=++generation;
        final List<AppLibrary.Entry> entries=library.entries();
        final Map<String,State> previous=new LinkedHashMap<>(states);
        io.execute(()->{
            for(AppLibrary.Entry entry:entries) {
                if(ticket!=generation || Thread.currentThread().isInterrupted()) return;
                State old=previous.get(entry.slug);
                State fresh;
                try {
                    InstalledState installed=InstalledState.read(activity,entry.packageName);
                    if(old!=null && old.release!=null) {
                        entry.requireIdentity(old.release.slug,old.release.packageName,old.release.signers);
                        fresh=new State(installed,old.release,evaluate(old.release,installed),null,false,old.checkedAt);
                    } else fresh=new State(installed,null,null,old==null?null:old.error,old!=null&&old.blocked,old==null?0:old.checkedAt);
                } catch(Exception e) { fresh=new State(null,null,null,message(e),true,0); }
                final State value=fresh;
                post(ticket,()->{ if(library.find(entry.slug)==entry) { states.put(entry.slug,value); render(); } });
            }
        });
    }

    private void checkAll() {
        if(detailBusy || !readable || library.entries().isEmpty()) return;
        final long ticket=++generation;
        final List<AppLibrary.Entry> entries=library.entries();
        checking=true; render();
        io.execute(()->{
            int index=0;
            for(AppLibrary.Entry entry:entries) {
                if(ticket!=generation || Thread.currentThread().isInterrupted()) return;
                final int position=++index;
                post(ticket,()->summary.setText("Prüfe "+position+" von "+entries.size()+" · "+entry.name));
                InstalledState installed=null;
                State state;
                try {
                    installed=InstalledState.read(activity,entry.packageName);
                    InstallContract release=ContractClient.fetch(entry.slug);
                    entry.requireIdentity(release.slug,release.packageName,release.signers);
                    state=new State(installed,release,evaluate(release,installed),null,false,System.currentTimeMillis());
                } catch(Exception e) {
                    state=new State(installed,null,null,message(e),e instanceof SecurityException,System.currentTimeMillis());
                }
                final State value=state;
                post(ticket,()->{
                    if(library.find(entry.slug)==entry) states.put(entry.slug,value);
                    render(); summary.setText("Prüfe "+position+" von "+entries.size()+" · "+entry.name);
                });
            }
            post(ticket,()->{
                checking=false; render();
                if(refreshPending) { refreshPending=false; refreshInstalled(); }
            });
        });
    }

    private InstallPolicy.Result evaluate(InstallContract release,InstalledState installed) {
        return InstallPolicy.evaluate(release,installed,Build.VERSION.SDK_INT,Build.SUPPORTED_ABIS);
    }

    private void render() {
        list.removeAllViews();
        int updates=0,current=0,unknown=0;
        for(AppLibrary.Entry entry:library.entries()) {
            State state=states.get(entry.slug);
            if(state==null || state.decision==null) unknown++;
            else if(state.decision.mode==InstallPolicy.Mode.UPDATE) updates++;
            else if(state.decision.mode==InstallPolicy.Mode.CURRENT) current++;
            View row=activity.getLayoutInflater().inflate(R.layout.item_tracked_app,list,false);
            ImageView icon=row.findViewById(R.id.trackedIcon);
            TextView name=row.findViewById(R.id.trackedName),version=row.findViewById(R.id.trackedVersion);
            TextView badge=row.findViewById(R.id.trackedBadge),detail=row.findViewById(R.id.trackedDetail);
            name.setText(state!=null&&state.release!=null?state.release.appName:entry.name);
            Drawable image;
            image=icons.get(entry.packageName);
            if(image==null) {
                try { image=activity.getPackageManager().getApplicationIcon(entry.packageName); }
                catch(Exception missing) { image=activity.getDrawable(android.R.drawable.sym_def_app_icon); }
                icons.put(entry.packageName,image);
            }
            icon.setImageDrawable(image); icon.setContentDescription(entry.name);
            version.setText(state==null?"Installationsstand wird geladen …":state.installed!=null
                    ? "Installiert: "+installedVersion(state.installed):state.error!=null?"Installationsstand nicht verfügbar":"Nicht installiert");
            badge.setTextColor(activity.getColor(R.color.muted));
            String reason;
            if(state==null) { badge.setText("NOCH NICHT GEPRÜFT"); reason="Tippe auf Alle prüfen oder öffne die App-Details."; }
            else if(state.error!=null) {
                badge.setText(state.blocked?"BLOCKIERT":"PRÜFUNG FEHLGESCHLAGEN");
                badge.setTextColor(activity.getColor(R.color.danger)); reason=state.error;
            } else if(state.decision==null) { badge.setText("NOCH NICHT GEPRÜFT"); reason="Der installierte Stand ist erfasst. Prüfe jetzt den verfügbaren Release."; }
            else {
                switch(state.decision.mode) {
                    case UPDATE -> badge.setText("UPDATE VERFÜGBAR");
                    case CURRENT -> badge.setText("AKTUELL");
                    case INSTALL -> badge.setText("NICHT INSTALLIERT");
                    case BLOCKED -> badge.setText("BLOCKIERT");
                }
                badge.setTextColor(activity.getColor(state.decision.mode==InstallPolicy.Mode.BLOCKED?R.color.danger:R.color.lime));
                reason="Verfügbar: v"+state.release.version+" · "+state.decision.reason;
                if(state.checkedAt>0) reason+="\nZuletzt geprüft: "+DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(state.checkedAt));
            }
            detail.setText(reason);
            Button open=row.findViewById(R.id.trackedOpen); open.setEnabled(!detailBusy&&!checking);
            open.setOnClickListener(v->select.accept(entry.slug));
            Button menu=row.findViewById(R.id.trackedMenu); menu.setEnabled(!detailBusy&&!checking);
            final State selected=state;
            menu.setOnClickListener(v->menu(entry,selected,menu));
            list.addView(row);
        }
        int count=library.entries().size();
        empty.setVisibility(count==0?View.VISIBLE:View.GONE);
        empty.setText(readable?"Noch keine Apps gespeichert. Prüfe unten einen APKDrop-Link und tippe auf Zu meinen Apps hinzufügen.":listError);
        String text=count+" "+(count==1?"App":"Apps");
        if(updates>0) text+=" · "+updates+" "+(updates==1?"Update verfügbar":"Updates verfügbar");
        else if(count>0 && current==count) text+=" · Alle Apps aktuell";
        else if(unknown>0) text+=" · "+unknown+" noch zu prüfen";
        if(!readable) text="App-Liste nicht verfügbar";
        if(!checking) summary.setText(text);
        checkAll.setText(checking?"Prüfung abbrechen":"Alle auf Updates prüfen →");
        checkAll.setEnabled(!detailBusy&&readable&&count>0);
        changed.run();
    }

    private void menu(AppLibrary.Entry entry,State state,View anchor) {
        PopupMenu menu=new PopupMenu(activity,anchor);
        if(state!=null && state.installed!=null) menu.getMenu().add("App öffnen").setOnMenuItemClickListener(item->{
            try {
                Intent intent=activity.getPackageManager().getLaunchIntentForPackage(entry.packageName);
                if(intent!=null) activity.startActivity(intent);
                else Toast.makeText(activity,"Diese App hat keinen Startbildschirm.",Toast.LENGTH_SHORT).show();
            } catch(Exception e) { Toast.makeText(activity,message(e),Toast.LENGTH_LONG).show(); }
            return true;
        });
        menu.getMenu().add("Aus Meine Apps entfernen").setOnMenuItemClickListener(item->{
            new AlertDialog.Builder(activity).setTitle(entry.name+" entfernen?")
                    .setMessage("Die App wird aus deiner APKDrop-Liste entfernt. Sie bleibt auf deinem Gerät installiert.")
                    .setNegativeButton("Abbrechen",null).setPositiveButton("Entfernen",(dialog,which)->{
                        if(detailBusy || checking) return;
                        try {
                            AppLibrary next=library.remove(entry.slug); store.save(next); library=next;
                            generation++; states.remove(entry.slug); render();
                        } catch(Exception e) { Toast.makeText(activity,message(e),Toast.LENGTH_LONG).show(); }
                    }).show();
            return true;
        });
        menu.show();
    }

    private void post(long ticket,Runnable callback) {
        activity.runOnUiThread(()->{
            if(ticket==generation&&!activity.isFinishing()&&!activity.isDestroyed()) callback.run();
        });
    }

    static String installedVersion(InstalledState state) {
        return state.versionName==null||state.versionName.trim().isEmpty() ? "Build "+state.versionCode : "v"+state.versionName;
    }
    private static String message(Exception e) { return e.getMessage()==null?"Prüfung fehlgeschlagen. Bitte erneut versuchen.":e.getMessage(); }
}
