package com.liskovsoft.smartyoutubetv2.common.app.presenters.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.misc.PhoneUi;
import com.liskovsoft.smartyoutubetv2.common.misc.VideoDownloads;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.util.ReflectionHelpers;

import java.util.Arrays;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class SidebarServiceWatchLaterTest {
    private SidebarService sidebar;

    @Before
    public void setUp() {
        PhoneUi.setEnabled(false);
        ReflectionHelpers.setStaticField(SidebarService.class, "sInstance", null);
        Video home = new Video();
        home.sectionId = MediaGroup.TYPE_HOME;
        Video downloads = new Video();
        downloads.sectionId = VideoDownloads.SECTION_ID;
        AppPrefs.instance(RuntimeEnvironment.getApplication()).setSidebarData(
                Helpers.mergeData(Arrays.asList(home, downloads), MediaGroup.TYPE_HOME, true));
        sidebar = SidebarService.instance(RuntimeEnvironment.getApplication());
    }

    @After
    public void tearDown() {
        PhoneUi.setEnabled(false);
        ReflectionHelpers.setStaticField(SidebarService.class, "sInstance", null);
    }

    @Test
    public void tvSectionRegistryIsUnchanged() {
        sidebar.initializePhoneSections();
        assertFalse(sidebar.getDefaultSections().containsValue(BrowseSection.WATCH_LATER_ID));
        assertFalse(sidebar.isSectionPinned(BrowseSection.WATCH_LATER_ID));
    }

    @Test
    public void phoneRegistersAndEnablesWatchLaterForAnExistingInstallation() {
        PhoneUi.setEnabled(true);
        sidebar.initializePhoneSections();
        assertTrue(sidebar.getDefaultSections().containsValue(BrowseSection.WATCH_LATER_ID));
        assertTrue(sidebar.isSectionPinned(BrowseSection.WATCH_LATER_ID));
        assertTrue(sidebar.isSectionPinned(VideoDownloads.SECTION_ID));
    }

    @Test
    public void hidingWatchLaterSurvivesRestoringTheSavedProfile() {
        PhoneUi.setEnabled(true);
        sidebar.initializePhoneSections();
        sidebar.enableSection(BrowseSection.WATCH_LATER_ID, false);
        sidebar.onProfileChanged();
        sidebar.initializePhoneSections();
        assertFalse(sidebar.isSectionPinned(BrowseSection.WATCH_LATER_ID));
        assertTrue(sidebar.isSectionPinned(VideoDownloads.SECTION_ID));
    }

    @Test
    public void watchLaterCanBeEnabledAgainAndRestored() {
        PhoneUi.setEnabled(true);
        sidebar.initializePhoneSections();
        sidebar.enableSection(BrowseSection.WATCH_LATER_ID, false);
        sidebar.enableSection(BrowseSection.WATCH_LATER_ID, true);
        sidebar.onProfileChanged();
        assertTrue(sidebar.isSectionPinned(BrowseSection.WATCH_LATER_ID));
    }

    @Test
    public void hidingDownloadsDoesNotHideWatchLater() {
        PhoneUi.setEnabled(true);
        sidebar.initializePhoneSections();
        sidebar.enableSection(VideoDownloads.SECTION_ID, false);
        sidebar.onProfileChanged();
        assertFalse(sidebar.isSectionPinned(VideoDownloads.SECTION_ID));
        assertTrue(sidebar.isSectionPinned(BrowseSection.WATCH_LATER_ID));
    }
}
