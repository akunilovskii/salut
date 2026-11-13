package com.newtube.mobile.watchlater;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.liskovsoft.mediaserviceinterfaces.data.PlaylistInfo;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subjects.PublishSubject;

public class WatchLaterAdditionTest {
    private final FakeStore mStore = new FakeStore();
    private final WatchLaterAddition mAddition = new WatchLaterAddition(mStore);

    @Test
    public void addAndUndoUseTheExactVideoAndWatchLaterPlaylist() {
        WatchLaterAddition.Result result = mAddition.add("video-one").blockingFirst();
        assertTrue(result.added);
        assertEquals("WL/video-one", mStore.added);
        mAddition.undo(result).test().assertComplete().assertNoErrors();
        assertEquals("WL/video-one", mStore.removed);
    }

    @Test
    public void anAlreadySavedVideoHasNoDestructiveUndo() {
        mStore.playlists = Observable.just(Collections.singletonList(info("WL", true)));
        WatchLaterAddition.Result result = mAddition.add("saved-video").blockingFirst();
        assertFalse(result.added);
        mAddition.undo(result).test().assertComplete().assertNoErrors();
        assertEquals(null, mStore.added);
        assertEquals(null, mStore.removed);
    }

    @Test
    public void watchLaterIsIdentifiedByIdInsteadOfAnUnrelatedFirstPlaylist() {
        mStore.playlists = Observable.just(Arrays.asList(info("someone-else", false), info("WL", false)));
        mAddition.add("video-two").test().assertComplete().assertNoErrors();
        assertEquals("WL/video-two", mStore.added);
    }

    @Test
    public void aMissingWatchLaterNeverWritesToAnotherPlaylist() {
        mStore.playlists = Observable.just(Collections.singletonList(info("other-playlist", false)));
        mAddition.add("video").test().assertError(IllegalStateException.class).assertNoValues();
        assertEquals(null, mStore.added);
    }

    @Test
    public void failedOrEmptyLookupsDoNotPerformAnEdit() {
        mStore.playlists = Observable.error(new IllegalArgumentException("offline"));
        mAddition.add("video").test().assertError(IllegalArgumentException.class).assertNoValues();
        mStore.playlists = Observable.empty();
        mAddition.add("video").test().assertError(IllegalStateException.class).assertNoValues();
        assertEquals(null, mStore.added);
    }

    @Test
    public void successIsReportedOnlyAfterTheServerCompletesTheAdd() {
        PublishSubject<Void> edit = PublishSubject.create();
        mStore.edit = edit;
        TestObserver<WatchLaterAddition.Result> observer = mAddition.add("video").test();
        observer.assertNoValues().assertNotComplete();
        edit.onComplete();
        observer.assertValue(result -> result.added).assertComplete();
    }

    @Test
    public void failedEditsNeverOfferUndo() {
        mStore.edit = Observable.error(new IllegalArgumentException("add failed"));
        mAddition.add("video").test().assertError(IllegalArgumentException.class).assertNoValues();
        assertEquals(null, mStore.removed);
    }

    @Test
    public void aFailedUndoReportsFailureInsteadOfSuccess() {
        WatchLaterAddition.Result result = mAddition.add("video").blockingFirst();
        mStore.removal = Observable.error(new IllegalArgumentException("undo failed"));
        mAddition.undo(result).test().assertError(IllegalArgumentException.class).assertNotComplete();
    }

    @Test
    public void missingVideoIdsNeverQueryOrWriteAPlaylist() {
        mAddition.add(null).test().assertError(IllegalArgumentException.class);
        mAddition.add("").test().assertError(IllegalArgumentException.class);
        assertEquals(null, mStore.added);
    }

    @Test
    public void cancelledLookupCannotAddAfterTheHostOrAccountChanges() {
        PublishSubject<List<PlaylistInfo>> lookup = PublishSubject.create();
        mStore.playlists = lookup;
        TestObserver<WatchLaterAddition.Result> observer = mAddition.add("video").test();
        observer.dispose();
        lookup.onNext(Collections.singletonList(info("WL", false)));
        observer.assertNoValues();
        assertFalse(lookup.hasObservers());
        assertEquals(null, mStore.added);
    }

    private static PlaylistInfo info(String id, boolean selected) {
        return new PlaylistInfo() {
            @Override public String getTitle() { return "Playlist"; }
            @Override public String getPlaylistId() { return id; }
            @Override public boolean isSelected() { return selected; }
            @Override public int getSize() { return 0; }
            @Override public int getCurrentIndex() { return -1; }
        };
    }

    private static final class FakeStore implements WatchLaterAddition.Store {
        Observable<List<PlaylistInfo>> playlists = Observable.just(Collections.singletonList(info("WL", false)));
        Observable<Void> edit = Observable.empty();
        Observable<Void> removal = Observable.empty();
        String added;
        String removed;

        @Override public Observable<List<PlaylistInfo>> playlists(String videoId) { return playlists; }
        @Override public Observable<Void> add(String playlistId, String videoId) {
            added = playlistId + "/" + videoId;
            return edit;
        }
        @Override public Observable<Void> remove(String playlistId, String videoId) {
            removed = playlistId + "/" + videoId;
            return removal;
        }
    }
}
