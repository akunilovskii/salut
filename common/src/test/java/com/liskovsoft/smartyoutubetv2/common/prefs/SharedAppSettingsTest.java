package com.liskovsoft.smartyoutubetv2.common.prefs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.service.SidebarService;
import com.liskovsoft.smartyoutubetv2.common.misc.PhoneUi;
import com.liskovsoft.smartyoutubetv2.common.misc.VideoDownloads;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@LooperMode(LooperMode.Mode.PAUSED)
public class SharedAppSettingsTest {
    private Application mContext;
    private AppPrefs mPrefs;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.getApplication();
        resetSingletons();
        AppPrefs.setSharedSettingsEnabled(false);
        PhoneUi.setEnabled(false);
        mContext.getSharedPreferences(mContext.getPackageName() + "_preferences", 0).edit().clear()
                .putString("last_profile_name", "Alice").putBoolean("multi_profiles", true).commit();
        mPrefs = AppPrefs.instance(mContext);
    }

    @After
    public void tearDown() {
        AppPrefs.setSharedSettingsEnabled(false);
        PhoneUi.setEnabled(false);
        resetSingletons();
    }

    @Test
    public void activeProfileBecomesTheSharedBaselineOnce() {
        for (String key : Arrays.asList("main_ui_data", "video_player_data", "video_player_tweaks_data", "blocked_channel_data")) {
            mPrefs.setData(key, "old-global");
            mPrefs.setData("Alice_" + key, "active-" + key);
            mPrefs.setData("Bob_" + key, "other-" + key);
        }
        startPhone();
        switchTo("Bob");
        assertEquals("active-main_ui_data", mPrefs.getProfileData("main_ui_data"));
        assertEquals("active-video_player_data", mPrefs.getProfileData("video_player_data"));
        assertEquals("active-video_player_tweaks_data", mPrefs.getProfileData("video_player_tweaks_data"));
        assertEquals("active-blocked_channel_data", mPrefs.getProfileData("blocked_channel_data"));
        mPrefs.setProfileData("main_ui_data", "new-shared");
        restartPrefs();
        switchTo(null);
        assertEquals("new-shared", mPrefs.getProfileData("main_ui_data"));
        mPrefs.enableMultiProfiles(true);
        assertFalse(mPrefs.isMultiProfilesEnabled());
        switchTo("Alice");
        assertEquals("new-shared", mPrefs.getProfileData("main_ui_data"));
        assertEquals("active-main_ui_data", mPrefs.getData("Alice_main_ui_data"));
    }

    @Test
    public void missingActivePreferenceUsesItsDefaultInsteadOfAnOldGlobalValue() {
        mPrefs.setData("main_ui_data", "stale-global");
        startPhone();
        assertEquals("", mPrefs.getProfileData("main_ui_data"));
    }

    @Test
    public void anExistingSharedConfigurationRemainsTheBaseline() {
        mPrefs.enableMultiProfiles(false);
        mPrefs.setData("main_ui_data", "existing-shared");
        mPrefs.setData("Alice_main_ui_data", "stale-profile");
        startPhone();
        switchTo("Bob");
        assertEquals("existing-shared", mPrefs.getProfileData("main_ui_data"));
    }

    @Test
    public void playlistAndFeedStateStayWithTheirAccounts() {
        mPrefs.setData("Alice_general_data", general("alice-setting", "alice-playlist", "alice-position"));
        mPrefs.setData("Bob_general_data", general("bob-setting", "bob-playlist", "bob-position"));
        startPhone();
        assertGeneral("alice-setting", "alice-playlist", "alice-position");
        switchTo("Bob");
        assertGeneral("alice-setting", "bob-playlist", "bob-position");
        mPrefs.setProfileData("general_data", general("updated-setting", "bob-new-playlist", "bob-new-position"));
        switchTo("Alice");
        assertGeneral("updated-setting", "alice-playlist", "alice-position");
        switchTo(null);
        assertGeneral("updated-setting", null, null);
        switchTo("Bob");
        assertGeneral("updated-setting", "bob-new-playlist", "bob-new-position");
        assertNull(Helpers.parseStr(Helpers.splitData(mPrefs.getData("general_data")), 13));
    }

    @Test
    public void oldGlobalAccountStateIsAssignedOnlyToTheInitiallyActiveAccount() {
        mPrefs.enableMultiProfiles(false);
        mPrefs.setData("general_data", general("shared", "alice-playlist", "alice-position"));
        startPhone();
        assertGeneral("shared", "alice-playlist", "alice-position");
        switchTo("Bob");
        assertGeneral("shared", null, null);
        switchTo(null);
        assertGeneral("shared", null, null);
        restartPrefs();
        switchTo("Alice");
        assertGeneral("shared", "alice-playlist", "alice-position");
    }

    @Test
    public void sidebarChoicesAreSharedWhilePinnedContentRemainsPrivate() {
        mPrefs.setData("Alice_sidebar_data", sidebar(MediaGroup.TYPE_HOME, "alice-channel"));
        mPrefs.setData("Bob_sidebar_data", sidebar(MediaGroup.TYPE_SUBSCRIPTIONS, "bob-channel"));
        startPhone();
        switchTo("Bob");
        assertSidebar(MediaGroup.TYPE_HOME, "bob-channel");
        mPrefs.setSidebarData(sidebar(MediaGroup.TYPE_SUBSCRIPTIONS, "bob-new-channel"));
        switchTo("Alice");
        assertSidebar(MediaGroup.TYPE_SUBSCRIPTIONS, "alice-channel");
        switchTo(null);
        assertSidebar(MediaGroup.TYPE_SUBSCRIPTIONS, null);
        assertFalse(mPrefs.getData("phone_shared_sidebar_data").contains("channel"));
    }

    @Test
    public void actualNavigationVisibilitySurvivesSwitchingAndRestart() {
        startPhone();
        SidebarService sidebar = SidebarService.instance(mContext);
        sidebar.enableSection(BrowseSection.WATCH_LATER_ID, false);
        sidebar.enableSection(VideoDownloads.SECTION_ID, false);
        switchTo("Bob");
        assertFalse(sidebar.isSectionPinned(BrowseSection.WATCH_LATER_ID));
        assertFalse(sidebar.isSectionPinned(VideoDownloads.SECTION_ID));
        sidebar.enableSection(BrowseSection.WATCH_LATER_ID, true);
        switchTo(null);
        assertTrue(sidebar.isSectionPinned(BrowseSection.WATCH_LATER_ID));
        assertFalse(sidebar.isSectionPinned(VideoDownloads.SECTION_ID));
        resetSingletons();
        mPrefs = AppPrefs.instance(mContext);
        sidebar = SidebarService.instance(mContext);
        assertTrue(sidebar.isSectionPinned(BrowseSection.WATCH_LATER_ID));
        assertFalse(sidebar.isSectionPinned(VideoDownloads.SECTION_ID));
    }

    @Test
    public void switchingImmediatelyFlushesDelayedSettingsAndOldAccountState() {
        startPhone();
        GeneralData general = GeneralData.instance(mContext);
        PlayerData player = PlayerData.instance(mContext);
        general.setLastPlaylistId("alice-playlist");
        player.setUiHideTimeoutSec(17);
        switchTo("Bob");
        assertEquals(17, player.getUiHideTimeoutSec());
        assertNull(general.getLastPlaylistId());
        general.setLastPlaylistId("bob-playlist");
        player.setUiHideTimeoutSec(23);
        switchTo("Alice");
        assertEquals(23, player.getUiHideTimeoutSec());
        assertEquals("alice-playlist", general.getLastPlaylistId());
        switchTo("Bob");
        assertEquals("bob-playlist", general.getLastPlaylistId());
    }

    @Test
    public void appearanceTweaksAndLocalFiltersSurviveAnImmediateAccountSwitch() {
        startPhone();
        MainUIData ui = MainUIData.instance(mContext);
        PlayerTweaksData tweaks = PlayerTweaksData.instance(mContext);
        BlockedChannelData blocked = BlockedChannelData.instance(mContext);
        ui.setCardMultilineTitleEnabled(false);
        tweaks.setPreferredDnsType(PlayerTweaksData.DNS_TYPE_GOOGLE);
        blocked.addChannel("test-channel", "Test channel");
        switchTo("Bob");
        assertFalse(ui.isCardMultilineTitleEnabled());
        assertEquals(PlayerTweaksData.DNS_TYPE_GOOGLE, tweaks.getPreferredDnsType());
        assertTrue(blocked.containsChannel("test-channel", "Test channel"));
        ui.setCardMultilineTitleEnabled(true);
        blocked.removeChannel("test-channel", "Test channel");
        switchTo(null);
        assertTrue(ui.isCardMultilineTitleEnabled());
        assertFalse(blocked.containsChannel("test-channel", "Test channel"));
    }

    @Test
    public void firstLaunchWhileSignedOutKeepsGuestContentSeparate() {
        switchTo(null);
        mPrefs.setProfileData("general_data", general("guest-setting", "guest-playlist", "guest-position"));
        startPhone();
        assertGeneral("guest-setting", "guest-playlist", "guest-position");
        switchTo("Alice");
        assertGeneral("guest-setting", null, null);
        switchTo(null);
        assertGeneral("guest-setting", "guest-playlist", "guest-position");
    }

    @Test
    public void localWatchProgressAndChannelGroupsStaySeparate() {
        startPhone();
        mPrefs.setStateUpdaterData("alice-watch-progress");
        mPrefs.setChannelGroupData("alice-channel-group");
        switchTo("Bob");
        assertNull(mPrefs.getStateUpdaterData());
        assertNull(mPrefs.getChannelGroupData());
        mPrefs.setStateUpdaterData("bob-watch-progress");
        switchTo("Alice");
        assertEquals("alice-watch-progress", mPrefs.getStateUpdaterData());
        assertEquals("alice-channel-group", mPrefs.getChannelGroupData());
    }

    @Test
    public void theTvKeepsItsExistingProfileModeAndSidebar() {
        mPrefs.setProfileData("main_ui_data", "alice-settings");
        mPrefs.setSidebarData(sidebar(MediaGroup.TYPE_HOME, "alice-channel"));
        switchTo("Bob");
        mPrefs.setProfileData("main_ui_data", "bob-settings");
        mPrefs.setSidebarData(sidebar(MediaGroup.TYPE_SUBSCRIPTIONS, "bob-channel"));
        switchTo("Alice");
        assertEquals("alice-settings", mPrefs.getProfileData("main_ui_data"));
        assertSidebar(MediaGroup.TYPE_HOME, "alice-channel");
        assertTrue(mPrefs.isMultiProfilesEnabled());
        assertFalse(mPrefs.getBoolean("phone_shared_settings_v1", false));
    }

    private void startPhone() {
        PhoneUi.setEnabled(true);
        AppPrefs.setSharedSettingsEnabled(true);
        restartPrefs();
    }

    private void restartPrefs() {
        ReflectionHelpers.setStaticField(AppPrefs.class, "sInstance", null);
        mPrefs = AppPrefs.instance(mContext);
    }

    private void switchTo(String name) {
        Account account = name == null ? null : (Account) Proxy.newProxyInstance(Account.class.getClassLoader(),
                new Class<?>[] {Account.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getName")) {
                        return name;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        mPrefs.onAccountChanged(account);
    }

    private static String general(String setting, String playlist, String position) {
        Object[] values = new Object[74];
        values[3] = setting;
        for (int index : new int[] {6, 28, 29, 30}) {
            values[index] = playlist == null ? null : playlist + "-field-" + index;
        }
        values[13] = playlist;
        values[63] = position;
        values[73] = "future-field";
        return Helpers.mergeData(values);
    }

    private void assertGeneral(String setting, String playlist, String position) {
        String[] data = Helpers.splitData(mPrefs.getProfileData("general_data"));
        assertEquals(setting, Helpers.parseStr(data, 3));
        assertEquals(playlist, Helpers.parseStr(data, 13));
        assertEquals(position, Helpers.parseStr(data, 63));
        for (int index : new int[] {6, 28, 29, 30}) {
            assertEquals(playlist == null ? null : playlist + "-field-" + index, Helpers.parseStr(data, index));
        }
        assertEquals("future-field", Helpers.parseStr(data, 73));
    }

    private static String sidebar(int section, String channel) {
        Video nativeSection = new Video();
        nativeSection.sectionId = section;
        Video pin = new Video();
        pin.channelId = channel;
        return Helpers.mergeData(Arrays.asList(nativeSection, pin), section, true, true);
    }

    private void assertSidebar(int section, String channel) {
        String[] data = Helpers.splitData(mPrefs.getSidebarData());
        List<Video> items = Helpers.parseList(data, 0, Video::fromString);
        assertEquals(channel == null ? 1 : 2, items.size());
        assertEquals(section, items.get(0).sectionId);
        assertEquals(section, Helpers.parseInt(data, 1, -1));
        if (channel != null) {
            assertEquals(channel, items.get(1).channelId);
        }
    }

    private static void resetSingletons() {
        for (Class<?> type : Arrays.asList(AppPrefs.class, GeneralData.class, PlayerData.class,
                MainUIData.class, PlayerTweaksData.class, BlockedChannelData.class, SidebarService.class)) {
            ReflectionHelpers.setStaticField(type, "sInstance", null);
        }
    }
}
