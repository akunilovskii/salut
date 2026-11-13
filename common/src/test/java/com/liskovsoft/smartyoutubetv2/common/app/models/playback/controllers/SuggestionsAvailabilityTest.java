package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Playlist;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.lang.reflect.Proxy;
import java.util.Arrays;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class SuggestionsAvailabilityTest {
    private Video mCurrent;
    private PlaybackView mPage;
    private SuggestionsController mController;

    @Before public void setUp() {
        Playlist.instance().clear();
        mCurrent = video("current");
        mPage = (PlaybackView) Proxy.newProxyInstance(PlaybackView.class.getClassLoader(),
                new Class<?>[] {PlaybackView.class}, (proxy, method, args) ->
                        method.getReturnType() == boolean.class ? false : null);
        mController = new SuggestionsController() {
            @Override public PlaybackView getPlayer() { return mPage; }
            @Override public Video getVideo() { return mCurrent; }
            @Override public Context getContext() { return RuntimeEnvironment.getApplication(); }
        };
    }

    @After public void tearDown() {
        Playlist.instance().clear();
    }

    @Test public void availabilityDoesNotAdvanceOrMarkQueuedVideos() {
        Video next = video("next");
        Playlist.instance().addAll(Arrays.asList(mCurrent, next));
        Playlist.instance().setCurrent(mCurrent);
        assertTrue(mController.hasNext());
        assertFalse(next.fromQueue);
        assertSame(next, Playlist.instance().getNext());
        Playlist.instance().setCurrent(next);
        assertFalse(mController.hasNext());
    }

    @Test public void metadataRecommendationEnablesNextWithoutAQueue() {
        mCurrent.nextMediaItem = (MediaItem) Proxy.newProxyInstance(MediaItem.class.getClassLoader(),
                new Class<?>[] {MediaItem.class}, (proxy, method, args) -> null);
        assertTrue(mController.hasNext());
        mCurrent.nextMediaItem = null;
        assertFalse(mController.hasNext());
    }

    @Test public void detachedOrEmptyPlayersHaveNoTransportTargets() {
        mCurrent = null;
        assertFalse(mController.hasNext());
        assertFalse(mController.hasPrevious());
        mCurrent = video("current");
        mPage = null;
        assertFalse(mController.hasNext());
        assertFalse(mController.hasPrevious());
    }

    private static Video video(String id) {
        Video video = new Video();
        video.videoId = id;
        return video;
    }
}
