package de.rawinstinctai.apkdrop;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import java.util.*;

final class UpdateNotifications {
    private static final String CHANNEL="apkdrop-updates";
    private UpdateNotifications() {}
    static boolean allowed(Context context) {
        NotificationManager manager=context.getSystemService(NotificationManager.class);
        if(manager==null || !manager.areNotificationsEnabled()) return false;
        if(Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) return false;
        NotificationChannel channel=manager.getNotificationChannel(CHANNEL);
        return channel==null || channel.getImportance()!=NotificationManager.IMPORTANCE_NONE;
    }
    static void post(Context context,AppLibrary.Entry entry,InstallContract release,InstallPolicy.Result result) {
        if(result.mode!=InstallPolicy.Mode.UPDATE || !allowed(context)) return;
        NotificationManager manager=context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,"APKDrop Updates",NotificationManager.IMPORTANCE_DEFAULT));
        SharedPreferences prefs=context.getSharedPreferences("apkdrop-notifications",Context.MODE_PRIVATE);
        String key=release.versionCode+":"+release.sha256;
        if(key.equals(prefs.getString(entry.slug,null))) return;
        Intent intent=new Intent(context,MainActivity.class).setAction(Intent.ACTION_VIEW)
                .setData(Uri.parse("https://apkdrop.rawinstinctai.de/install/"+entry.slug))
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent open=PendingIntent.getActivity(context,0,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        String message=result.sensitiveAdded.isEmpty()?"Neuer Release verfügbar. Tippe zum Prüfen.":"Neue sensible Berechtigungen. Tippe zum Prüfen.";
        Notification notification=new Notification.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(entry.name+" "+release.version+" ist verfügbar").setContentText(message)
                .setContentIntent(open).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).build();
        try { manager.notify(entry.slug,0,notification); prefs.edit().putString(entry.slug,key).apply(); }
        catch(SecurityException denied) { /* Permission can be revoked between check and post. Retry next check. */ }
    }
    static void remove(Context context,String slug) {
        NotificationManager manager=context.getSystemService(NotificationManager.class);
        if(manager!=null) manager.cancel(slug,0);
    }
    static void forget(Context context,String slug) {
        remove(context,slug);
        context.getSharedPreferences("apkdrop-notifications",Context.MODE_PRIVATE).edit().remove(slug).apply();
    }
}
