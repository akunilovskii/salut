package com.newtube.mobile.ui.browse;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.material.snackbar.Snackbar;
import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.data.PlaylistInfo;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;
import com.newtube.mobile.ui.common.MobileActivity;
import com.newtube.mobile.ui.common.MobileSnackbar;
import com.newtube.mobile.watchlater.WatchLaterAddition;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

/** Owns feed actions until their host closes or the selected account changes. */
public final class WatchLaterQuickAdd implements DefaultLifecycleObserver, MediaServiceManager.AccountChangeListener {
    private final MobileActivity mActivity;
    private final CompositeDisposable mActions = new CompositeDisposable();
    private final Set<String> mPending = new HashSet<>();
    private Snackbar mNotice;

    public WatchLaterQuickAdd(MobileActivity activity) {
        mActivity = activity;
        activity.getLifecycle().addObserver(this);
        MediaServiceManager.instance().addAccountListener(this);
    }

    public void add(Video video) {
        if (video == null || !video.hasVideo() || video.isPlaylistAsChannel()) {
            return;
        }
        Account account = MediaServiceManager.instance().getSelectedAccount();
        if (account == null || account.isEmpty()) {
            AccountsSheet.show(mActivity, () -> { });
            return;
        }
        String videoId = video.videoId;
        if (!mPending.add(videoId)) {
            return;
        }
        WatchLaterAddition addition = new WatchLaterAddition(storeFor(account));
        mActions.add(RxHelper.execute(addition.add(videoId).doFinally(() -> mPending.remove(videoId)),
                result -> {
                    if (result.added) {
                        playlistEdited();
                    }
                    mNotice = MobileSnackbar.showTransient(mActivity,
                            mActivity.getString(result.added ? R.string.mobile_watch_later_added : R.string.mobile_watch_later_already_saved),
                            result.added ? mActivity.getString(R.string.mobile_undo) : null,
                            result.added ? () -> undo(addition, result) : null);
                }, error -> showError(R.string.mobile_watch_later_add_failed)));
    }

    private void undo(WatchLaterAddition addition, WatchLaterAddition.Result result) {
        if (!mPending.add(result.videoId)) {
            return;
        }
        mActions.add(RxHelper.execute(addition.undo(result).doFinally(() -> mPending.remove(result.videoId)),
                error -> showError(R.string.mobile_watch_later_undo_failed), this::playlistEdited));
    }

    private WatchLaterAddition.Store storeFor(Account account) {
        MediaItemService service = YouTubeServiceManager.instance().getMediaItemService();
        return new WatchLaterAddition.Store() {
            private void requireCurrentAccount() {
                Account current = MediaServiceManager.instance().getSelectedAccount();
                if (mActivity.isDestroyed() || current == null || current.getId() != account.getId()) {
                    throw new IllegalStateException("The selected account changed");
                }
            }

            @Override
            public Observable<List<PlaylistInfo>> playlists(String videoId) {
                requireCurrentAccount();
                return service.getPlaylistsInfoObserve(videoId);
            }

            @Override
            public Observable<Void> add(String playlistId, String videoId) {
                requireCurrentAccount();
                return service.addToPlaylistObserve(playlistId, videoId);
            }

            @Override
            public Observable<Void> remove(String playlistId, String videoId) {
                requireCurrentAccount();
                return service.removeFromPlaylistObserve(playlistId, videoId);
            }
        };
    }

    private void showError(int message) {
        mNotice = MobileSnackbar.showTransient(mActivity, mActivity.getString(message), null, null);
    }

    private void playlistEdited() {
        BrowsePresenter.instance(mActivity).onPlaylistEdited();
    }

    @Override
    public void onAccountChanged(Account account) {
        mActions.clear();
        mPending.clear();
        if (mNotice != null) {
            mNotice.dismiss();
        }
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner) {
        onAccountChanged(null);
        mActions.dispose();
        MediaServiceManager.instance().removeAccountListener(this);
    }
}
