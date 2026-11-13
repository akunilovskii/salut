package com.newtube.mobile.ui.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class VisibleNextQueueTest {
    @Test
    public void nextFollowsVisibleOrderAndKeepsRemainingVideos() {
        Video first = video("first");
        Video second = video("second");
        Video third = video("third");
        List<Video> visible = Arrays.asList(first, second, third);
        VisibleNextQueue queue = new VisibleNextQueue();

        assertSame(first, queue.peek(visible));
        queue.lock(visible);
        queue.expect(queue.peek(visible));
        assertTrue(queue.consume(first));
        assertEquals(Arrays.asList(second, third), queue.remaining());
        queue.expect(queue.peek(visible));
        assertTrue(queue.consume(second));
        assertEquals(Arrays.asList(third), queue.remaining());
        assertSame(third, queue.peek(visible));
    }

    @Test
    public void unrelatedOpenReleasesTheSavedOrder() {
        Video first = video("first");
        Video second = video("second");
        VisibleNextQueue queue = new VisibleNextQueue();
        queue.lock(Arrays.asList(first, second));
        queue.expect(first);

        assertFalse(queue.consume(video("other")));
        assertFalse(queue.isLocked());
        assertSame(second, queue.peek(Arrays.asList(second)));
    }

    @Test
    public void explicitNextInterruptsThenResumesTheRetainedList() {
        Video first = video("first");
        Video second = video("second");
        List<Video> visible = Arrays.asList(first, second);
        VisibleNextQueue queue = new VisibleNextQueue();
        queue.lock(visible);
        Video requested = video("requested");
        requested.fromQueue = true;
        queue.expect(requested);

        assertTrue(queue.consume(requested));
        assertTrue(queue.isLocked());
        assertEquals(visible, queue.remaining());
        assertSame(first, queue.peek(visible));
        queue.expect(first);
        assertTrue(queue.consume(first));
        assertEquals(Arrays.asList(second), queue.remaining());
    }

    @Test
    public void movingALaterVisibleVideoNextDoesNotSkipEarlierVideosOrRepeatIt() {
        Video first = video("first");
        Video second = video("second");
        Video third = video("third");
        VisibleNextQueue queue = new VisibleNextQueue();
        queue.lock(Arrays.asList(first, second, third));
        second.fromQueue = true;
        queue.expect(second);

        assertTrue(queue.consume(second));
        assertEquals(Arrays.asList(first, third), queue.remaining());
    }

    @Test
    public void channelLaunchKeepsOnlyFollowingVideosInTheDisplayedOrder() {
        Video a = video("a");
        Video b = video("b");
        Video c = video("c");
        Video d = video("d");
        List<Video> visible = new java.util.ArrayList<>(Arrays.asList(a, b, d, c));
        ChannelPlaybackLaunch.prepare(b, visible);
        visible.clear(); // Leaving or refreshing the channel cannot mutate the launch snapshot.
        VisibleNextQueue queue = new VisibleNextQueue();
        assertTrue(queue.startChannel(ChannelPlaybackLaunch.take(b), b));
        assertEquals(Arrays.asList(d, c), queue.remaining());
        assertTrue(ChannelPlaybackLaunch.take(b) == null);
        Video requested = video("requested");
        requested.fromQueue = true;
        queue.expect(requested);
        assertTrue(queue.consume(requested));
        assertSame(d, queue.peek(java.util.Collections.emptyList()));
    }

    @Test
    public void lastChannelVideoKeepsAnEmptySourceInsteadOfRecommendations() {
        Video last = video("last");
        VisibleNextQueue queue = new VisibleNextQueue();
        assertTrue(queue.startChannel(Arrays.asList(video("earlier"), last), last));
        assertTrue(queue.isLocked());
        assertTrue(queue.peek(Arrays.asList(video("recommendation"))) == null);
    }

    @Test
    public void channelContinuationDoesNotRepeatConsumedVideosOrOtherTabs() {
        Video a = video("a");
        Video b = video("b");
        Video c = video("c");
        com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup source =
                com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup.from(Arrays.asList(a, b));
        source.setId(51);
        VisibleNextQueue queue = new VisibleNextQueue();
        assertTrue(queue.startChannel(Arrays.asList(a, b), a));
        com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup next =
                com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup.from(Arrays.asList(a, b, c));
        next.setId(51);
        assertTrue(queue.append(next));
        assertEquals(Arrays.asList(b, c), queue.remaining());
        next.setId(52);
        assertFalse(queue.append(next));
    }

    private static Video video(String id) {
        Video video = new Video();
        video.videoId = id;
        return video;
    }
}
