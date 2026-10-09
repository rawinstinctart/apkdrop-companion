package de.rawinstinctai.apkdrop;
import android.os.Bundle;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=26,qualifiers="w320dp-h640dp-xhdpi")
public class Alpha19ExperienceTest {
    @Test public void releaseTeaserKeepsOnlyTwoBackendHighlights(){assertEquals("Erste Änderung. · Zweite Änderung.",ReleaseTeaser.summary("Erste Änderung.\nZweite Änderung.\nDritte Änderung."));assertTrue(ReleaseTeaser.summary("x".repeat(800)).length()<=240);assertEquals("Neue Version verfügbar.",ReleaseTeaser.summary(""));}
    @Test public void narrowSettingsUseFullWidthBackupActions() {
        RuntimeEnvironment.setFontScale(1.8f);var controller=Robolectric.buildActivity(MainActivity.class).create();
        try{MainActivity a=controller.get();LinearLayout row=(LinearLayout)a.findViewById(R.id.backupExport).getParent();assertEquals(LinearLayout.VERTICAL,row.getOrientation());for(int id:new int[]{R.id.backupExport,R.id.backupImport})assertEquals(-1,a.findViewById(id).getLayoutParams().width);assertEquals(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE,a.findViewById(R.id.statusText).getAccessibilityLiveRegion());}
        finally{controller.destroy();RuntimeEnvironment.setFontScale(1f);}
    }
    @Test public void sharedReleaseNeedsExplicitPreviewAndSurvivesRotation()throws Exception {
        var controller=Robolectric.buildActivity(MainActivity.class).create();try{MainActivity a=controller.get();var field=MainActivity.class.getDeclaredField("store");field.setAccessible(true);StoreController store=(StoreController)field.get(a);String link="https://github.com/fixture-dev/new-app/releases/tag/v2.0";store.importGitHubRelease(link);Bundle saved=new Bundle();store.saveState(saved);assertEquals(link,saved.getString("sharedGithubRelease"));var rf=StoreController.class.getDeclaredField("radar");rf.setAccessible(true);GitHubRadarController radar=(GitHubRadarController)rf.get(store);assertFalse(radar.connected());assertEquals(link,radar.sharedRelease());assertTrue(((LinearLayout)a.findViewById(R.id.githubRadarPanel)).getChildCount()>0);assertThrows(IllegalArgumentException.class,()->store.importGitHubRelease("https://evil.test/release.apk"));}finally{controller.destroy();}
    }
}
