package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class PlaylistRemovalTest {
    private VideoMenuPresenter presenter;
    private final Map<String, PublishSubject<Void>> removals = new LinkedHashMap<>();
    private final List<String> removed = new ArrayList<>();
    private final List<String> restored = new ArrayList<>();
    private final List<String> failed = new ArrayList<>();
    private final List<String> added = new ArrayList<>();
    private Runnable undo;
    private boolean failAdd;

    @Before
    public void setUp() {
        presenter = VideoMenuPresenter.instance(RuntimeEnvironment.getApplication());
        MediaItemService service = (MediaItemService) Proxy.newProxyInstance(
                MediaItemService.class.getClassLoader(), new Class<?>[] {MediaItemService.class},
                (instance, method, arguments) -> {
                    if ("removeFromPlaylistObserve".equals(method.getName())) {
                        assertEquals("WL", arguments[0]);
                        PublishSubject<Void> request = PublishSubject.create();
                        removals.put((String) arguments[1], request);
                        return request;
                    }
                    if ("addToPlaylistObserve".equals(method.getName())) {
                        assertEquals("WL", arguments[0]);
                        added.add((String) arguments[1]);
                        return failAdd ? Observable.error(new IllegalStateException("offline")) : Observable.empty();
                    }
                    return null;
                });
        ReflectionHelpers.setField(presenter, "mMediaItemService", service);
        BaseMenuPresenter.setConfirmationSink((context, message, action, onAction) -> {
            if (onAction != null) undo = onAction;
        });
    }

    @After
    public void tearDown() {
        BaseMenuPresenter.setConfirmationSink(null);
    }

    @Test
    public void rowRemovalIsConfirmedOnlyAfterTheServerCompletes() {
        remove("first");
        assertTrue(removed.isEmpty());
        removals.get("first").onComplete();
        assertEquals(Arrays.asList("first"), removed);
    }

    @Test
    public void consecutiveSwipeEditsCompleteIndependently() {
        remove("first");
        remove("second");
        assertTrue(removals.get("first").hasObservers());
        removals.get("second").onComplete();
        removals.get("first").onComplete();
        assertEquals(Arrays.asList("second", "first"), removed);
    }

    @Test
    public void serverFailureRequestsTheOptimisticallyHiddenRowBack() {
        remove("first");
        removals.get("first").onError(new IllegalStateException("offline"));
        assertEquals(Arrays.asList("first"), failed);
        assertTrue(removed.isEmpty());
    }

    @Test
    public void undoRestoresTheRowAfterAddingItBackToWatchLater() {
        remove("first");
        removals.get("first").onComplete();
        assertNotNull(undo);
        undo.run();
        assertEquals(Arrays.asList("first"), added);
        assertEquals(Arrays.asList("first"), restored);
    }

    @Test
    public void failedUndoDoesNotPretendTheVideoWasRestored() {
        remove("first");
        removals.get("first").onComplete();
        failAdd = true;
        undo.run();
        assertTrue(restored.isEmpty());
    }

    private void remove(String id) {
        Video video = new Video();
        video.videoId = id;
        presenter.removeFromPlaylist(video, "WL", "Watch later", new VideoMenuPresenter.PlaylistEditCallback() {
            @Override public void onRemoved() { removed.add(id); }
            @Override public void onRestored() { restored.add(id); }
            @Override public void onError() { failed.add(id); }
        });
    }
}
