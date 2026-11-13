package com.newtube.mobile.ui.common;

import static org.junit.Assert.*;
import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.provider.Settings;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class YouTubeLinkSetupTest {
    @Test public void everyHostMustBeVerifiedOrExplicitlySelected() {
        Map<String, Integer> states = new HashMap<>();
        for (String host : YouTubeLinkSetup.HOSTS) states.put(host, 1);
        assertTrue(YouTubeLinkSetup.approved(true, states));
        for (String host : YouTubeLinkSetup.HOSTS) {
            states.put(host, 2);
            assertTrue(YouTubeLinkSetup.approved(true, states));
            states.put(host, 0);
            assertFalse(YouTubeLinkSetup.approved(true, states));
            states.remove(host);
            assertFalse(YouTubeLinkSetup.approved(true, states));
            states.put(host, 1);
        }
        assertFalse(YouTubeLinkSetup.approved(false, states));
        assertFalse(YouTubeLinkSetup.approved(true, null));
    }

    @Test public void oldAndroidOpensItsExistingApplicationSettings() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Intent intent = YouTubeLinkSetup.settingsIntent(activity);
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.getAction());
        assertEquals("package:" + activity.getPackageName(), intent.getDataString());
        activity.finish();
    }

    @Test @Config(sdk = 33)
    public void modernAndroidOpensThisAppsDomainSelection() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Intent intent = YouTubeLinkSetup.settingsIntent(activity);
        assertEquals(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, intent.getAction());
        assertEquals("package:" + activity.getPackageName(), intent.getDataString());
        activity.finish();
    }
    public static class FocusedActivity extends Activity {
        @Override public boolean hasWindowFocus() { return true; }
    }

    @Test public void dismissingTheOfferDoesNotRepeatUntilAnotherInstallationUpdate() throws Exception {
        FocusedActivity activity = Robolectric.buildActivity(FocusedActivity.class).setup().get();
        activity.setTheme(com.liskovsoft.smartyoutubetv2.tv.R.style.Theme_NewTube);
        android.content.pm.PackageInfo installed = new android.content.pm.PackageInfo();
        installed.packageName = activity.getPackageName();
        installed.lastUpdateTime = 123;
        org.robolectric.Shadows.shadowOf(activity.getPackageManager()).installPackage(installed);
        assertTrue(activity.hasWindowFocus());
        assertFalse(activity.isFinishing());
        assertFalse(activity.isDestroyed());
        assertFalse(activity.isInPictureInPictureMode());
        assertFalse(YouTubeLinkSetup.allLinksApproved(activity));
        activity.getSharedPreferences("salut_link_setup", 0).edit().clear().commit();
        YouTubeLinkSetup.maybeShow(activity);
        android.app.Dialog first = org.robolectric.shadows.ShadowDialog.getLatestDialog();
        assertNotNull(first);
        assertTrue(first.isShowing());
        android.widget.Button action = ((androidx.appcompat.app.AlertDialog) first)
                .getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
        assertTrue(action instanceof com.google.android.material.button.MaterialButton);
        android.content.res.ColorStateList tint =
                ((com.google.android.material.button.MaterialButton) action).getBackgroundTintList();
        assertNotNull(tint);
        com.google.android.material.button.MaterialButton cancel =
                (com.google.android.material.button.MaterialButton) ((androidx.appcompat.app.AlertDialog) first)
                        .getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE);
        assertEquals(cancel.getBackgroundTintList().getDefaultColor(), tint.getDefaultColor());
        assertTrue("The settings action uses the same restrained text-button surface as Cancel",
                android.graphics.Color.alpha(tint.getDefaultColor()) <= 20);
        first.dismiss();
        YouTubeLinkSetup.maybeShow(activity);
        assertSame(first, org.robolectric.shadows.ShadowDialog.getLatestDialog());
        assertFalse(first.isShowing());
        android.content.pm.PackageInfo info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
        info.lastUpdateTime += 1000;
        org.robolectric.Shadows.shadowOf(activity.getPackageManager()).installPackage(info);
        YouTubeLinkSetup.maybeShow(activity);
        android.app.Dialog second = org.robolectric.shadows.ShadowDialog.getLatestDialog();
        assertNotSame(first, second);
        assertTrue(second.isShowing());
        second.dismiss();
        activity.finish();
    }
    @Test public void packageQueriesAreInstallableAndCoverEveryWebHostAndScheme() throws Exception {
        java.nio.file.Path path = java.nio.file.Paths.get("src/stmobile/AndroidManifest.xml");
        if (!java.nio.file.Files.exists(path)) path = java.nio.file.Paths.get("smarttubetv").resolve(path);
        org.w3c.dom.Document manifest = javax.xml.parsers.DocumentBuilderFactory.newInstance()
                .newDocumentBuilder().parse(path.toFile());
        org.w3c.dom.NodeList queries = manifest.getElementsByTagName("queries");
        java.util.Set<String> covered = new java.util.HashSet<>();
        for (int q = 0; q < queries.getLength(); q++) {
            org.w3c.dom.NodeList intents = ((org.w3c.dom.Element) queries.item(q)).getElementsByTagName("intent");
            for (int i = 0; i < intents.getLength(); i++) {
                org.w3c.dom.NodeList data = ((org.w3c.dom.Element) intents.item(i)).getElementsByTagName("data");
                int schemes = 0;
                for (int j = 0; j < data.getLength(); j++) {
                    org.w3c.dom.Element item = (org.w3c.dom.Element) data.item(j);
                    String scheme = item.getAttribute("android:scheme");
                    if (!scheme.isEmpty()) schemes++;
                    if (scheme.equals("http") || scheme.equals("https")) {
                        covered.add(scheme + "://" + item.getAttribute("android:host"));
                    }
                }
                assertTrue("Android permits at most one scheme in each package-query intent", schemes <= 1);
            }
        }
        for (String host : YouTubeLinkSetup.HOSTS) {
            assertTrue(covered.contains("http://" + host));
            assertTrue(covered.contains("https://" + host));
        }
    }
    @Test public void setupActionsOnlyOpenTheGuideAndTheRequestedSystemPage() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        YouTubeLinkSetup.openGuide(activity);
        Intent guide = org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(com.newtube.mobile.ui.settings.MobileSettingsActivity.class.getName(),
                guide.getComponent().getClassName());
        assertEquals(com.newtube.mobile.ui.settings.SettingsPages.YOUTUBE_LINKS,
                guide.getStringExtra(com.newtube.mobile.ui.settings.MobileSettingsActivity.EXTRA_PAGE));
        YouTubeLinkSetup.openYouTubeSettings(activity);
        Intent youtube = org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals("package:com.google.android.youtube", youtube.getDataString());
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, youtube.getAction());
        YouTubeLinkSetup.openSettings(activity);
        Intent salut = org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals("package:" + activity.getPackageName(), salut.getDataString());
        activity.finish();
    }
}
