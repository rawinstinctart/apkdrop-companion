package de.rawinstinctai.apkdrop;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import java.io.File;

final class InstallerHandoff {
    private InstallerHandoff() {}

    static boolean open(Activity activity,File verifiedApk) {
        if(Build.VERSION.SDK_INT>=26 && !activity.getPackageManager().canRequestPackageInstalls()) {
            Intent settings=new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:"+activity.getPackageName()));
            activity.startActivity(settings);
            return false;
        }

        Uri uri=new Uri.Builder()
                .scheme("content")
                .authority(activity.getPackageName()+".files")
                .path("verified.apk")
                .build();

        Intent intent=new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri,"application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivity(intent);
        return true;
    }
}
