package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import android.app.Application;
import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.VideoMenuPresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.PhoneUi;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import java.util.ArrayList;
import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class ChannelPinTest {
    @After public void tearDown() { PhoneUi.setEnabled(false); }
    @Test public void aChannelCanBePinnedAndUnpinnedFromItsSourceWithoutAnOpenBrowseView() {
        PhoneUi.setEnabled(true);
        Context context = RuntimeEnvironment.getApplication();
        Video video = Video.from("test-video");
        video.channelId = "test-channel";
        video.author = "Test channel";
        video.reloadPageKey = "first-tab-token";
        VideoGroup source = VideoGroup.from(new ArrayList<>(Collections.singletonList(video)));
        video.setGroup(source);
        VideoMenuPresenter menu = VideoMenuPresenter.instance(context);
        assertFalse(menu.isChannelPinned(video));
        menu.toggleChannelPin(video);
        assertTrue(menu.isChannelPinned(video));
        Video otherCard = Video.from("other-video");
        otherCard.channelId = video.channelId;
        otherCard.reloadPageKey = "another-tab-token";
        assertTrue(menu.isChannelPinned(otherCard));
        menu.toggleChannelPin(otherCard);
        assertFalse(menu.isChannelPinned(video));
        assertFalse(menu.isChannelPinned(otherCard));
    }
}
