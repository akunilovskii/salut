package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.ContentService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Playlist;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.PlayerEventListener;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerConstants;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.misc.PhoneUi;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.util.ReflectionHelpers;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.reactivex.rxjava3.core.Observable;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class SubscriptionsQueueTest {
    private Context context;
    private Video current;
    private SuggestionsController controller;
    private PlaybackView player;
    private int continuationRequests;
    // Video keeps only a weak reference; the real playback view owns its suggestion groups.
    private final List<VideoGroup> groups = new ArrayList<>();

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        PhoneUi.setEnabled(true);
        PlayerTweaksData.instance(context).setSectionPlaylistEnabled(true);
        Playlist.instance().clear();
        current = video("current");
        group(MediaGroup.TYPE_SUBSCRIPTIONS, current, video("following"));
        player = proxy(PlaybackView.class, (name, arguments, type) -> defaultValue(type));
        ContentService service = proxy(ContentService.class, (name, arguments, type) -> {
            if ("continueGroupObserve".equals(name)) {
                continuationRequests++;
            }
            return Observable.class.equals(type) ? Observable.empty() : defaultValue(type);
        });
        controller = new SuggestionsController() {
            @Override public PlaybackView getPlayer() { return player; }
            @Override public Video getVideo() { return current; }
            @Override public Context getContext() { return context; }
            @Override protected ContentService getContentService() { return service; }
        };
    }

    @After
    public void tearDown() {
        Playlist.instance().clear();
        PhoneUi.setEnabled(false);
    }

    @Test
    public void subscriptionsUseTheirSourceGroupWithoutAPlaylistId() {
        assertNull(current.getPlaylistId());
        assertTrue(current.isSectionPlaylistEnabled(context));
    }

    @Test
    public void homeSearchAndHistoryKeepTheirExistingRecommendationBehavior() {
        for (int type : new int[] {MediaGroup.TYPE_HOME, MediaGroup.TYPE_SEARCH, MediaGroup.TYPE_HISTORY}) {
            group(type, current, video("following"));
            assertFalse(current.isSectionPlaylistEnabled(context));
        }
    }

    @Test
    public void chosenPlaylistStillUsesItsSection() {
        Video following = video("following");
        current.playlistId = following.playlistId = "PLchosen";
        group(MediaGroup.TYPE_UNDEFINED, current, following);
        assertTrue(current.isSectionPlaylistEnabled(context));
    }

    @Test
    public void disabledSectionPlaylistSettingIsRespected() {
        PlayerTweaksData.instance(context).setSectionPlaylistEnabled(false);
        assertFalse(current.isSectionPlaylistEnabled(context));
    }

    @Test
    public void nextUsesTheFollowingSubscriptionInsteadOfTheRecommendation() {
        current.nextMediaItem = recommendation();
        Video following = current.getGroup().get(1);
        findFollowingVideo();
        assertSame(following, controller.getNext());
    }

    @Test
    public void playNextOverridesTheVisibleFollowingVideoAndAutoplay() {
        Video visible = video("visible");
        player = proxy(PlaybackView.class, (name, arguments, type) ->
                "getVisibleNextVideo".equals(name) ? visible : defaultValue(type));
        Video requested = enqueueNext();

        assertSame(requested, controller.getNext());
        assertTrue(requested.fromQueue);
        List<String> opens = new ArrayList<>();
        loader(opens).loadNext();
        assertEquals(Arrays.asList("requested"), opens);
    }

    @Test
    public void playNextWorksAfterTheRetainedVisibleListIsExhausted() {
        player = proxy(PlaybackView.class, (name, arguments, type) ->
                "isVisibleNextQueueLocked".equals(name) ? true : defaultValue(type));
        assertFalse(controller.hasNext());
        Video requested = enqueueNext();
        assertTrue(controller.hasNext());
        assertSame(requested, controller.getNext());
    }

    private Video enqueueNext() {
        Playlist.instance().setCurrent(current);
        Video requested = video("requested");
        requested.fromQueue = true;
        Playlist.instance().next(requested);
        return requested;
    }

    @Test
    public void nextSkipsUpcomingVideosAndShortsInTheSourceOrder() {
        Video upcoming = video("upcoming");
        upcoming.isUpcoming = true;
        Video shorts = video("shorts");
        shorts.isShorts = true;
        Video following = video("following");
        group(MediaGroup.TYPE_SUBSCRIPTIONS, video("earlier"), current, upcoming, shorts, following);
        findFollowingVideo();
        assertSame(following, controller.getNext());
    }

    @Test
    public void exhaustedSubscriptionsNeverFallBackToRecommendations() {
        current.nextMediaItem = recommendation();
        group(MediaGroup.TYPE_SUBSCRIPTIONS, video("earlier"), current);
        findFollowingVideo();
        assertEquals(1, continuationRequests);
        assertNull(controller.getNext());
    }

    @Test
    public void appendedSubscriptionPageKeepsTheSameQueue() {
        VideoGroup source = group(MediaGroup.TYPE_SUBSCRIPTIONS, video("earlier"), current);
        Video following = video("next-page-first");
        Video later = video("next-page-second");
        MediaGroup continuation = proxy(MediaGroup.class, (name, arguments, type) -> {
            if ("getMediaItems".equals(name)) return Arrays.asList(following.toMediaItem(), later.toMediaItem());
            if ("getType".equals(name)) return MediaGroup.TYPE_SUBSCRIPTIONS;
            return defaultValue(type);
        });
        VideoGroup.from(source, continuation);
        findFollowingVideo();
        assertEquals(following.videoId, controller.getNext().videoId);
        assertSame(source, controller.getNext().getGroup());
    }

    @Test
    public void homeStillFallsBackToTheYoutubeRecommendation() {
        group(MediaGroup.TYPE_HOME, current, video("following"));
        current.nextMediaItem = recommendation();
        assertEquals("recommendation", controller.getNext().videoId);
    }

    @Test
    public void anExplicitManualQueueKeepsItsExistingPriority() {
        Video manual = video("manual");
        Playlist.instance().add(current);
        Playlist.instance().add(manual);
        Playlist.instance().setCurrent(current);
        findFollowingVideo();
        assertSame(manual, controller.getNext());
        assertTrue(manual.fromQueue);
    }

    @Test
    public void loaderMetadataFallbackCannotEscapeAnExhaustedSubscriptionsQueue() {
        List<String> opens = new ArrayList<>();
        current.isSynced = true;
        current.nextMediaItem = recommendation();
        loader(opens).loadNext();
        assertTrue(opens.isEmpty());
    }

    @Test
    public void finishPlaylistModeAutomaticallyContinuesThroughSubscriptions() {
        List<String> opens = new ArrayList<>();
        PlayerData.instance(context).setPlaybackMode(PlayerConstants.PLAYBACK_MODE_LIST);
        current.nextMediaItem = recommendation();
        findFollowingVideo();
        loader(opens).onPlayEnd();
        assertEquals(Arrays.asList("following"), opens);
    }

    @Test
    public void finishPlaylistModeStopsAtTheEndOfSubscriptions() {
        List<String> opens = new ArrayList<>();
        PlayerData.instance(context).setPlaybackMode(PlayerConstants.PLAYBACK_MODE_LIST);
        current.nextMediaItem = recommendation();
        group(MediaGroup.TYPE_SUBSCRIPTIONS, video("earlier"), current);
        findFollowingVideo();
        loader(opens).onPlayEnd();
        assertTrue(opens.isEmpty());
    }

    @Test
    public void exhaustedLockedWatchLaterCannotUseMetadataRecommendation() {
        current.isSynced = true;
        current.nextMediaItem = recommendation();
        player = proxy(PlaybackView.class, (name, arguments, type) ->
                "isVisibleNextQueueLocked".equals(name) || "hasSourcePlaylist".equals(name)
                        ? true : defaultValue(type));
        List<String> opens = new ArrayList<>();
        loader(opens).loadNext();
        assertTrue(opens.isEmpty());
        assertFalse(controller.hasNext());
    }

    @Test
    public void finishPlaylistModeContinuesThroughLockedWatchLaterSource() {
        Video following = video("following");
        player = proxy(PlaybackView.class, (name, arguments, type) -> {
            if ("isVisibleNextQueueLocked".equals(name) || "hasSourcePlaylist".equals(name)) return true;
            if ("getSourcePlaylistPlaybackMode".equals(name)) return arguments[0];
            if ("getVisibleNextVideo".equals(name)) return following;
            return defaultValue(type);
        });
        PlayerData.instance(context).setPlaybackMode(PlayerConstants.PLAYBACK_MODE_LIST);
        List<String> opens = new ArrayList<>();
        loader(opens).onPlayEnd();
        assertEquals(Arrays.asList("following"), opens);
    }

    @Test
    public void pendingSourcePageCountsAsNextWithoutUsingRecommendations() {
        current.nextMediaItem = recommendation();
        player = proxy(PlaybackView.class, (name, arguments, type) ->
                "isVisibleNextQueueLocked".equals(name) || "isSourcePlaylistLoading".equals(name)
                        ? true : defaultValue(type));
        assertTrue(controller.hasNext());
        assertNull(controller.getNext());
    }

    @Test
    public void selectingAQueuedSourceRowMatchesItsIdAcrossPlaylistContexts() {
        Playlist.instance().setCurrent(video("previous"));
        Video queued = video("selected");
        queued.fromQueue = true;
        Playlist.instance().next(queued);
        Video selected = video("selected");
        selected.playlistId = "PLchosen";
        Playlist.instance().setCurrentFromSource(selected);
        assertSame(selected, Playlist.instance().getCurrent());
        assertNull(Playlist.instance().getNext());
    }

    @Test public void sourceRepeatOneOverridesTheInheritedGlobalModeAndKeepsPlayNextPriority() {
        PlayerData.instance(context).setPlaybackMode(PlayerConstants.PLAYBACK_MODE_ALL);
        List<Long> positions = new ArrayList<>();
        player = proxy(PlaybackView.class, (name, arguments, type) -> {
            if ("hasSourcePlaylist".equals(name)) return true;
            if ("getSourcePlaylistPlaybackMode".equals(name)) return PlayerConstants.PLAYBACK_MODE_ONE;
            if ("setPositionMs".equals(name)) positions.add((Long) arguments[0]);
            return defaultValue(type);
        });
        List<String> opens = new ArrayList<>();
        loader(opens).onPlayEnd();
        assertEquals(Arrays.asList(0L), positions);
        assertTrue(opens.isEmpty());
        enqueueNext();
        loader(opens).onPlayEnd();
        assertEquals(Arrays.asList("requested"), opens);
        assertEquals(PlayerConstants.PLAYBACK_MODE_ALL, PlayerData.instance(context).getPlaybackMode());
    }

    private VideoLoaderController loader(List<String> opens) {
        PlayerEventListener listener = proxy(PlayerEventListener.class, (name, arguments, type) -> {
            if ("onNewVideo".equals(name)) opens.add(((Video) arguments[0]).videoId);
            return defaultValue(type);
        });
        VideoLoaderController loader = new VideoLoaderController() {
            @Override public PlaybackView getPlayer() { return player; }
            @Override public Context getContext() { return context; }
            @Override public Video getVideo() { return current; }
            @Override protected PlayerEventListener getMainController() { return listener; }
        };
        ReflectionHelpers.setField(loader, "mSuggestionsController", controller);
        return loader;
    }

    private void findFollowingVideo() {
        ReflectionHelpers.callInstanceMethod(controller, "findNextSectionVideo",
                ReflectionHelpers.ClassParameter.from(Video.class, current));
    }

    private static Video video(String id) {
        Video video = new Video();
        video.videoId = id;
        video.title = id;
        return video;
    }

    private VideoGroup group(int type, Video... videos) {
        VideoGroup group = VideoGroup.from(new ArrayList<>(Arrays.asList(videos)));
        groups.add(group);
        group.setType(type);
        for (Video video : videos) video.setGroup(group);
        return group;
    }

    private static MediaItem recommendation() {
        return proxy(MediaItem.class, (name, arguments, type) ->
                "getVideoId".equals(name) ? "recommendation" : defaultValue(type));
    }

    private interface Answer {
        Object invoke(String name, Object[] arguments, Class<?> type);
    }

    private static <T> T proxy(Class<T> type, Answer answer) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (instance, method, arguments) -> answer.invoke(method.getName(), arguments, method.getReturnType())));
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        return null;
    }
}
