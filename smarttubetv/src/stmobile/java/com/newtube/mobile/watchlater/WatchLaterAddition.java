package com.newtube.mobile.watchlater;

import com.liskovsoft.mediaserviceinterfaces.data.PlaylistInfo;

import java.util.List;

import io.reactivex.rxjava3.core.Observable;

/** Adds once and returns an Undo token only for a membership created by this operation. */
public final class WatchLaterAddition {
    public interface Store {
        Observable<List<PlaylistInfo>> playlists(String videoId);
        Observable<Void> add(String playlistId, String videoId);
        Observable<Void> remove(String playlistId, String videoId);
    }

    public static final class Result {
        public final String playlistId;
        public final String videoId;
        public final boolean added;

        private Result(String playlistId, String videoId, boolean added) {
            this.playlistId = playlistId;
            this.videoId = videoId;
            this.added = added;
        }
    }

    private final Store mStore;

    public WatchLaterAddition(Store store) {
        mStore = store;
    }

    public Observable<Result> add(String videoId) {
        if (videoId == null || videoId.isEmpty()) {
            return Observable.error(new IllegalArgumentException("A video ID is required"));
        }
        return Observable.defer(() -> mStore.playlists(videoId)).take(1)
                .switchIfEmpty(Observable.error(new IllegalStateException("Watch later is unavailable")))
                .concatMap(playlists -> {
                    PlaylistInfo watchLater = null;
                    for (PlaylistInfo playlist : playlists) {
                        if (playlist != null && "WL".equals(playlist.getPlaylistId())) {
                            watchLater = playlist;
                            break;
                        }
                    }
                    if (watchLater == null) {
                        return Observable.error(new IllegalStateException("Watch later is unavailable"));
                    }
                    Result result = new Result(watchLater.getPlaylistId(), videoId, !watchLater.isSelected());
                    return result.added
                            ? mStore.add(result.playlistId, videoId).ignoreElements().andThen(Observable.just(result))
                            : Observable.just(result);
                });
    }

    public Observable<Void> undo(Result result) {
        return result.added ? Observable.defer(() -> mStore.remove(result.playlistId, result.videoId))
                : Observable.empty();
    }
}
