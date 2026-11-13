package com.newtube.mobile.ui.settings;

import static org.junit.Assert.*;
import android.app.Application;
import android.content.Context;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.newtube.mobile.audio.SwitchAudioOutput;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class GeneralSettingsTest {
    @org.junit.Before public void enablePhoneBehavior() {
        com.liskovsoft.smartyoutubetv2.common.misc.PhoneUi.setEnabled(true);
    }

    @org.junit.After public void restorePhoneGate() {
        com.liskovsoft.smartyoutubetv2.common.misc.PhoneUi.setEnabled(false);
    }

    @Test public void generalProvidesLinkSettingsAndOneGlobalSwitchToggle() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("switch_audio_output", 0).edit().clear().commit();
        SettingsPages.Page page = AppPages.general(context);
        SettingsRow switchRow = page.rows.stream().filter(row -> context.getString(
                R.string.mobile_settings_show_switch).contentEquals(row.title)).findFirst().get();
        SettingsRow links = page.rows.stream().filter(row -> context.getString(
                R.string.mobile_settings_youtube_links).contentEquals(row.title)).findFirst().get();
        assertEquals(SettingsRow.KIND_LINK, links.kind);
        assertEquals(SettingsPages.YOUTUBE_LINKS, links.page);
        assertTrue(switchRow.checked.getAsBoolean());
        switchRow.toggle.set(false);
        assertFalse(SwitchAudioOutput.isButtonShown(context));
        SettingsRow restored = AppPages.general(context).rows.stream().filter(row -> context.getString(
                R.string.mobile_settings_show_switch).contentEquals(row.title)).findFirst().get();
        assertFalse(restored.checked.getAsBoolean());
        restored.toggle.set(true);
    }

    @Test public void bothRepositoryActionsUseSalutWhileTheLicenceKeepsItsUpstreamSource() {
        Context context = RuntimeEnvironment.getApplication();
        assertEquals("https://github.com/akunilovskii/salut", context.getString(R.string.about_source_code_url));
        assertEquals(context.getString(R.string.about_source_code_url), context.getString(R.string.mobile_about_repo_url));
        assertTrue(context.getString(R.string.about_license_url).endsWith("/newtube/blob/main/LICENSE"));
    }
    @Test public void linkGuideShowsBothManualStepsAndAReadOnlyStatusCheck() {
        Context context = RuntimeEnvironment.getApplication();
        android.content.pm.PackageInfo youtube = new android.content.pm.PackageInfo();
        youtube.packageName = "com.google.android.youtube";
        youtube.applicationInfo = new android.content.pm.ApplicationInfo();
        youtube.applicationInfo.packageName = youtube.packageName;
        org.robolectric.Shadows.shadowOf(context.getPackageManager()).installPackage(youtube);
        SettingsPages.Page guide = SettingsPages.build(context, SettingsPages.YOUTUBE_LINKS);
        for (int title : new int[] {R.string.mobile_link_guide_youtube,
                R.string.mobile_link_guide_salut, R.string.mobile_link_guide_check}) {
            assertTrue(guide.rows.stream().anyMatch(row -> context.getString(title).contentEquals(row.title)
                    && row.kind == SettingsRow.KIND_LINK && row.action != null));
        }
    }

    @Test public void changingTheDefaultResolutionClearsTheVideoOverrideAndKeepsGlobalSpeed() {
        Context context = RuntimeEnvironment.getApplication();
        com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData preferences =
                com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData.instance(context);
        preferences.setSpeed(1.5f);
        preferences.setTempVideoFormat(preferences.getDefaultVideoFormat());
        SettingsRow choice = PlayerPages.quality(context).rows.stream().filter(row ->
                row.kind == SettingsRow.KIND_CHOICE && context.getString(R.string.mobile_settings_default_quality)
                        .contentEquals(row.title)).findFirst().get();
        SettingsRow.Option option = choice.options.stream().filter(item -> !"auto".equals(item.value)
                && !"other".equals(item.value)).findFirst().get();
        com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem expected = null;
        for (com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem.VideoPreset preset :
                com.liskovsoft.smartyoutubetv2.common.misc.AppDataSourceManager.instance().getVideoPresets()) {
            if (preset.name.equals(option.value)) expected = preset.format;
        }
        assertNotNull(expected);
        choice.pick.pick(option.value);
        assertNull(preferences.getTempVideoFormat());
        assertTrue(preferences.getFormat(com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem.TYPE_VIDEO).isPreset());
        assertEquals(expected, preferences.getFormat(com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem.TYPE_VIDEO));
        assertEquals(1.5f, preferences.getSpeed(), 0f);
    }
}
