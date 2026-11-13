package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Playlist;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.misc.PhoneUi;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;

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
import java.util.Collections;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class ContextualSuggestionsTest {
    private final List<VideoGroup> sources = new ArrayList<>();
    private final List<VideoGroup> delivered = new ArrayList<>();
    private SuggestionsController controller;
    private PlayerTweaksData tweaks;
    private Video current;

    @Before public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        PhoneUi.setEnabled(true);
        SuggestionsController.setEagerSuggestionsEnabled(false);
        SuggestionsController.setRowContinuationsDisabled(true);
        Playlist.instance().clear();
        tweaks = PlayerTweaksData.instance(context);
        tweaks.setSuggestionsDisabled(true);
        tweaks.setSectionPlaylistEnabled(false);
        GeneralData.instance(context).setChildModeEnabled(false);
        PlaybackView view = proxy(PlaybackView.class, (name, args, type) -> {
            if ("updateSuggestions".equals(name)) delivered.add((VideoGroup) args[0]);
            return defaultValue(type);
        });
        controller = new SuggestionsController() {
            @Override public Context getContext() { return context; }
            @Override public PlaybackView getPlayer() { return view; }
            @Override public Video getVideo() { return current; }
            @Override String accountKey() { return "account"; }
        };
    }

    @After public void tearDown() {
        PhoneUi.setEnabled(false);
        SuggestionsController.setRowContinuationsDisabled(false);
        Playlist.instance().clear();
    }

    @Test public void homeSearchAndExternalLinksDeliverRecommendationRows() {
        for (Video video : Arrays.asList(video("home", MediaGroup.TYPE_HOME),
                video("search", MediaGroup.TYPE_SEARCH), Video.from("external"),
                video("undefined", MediaGroup.TYPE_UNDEFINED))) {
            open(video);
            assertFalse(controller.shouldHideRecommendations());
            delivered.clear();
            appendRecommendations();
            assertEquals(video.videoId, 1, delivered.size());
        }
    }

    @Test public void curatedSourcesDoNotDeliverRecommendationRows() {
        for (int type : new int[] {MediaGroup.TYPE_SUBSCRIPTIONS, MediaGroup.TYPE_USER_PLAYLISTS,
                MediaGroup.TYPE_HISTORY, MediaGroup.TYPE_CHANNEL, MediaGroup.TYPE_CHANNEL_UPLOADS}) {
            open(video("curated-" + type, type));
            assertTrue(controller.shouldHideRecommendations());
            delivered.clear();
            appendRecommendations();
            assertTrue(delivered.isEmpty());
        }
    }

    @Test public void recommendationHopsRetainTheOriginUntilAnotherSourceIsOpened() {
        open(video("subscriptions", MediaGroup.TYPE_SUBSCRIPTIONS));
        open(video("suggestion", MediaGroup.TYPE_SUGGESTIONS));
        assertTrue(controller.shouldHideRecommendations());
        open(video("search", MediaGroup.TYPE_SEARCH));
        open(video("related", MediaGroup.TYPE_SUGGESTIONS));
        assertFalse(controller.shouldHideRecommendations());
        open(video("history", MediaGroup.TYPE_HISTORY));
        assertTrue(controller.shouldHideRecommendations());
        open(Video.from("external"));
        assertFalse(controller.shouldHideRecommendations());
    }

    @Test public void explicitQueueRetainsTheViewingContext() {
        open(video("subscriptions", MediaGroup.TYPE_SUBSCRIPTIONS));
        Video queued = video("queued-home-card", MediaGroup.TYPE_HOME);
        queued.fromQueue = true;
        open(queued);
        assertTrue(controller.shouldHideRecommendations());
        queued.fromQueue = false;
        open(queued);
        assertTrue(controller.shouldHideRecommendations());
        open(video("home", MediaGroup.TYPE_HOME));
        open(video("queued", MediaGroup.TYPE_PLAYBACK_QUEUE));
        assertFalse(controller.shouldHideRecommendations());
    }

    @Test public void reloadRetainsTheSourceAfterItsWeakGroupIsGone() {
        Video video = video("subscriptions", MediaGroup.TYPE_SUBSCRIPTIONS);
        open(video);
        video.setGroup(null);
        open(video);
        assertTrue(controller.shouldHideRecommendations());
    }

    @Test public void watchLaterKeepsItsSourceListAndExplicitPlayNextPriority() {
        tweaks.setSectionPlaylistEnabled(true);
        Video first = Video.from("first");
        Video second = Video.from("second");
        first.playlistId = second.playlistId = "WL";
        VideoGroup group = VideoGroup.from(new ArrayList<>(Arrays.asList(first, second)));
        group.setType(MediaGroup.TYPE_USER_PLAYLISTS);
        first.setGroup(group);
        second.setGroup(group);
        sources.add(group);
        open(first);
        appendRecommendations();
        assertEquals(1, delivered.size());
        assertSame(group, delivered.get(0));
        assertSame(second, controller.getNext());

        Video queued = Video.from("play-next");
        Playlist.instance().addAll(Arrays.asList(first, queued));
        Playlist.instance().setCurrent(first);
        assertSame(queued, controller.getNext());
        assertTrue(controller.shouldHideRecommendations());
    }

    @Test public void disablingTheOptionRestoresRecommendationsForCuratedSources() {
        open(video("subscriptions", MediaGroup.TYPE_SUBSCRIPTIONS));
        tweaks.setSuggestionsDisabled(false);
        assertFalse(controller.shouldHideRecommendations());
        appendRecommendations();
        assertEquals(1, delivered.size());
    }

    @Test public void childModeStillHidesHomeRecommendations() {
        open(video("home", MediaGroup.TYPE_HOME));
        GeneralData.instance(controller.getContext()).setChildModeEnabled(true);
        assertTrue(controller.shouldHideRecommendations());
        appendRecommendations();
        assertTrue(delivered.isEmpty());
    }

    @Test public void tvKeepsTheExistingGlobalHideBehavior() {
        PhoneUi.setEnabled(false);
        open(video("home", MediaGroup.TYPE_HOME));
        assertTrue(controller.shouldHideRecommendations());
    }

    @Test public void sameVideoOpenedFromDifferentSourcesHasDifferentRenderedContext() {
        open(video("same", MediaGroup.TYPE_SUBSCRIPTIONS));
        String curatedKey = controller.documentKey(current);
        open(video("same", MediaGroup.TYPE_HOME));
        assertNotEquals(SuggestionsController.contextOf(curatedKey),
                SuggestionsController.contextOf(controller.documentKey(current)));
    }

    private void open(Video video) {
        current = video;
        controller.onNewVideo(video);
    }

    private Video video(String id, int type) {
        Video video = Video.from(id);
        VideoGroup group = VideoGroup.from(new ArrayList<>(Collections.singletonList(video)));
        group.setType(type);
        video.setGroup(group);
        sources.add(group);
        return video;
    }

    private void appendRecommendations() {
        MediaItem item = proxy(MediaItem.class, (name, args, type) ->
                "getVideoId".equals(name) ? "recommended" : defaultValue(type));
        MediaGroup row = proxy(MediaGroup.class, (name, args, type) -> {
            switch (name) {
                case "getMediaItems": return Collections.singletonList(item);
                case "getType": return MediaGroup.TYPE_SUGGESTIONS;
                case "getTitle": return "Recommendations";
                default: return defaultValue(type);
            }
        });
        MediaItemMetadata metadata = proxy(MediaItemMetadata.class, (name, args, type) ->
                "getSuggestions".equals(name) ? Collections.singletonList(row) : defaultValue(type));
        ReflectionHelpers.callInstanceMethod(controller, "appendSuggestions",
                ReflectionHelpers.ClassParameter.from(Video.class, current),
                ReflectionHelpers.ClassParameter.from(MediaItemMetadata.class, metadata));
    }

    private interface Answer {
        Object invoke(String name, Object[] args, Class<?> returnType);
    }

    private static <T> T proxy(Class<T> type, Answer answer) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (instance, method, args) -> answer.invoke(method.getName(), args, method.getReturnType())));
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        return null;
    }
}
