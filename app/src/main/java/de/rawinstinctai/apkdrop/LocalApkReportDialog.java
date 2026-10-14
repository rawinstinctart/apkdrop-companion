package de.rawinstinctai.apkdrop;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Compact, accessible APK evidence display. Independent DropPass checks are NOT part of this UI. */
final class LocalApkReportDialog {
    private LocalApkReportDialog() {}

    static AlertDialog create(Activity activity,LocalApkInspector.Report report) {
        int text=activity.getColor(R.color.text),muted=activity.getColor(R.color.muted);
        int good=activity.getColor(R.color.lime),warn=activity.getColor(R.color.warn);
        ScrollView scroll=new ScrollView(activity);
        scroll.setFillViewport(false);
        LinearLayout body=new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(activity,20),dp(activity,8),dp(activity,20),dp(activity,8));
        scroll.addView(body);

        TextView packageLabel=label(activity,report.packageName,text,15,true);
        packageLabel.setId(R.id.localApkPackage);
        body.addView(packageLabel);
        if(report.versionName!=null && !report.versionName.isBlank()) {
            TextView version=label(activity,report.versionName,muted,12,false);
            version.setPadding(0,0,0,dp(activity,12));
            body.addView(version);
        }

        LinearLayout summary=new LinearLayout(activity);
        summary.setOrientation(LinearLayout.VERTICAL);
        summary.setPadding(dp(activity,14),dp(activity,12),dp(activity,14),dp(activity,12));
        GradientDrawable surface=new GradientDrawable();
        surface.setColor(activity.getColor(R.color.surface_alt));
        surface.setCornerRadius(dp(activity,14));
        summary.setBackground(surface);
        body.addView(summary,new LinearLayout.LayoutParams(-1,-2));

        row(activity,summary,"APK-Signatur","Gültig",good);
        switch(report.signerComparison) {
            case MATCH:
                row(activity,summary,"Installierte Signatur","Stimmt überein",good);
                break;
            case MISMATCH:
                row(activity,summary,"Installierte Signatur","⚠ Abweichung",warn);
                break;
            default:
                row(activity,summary,"Installierte Signatur","Nicht vergleichbar",muted);
        }
        String permissions=report.addedPermissions<0?"Nicht vergleichbar":String.valueOf(report.addedPermissions);
        row(activity,summary,"Neue Berechtigungen",permissions,report.addedPermissions>0?warn:text);
        row(activity,summary,"DropPass-Herkunft","Nicht geprüft",muted);

        if(report.signerComparison==LocalApkInspector.SignerComparison.MISMATCH) {
            TextView warning=label(activity,"⚠ Die Signatur weicht von der installierten App ab. Nicht als normales Update verwenden.",warn,14,true);
            warning.setId(R.id.localApkWarning);
            warning.setPadding(0,dp(activity,14),0,0);
            body.addView(warning);
        }

        TextView note=label(activity,
                "Nur lokal geprüft. Kein Malware-Scan, keine bestätigte Herkunft und keine Installationsfreigabe.",
                muted,12,false);
        note.setPadding(0,dp(activity,14),0,dp(activity,6));
        body.addView(note);

        TextView detail=label(activity,report.text,text,12,false);
        detail.setId(R.id.localApkDetails);
        detail.setTextIsSelectable(true);
        detail.setVisibility(View.GONE);
        detail.setPadding(dp(activity,12),dp(activity,12),dp(activity,12),dp(activity,12));
        GradientDrawable panel=new GradientDrawable();
        panel.setColor(activity.getColor(R.color.bg));
        panel.setCornerRadius(dp(activity,10));
        detail.setBackground(panel);

        Button toggle=new Button(activity);
        toggle.setId(R.id.localApkDetailsToggle);
        toggle.setAllCaps(false);
        toggle.setText("Technische Details anzeigen +");
        toggle.setTextColor(good);
        toggle.setMinHeight(dp(activity,48));
        toggle.setOnClickListener(v->{
            boolean show=detail.getVisibility()!=View.VISIBLE;
            detail.setVisibility(show?View.VISIBLE:View.GONE);
            toggle.setText(show?"Technische Details ausblenden −":"Technische Details anzeigen +");
        });
        body.addView(toggle,new LinearLayout.LayoutParams(-1,-2));
        body.addView(detail,new LinearLayout.LayoutParams(-1,-2));
        return new AlertDialog.Builder(activity)
                .setTitle("APK-Datei geprüft")
                .setView(scroll)
                .setPositiveButton("Schließen",null)
                .create();
    }

    private static TextView label(Activity activity,String value,int color,int sp,boolean strong) {
        TextView view=new TextView(activity);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(sp);
        view.setGravity(Gravity.CENTER_VERTICAL);
        if(strong)view.setTypeface(null,Typeface.BOLD);
        return view;
    }

    private static void row(Activity activity,LinearLayout body,String name,String value,int valueColor) {
        LinearLayout line=new LinearLayout(activity);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(0,dp(activity,7),0,dp(activity,7));
        TextView left=label(activity,name,activity.getColor(R.color.text),13,false);
        line.addView(left,new LinearLayout.LayoutParams(0,-2,1));
        TextView right=label(activity,value,valueColor,13,true);
        right.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        right.setPadding(dp(activity,8),0,0,0);
        line.addView(right,new LinearLayout.LayoutParams(-2,-2));
        body.addView(line,new LinearLayout.LayoutParams(-1,-2));
    }
    private static int dp(Activity activity,int value) {
        return (int)(value*activity.getResources().getDisplayMetrics().density+0.5f);
    }
}
