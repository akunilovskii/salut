package com.newtube.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class PhoneSpeedPrefsTest {
    @Test public void startupReplacesChannelMemoryWithTheExistingGlobalSpeedMode() {
        Application context = RuntimeEnvironment.getApplication();
        PlayerData data = PlayerData.instance(context);
        data.setSpeedPerChannelEnabled(true);
        data.setSpeed("old-channel", 1.25f);
        data.setSpeed("last-channel", 1.75f);

        new PhoneOnlyPrefs(context).install();

        assertTrue(data.isAllSpeedEnabled());
        assertFalse(data.isSpeedPerChannelEnabled());
        assertFalse(data.isSpeedPerVideoEnabled());
        assertEquals(1.75f, data.getSpeed("old-channel"), 0f);
        assertEquals(1.75f, data.getSpeed("new-channel"), 0f);
        data.setSpeed(1f);
        assertEquals(1.75f, data.getLastSpeed(), 0f);
    }
}
