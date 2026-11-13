package com.newtube.mobile.ui.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.service.SidebarService;
import com.liskovsoft.smartyoutubetv2.common.misc.PhoneUi;
import com.liskovsoft.smartyoutubetv2.tv.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class NavigationTabsSettingsTest {
    @Test public void bottomSettingsFollowTheActualTabOrder() {
        List<String> titles = new ArrayList<>();
        boolean bottom = false;
        for (SettingsRow row : AppPages.tabs(mContext).rows) {
            if (row.kind == SettingsRow.KIND_HEADER) {
                bottom = row.title.toString().equals(mContext.getString(R.string.mobile_settings_tabs_bottom_bar));
            } else if (bottom && row.kind == SettingsRow.KIND_SWITCH) {
                titles.add(row.title.toString());
            }
        }
        assertEquals(Arrays.asList(mContext.getString(R.string.header_history),
                mContext.getString(R.string.header_home),
                mContext.getString(R.string.header_watch_later),
                mContext.getString(R.string.header_subscriptions)), titles);
    }
    private Application mContext;

    @Before public void setUp() {
        mContext = RuntimeEnvironment.getApplication();
        PhoneUi.setEnabled(true);
    }

    @After public void tearDown() {
        PhoneUi.setEnabled(false);
    }

    @Test
    public void settingsKeepHomeUnderBottomBarWithoutUneditableTopBarNotes() {
        String group = null;
        boolean home = false;
        boolean you = false;
        for (SettingsRow row : AppPages.tabs(mContext).rows) {
            if (row.kind == SettingsRow.KIND_HEADER) {
                group = row.title.toString();
            }
            if (mContext.getString(R.string.header_home).contentEquals(row.title)) {
                assertEquals(mContext.getString(R.string.mobile_settings_tabs_bottom_bar), group);
                home = true;
            }
            if (mContext.getString(R.string.mobile_settings_tabs_you_fixed).contentEquals(row.title)) {
                you = true;
            }
        }
        assertTrue(home);
        assertFalse(you);
        assertTrue(AppPages.tabs(mContext).rows.stream().noneMatch(row ->
                mContext.getString(R.string.mobile_settings_tabs_top_bar).contentEquals(row.title)
                || mContext.getString(R.string.mobile_settings_tabs_subscriptions_dot).contentEquals(row.title)
                || mContext.getString(R.string.mobile_settings_tabs_account_cycle).contentEquals(row.title)));
    }

    @Test
    public void theMovedHomeSwitchStillReadsTheExistingVisibilityPreference() {
        List<SettingsRow> rows = AppPages.tabs(mContext).rows;
        SettingsRow home = rows.stream().filter(row -> row.kind == SettingsRow.KIND_SWITCH
                && mContext.getString(R.string.header_home).contentEquals(row.title)).findFirst().get();
        SidebarService sidebar = SidebarService.instance(mContext);
        sidebar.enableSection(MediaGroup.TYPE_HOME, false);
        assertFalse(home.checked.getAsBoolean());
        sidebar.enableSection(MediaGroup.TYPE_HOME, true);
        assertTrue(home.checked.getAsBoolean());
    }
}
