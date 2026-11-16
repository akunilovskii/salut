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
        SourcePlaybackLaunch.prepare(b, visible);
        visible.clear(); // Leaving or refreshing the channel cannot mutate the launch snapshot.
        VisibleNextQueue queue = new VisibleNextQueue();
        assertTrue(queue.startChannel(SourcePlaybackLaunch.take(b), b));
        assertEquals(Arrays.asList(d, c), queue.remaining());
        assertTrue(SourcePlaybackLaunch.take(b) == null);
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

    @Test
    public void watchLaterKeepsTheEntirePlaylistWhileNextAdvances() {
        Video a = video("a"), b = video("b"), c = video("c");
        List<Video> source = Arrays.asList(a, b, c);
        VisibleNextQueue queue = new VisibleNextQueue();
        assertTrue(queue.startPlaylist(source, b));
        assertEquals(source, queue.displayedVideos());
        assertSame(c, queue.peek(java.util.Collections.emptyList()));
        queue.expect(c);
        assertTrue(queue.consume(c));
        assertEquals(source, queue.displayedVideos());
        assertTrue(queue.peek(Arrays.asList(video("recommendation"))) == null);
        assertTrue(queue.isLocked());
    }

    @Test
    public void manualWatchLaterSelectionRestartsAtTheSelectedPosition() {
        Video a = video("a"), b = video("b"), c = video("c");
        VisibleNextQueue queue = new VisibleNextQueue();
        queue.startPlaylist(Arrays.asList(a, b, c), c);
        queue.expectSource(a);
        assertTrue(queue.consume(a));
        assertSame(b, queue.peek(java.util.Collections.emptyList()));
        assertEquals(0, queue.sourceIndex(a));
    }

    @Test
    public void watchLaterExplicitQueueInterruptsWithoutReplacingThePlaylist() {
        Video a = video("a"), b = video("b"), c = video("c");
        VisibleNextQueue queue = new VisibleNextQueue();
        queue.startPlaylist(Arrays.asList(a, b, c), a);
        Video requested = video("requested");
        requested.fromQueue = true;
        queue.expect(requested);
        assertTrue(queue.consume(requested));
        assertSame(b, queue.peek(java.util.Collections.emptyList()));
        c.fromQueue = true;
        queue.expect(c);
        assertTrue(queue.consume(c));
        queue.expect(b);
        assertTrue(queue.consume(b));
        assertTrue(queue.peek(java.util.Collections.emptyList()) == null);
        assertEquals(Arrays.asList(a, b, c), queue.displayedVideos());
    }

    @Test
    public void watchLaterContinuationAppendsWithoutDuplicatesOrOtherSources() {
        Video a = video("a"), b = video("b"), c = video("c");
        com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup group =
                com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup.from(Arrays.asList(a, b));
        group.setId(73);
        VisibleNextQueue queue = new VisibleNextQueue();
        queue.startPlaylist(Arrays.asList(a, b), b);
        com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup next =
                com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup.from(Arrays.asList(a, b, c));
        next.setId(74);
        assertFalse(queue.append(next));
        next.setId(73);
        assertTrue(queue.append(next));
        assertFalse(queue.append(next));
        assertEquals(Arrays.asList(a, b, c), queue.displayedVideos());
        assertSame(c, queue.peek(java.util.Collections.emptyList()));
    }

    @Test
    public void watchLaterShuffleIsStableAndDoesNotRepeatConsumedVideos() {
        Video a = video("a"), b = video("b"), c = video("c");
        VisibleNextQueue queue = new VisibleNextQueue();
        queue.startPlaylist(Arrays.asList(a, b, c), b);
        Video shuffled = queue.peek(java.util.Collections.emptyList(), true);
        assertTrue(shuffled == a || shuffled == c);
        assertSame(shuffled, queue.peek(java.util.Collections.emptyList(), true));
        queue.expect(shuffled);
        assertTrue(queue.consume(shuffled));
        Video remaining = queue.peek(java.util.Collections.emptyList(), true);
        assertTrue(remaining != b && remaining != shuffled);
        queue.expect(remaining);
        assertTrue(queue.consume(remaining));
        assertTrue(queue.peek(java.util.Collections.emptyList(), true) == null);
    }

    @Test
    public void watchLaterHandoffIsCopiedTypedAndConsumedOnce() {
        Video a = video("a"), b = video("b");
        List<Video> source = new java.util.ArrayList<>(Arrays.asList(a, b));
        SourcePlaybackLaunch.preparePlaylist(b, source, "Watch later");
        source.clear();
        SourcePlaybackLaunch.Snapshot snapshot = SourcePlaybackLaunch.takeSource(b);
        assertTrue(snapshot.playlist);
        assertEquals(Arrays.asList(a, b), snapshot.videos);
        assertTrue(SourcePlaybackLaunch.takeSource(b) == null);
    }

    @Test
    public void selectingAnEarlierSourceRowPreservesOnlyPendingExplicitQueueEntries() {
        com.liskovsoft.smartyoutubetv2.common.app.models.data.Playlist playlist =
                com.liskovsoft.smartyoutubetv2.common.app.models.data.Playlist.instance();
        playlist.clear();
        Video a = video("a"), b = video("b"), requested = video("requested");
        playlist.setCurrent(a);
        playlist.setCurrent(b);
        requested.fromQueue = true;
        playlist.next(requested);
        playlist.setCurrentFromSource(a);
        assertSame(a, playlist.getCurrent());
        assertSame(requested, playlist.getNext());
        playlist.setCurrentFromSource(b);
        assertSame(requested, playlist.getNext());
        playlist.clear();
    }

    @Test
    public void sourceHandoffDoesNotChangePlaybackBeforeTheMatchingOpen() {
        com.liskovsoft.smartyoutubetv2.common.app.models.data.Playlist playlist =
                com.liskovsoft.smartyoutubetv2.common.app.models.data.Playlist.instance();
        playlist.clear();
        Video a = video("a"), b = video("b"), requested = video("requested");
        playlist.setCurrent(a);
        playlist.setCurrent(b);
        requested.fromQueue = true;
        playlist.next(requested);
        SourcePlaybackLaunch.preparePlaylist(a, Arrays.asList(a, b), "Watch later");
        assertSame(b, playlist.getCurrent());
        playlist.setCurrent(a); // The loader selects the video before handing it to the view.
        SourcePlaybackLaunch.takeSource(a);
        assertSame(a, playlist.getCurrent());
        assertSame(requested, playlist.getNext());
        playlist.clear();
    }

    @Test
    public void otherPlaylistLaunchesRetainTheirTitleAndFullSourceOrder() {
        Video a = video("a"), b = video("b"), c = video("c");
        SourcePlaybackLaunch.preparePlaylist(b, Arrays.asList(a, b, c), "My playlist");
        SourcePlaybackLaunch.Snapshot source = SourcePlaybackLaunch.takeSource(b);
        assertEquals("My playlist", source.title);
        assertTrue(source.playlist);
        VisibleNextQueue queue = new VisibleNextQueue();
        queue.startPlaylist(source.videos, b);
        assertEquals(Arrays.asList(a, b, c), queue.displayedVideos());
        assertSame(c, queue.peek(java.util.Collections.emptyList()));
    }

    @Test public void repeatAllWrapsThenPlaysTheWholeSourceAgain() {
        Video a = video("a"), b = video("b"), c = video("c");
        VisibleNextQueue queue = new VisibleNextQueue();
        queue.startPlaylist(Arrays.asList(a, b, c), c);
        Video wrapped = queue.peek(java.util.Collections.emptyList(), false, true);
        assertSame(a, wrapped);
        queue.expect(wrapped);
        assertTrue(queue.consume(wrapped));
        assertEquals(Arrays.asList(b, c), queue.remaining());
        assertEquals(0, queue.currentSourceIndex());
    }

    @Test public void repeatAllShuffleStartsANewCycleWithoutRepeatingTheLastVideoImmediately() {
        Video a = video("a"), b = video("b");
        VisibleNextQueue queue = new VisibleNextQueue();
        queue.startPlaylist(Arrays.asList(a, b), a);
        queue.expect(b);
        assertTrue(queue.consume(b));
        assertSame(a, queue.peek(java.util.Collections.emptyList(), true, true));
        queue.expect(a);
        assertTrue(queue.consume(a));
        assertSame(b, queue.peek(java.util.Collections.emptyList(), true, true));
    }

    @Test public void queuedInterruptionSurvivesTheLoaderClearingItsTransientFlag() {
        Video a = video("a"), b = video("b");
        VisibleNextQueue queue = new VisibleNextQueue();
        queue.startPlaylist(Arrays.asList(a, b), a);
        queue.playbackMode().cycleRepeat();
        queue.playbackMode().toggleShuffle();
        Video requested = video("requested");
        queue.expect(requested, true);
        requested.fromQueue = false;
        assertTrue(queue.consume(requested));
        assertTrue(queue.hasPlaylist());
        assertTrue(queue.isQueueInterruption());
        assertEquals(0, queue.currentSourceIndex());
        assertTrue(queue.playbackMode().isShuffling());
        assertEquals(com.liskovsoft.smartyoutubetv2.common.app.models.playback.QueuePlaybackMode.Session.REPEAT_ALL,
                queue.playbackMode().repeat());
        assertSame(b, queue.peek(java.util.Collections.emptyList()));
        queue.expect(b, false);
        assertTrue(queue.consume(b));
        assertFalse(queue.isQueueInterruption());
    }

    @Test public void aSingleVideoCanRepeatWithoutAnUnrelatedFallback() {
        Video a = video("a");
        VisibleNextQueue queue = new VisibleNextQueue();
        queue.startPlaylist(Arrays.asList(a), a);
        assertSame(a, queue.peek(java.util.Collections.emptyList(), false, true));
        assertSame(a, queue.peek(java.util.Collections.emptyList(), true, true));
        assertTrue(queue.peek(Arrays.asList(video("recommendation")), false, false) == null);
    }

    @Test public void repeatAllDoesNotWrapBeforeTheRemainingSourcePageLoads() {
        Video a = video("a"), b = video("b");
        com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup source =
                com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup.from(Arrays.asList(a, b));
        com.liskovsoft.mediaserviceinterfaces.data.MediaGroup media =
                (com.liskovsoft.mediaserviceinterfaces.data.MediaGroup) java.lang.reflect.Proxy.newProxyInstance(
                        getClass().getClassLoader(), new Class<?>[] {com.liskovsoft.mediaserviceinterfaces.data.MediaGroup.class},
                        (instance, method, args) -> "getNextPageKey".equals(method.getName()) ? "next-page" : null);
        org.robolectric.util.ReflectionHelpers.setField(source, "mMediaGroup", media);
        VisibleNextQueue queue = new VisibleNextQueue();
        queue.startPlaylist(Arrays.asList(a, b), b);
        assertTrue(queue.peek(java.util.Collections.emptyList(), false, true) == null);
    }

    private static Video video(String id) {
        Video video = new Video();
        video.videoId = id;
        return video;
    }
}
