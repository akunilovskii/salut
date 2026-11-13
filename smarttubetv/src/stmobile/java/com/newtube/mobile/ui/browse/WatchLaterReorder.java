package com.newtube.mobile.ui.browse;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.newtube.mobile.ui.common.FeedCache;
import com.newtube.mobile.ui.common.MobileActivity;
import com.newtube.mobile.ui.common.MobileSnackbar;
import com.newtube.mobile.watchlater.PlaylistOrderStore;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;

/** One drag transaction, isolated from refreshes, other accounts and later gestures. */
final class WatchLaterReorder implements DefaultLifecycleObserver, MediaServiceManager.AccountChangeListener {
    private final MobileActivity mActivity;
    private final VideoCardAdapter mAdapter;
    private final List<Video> mVideos;
    private final BooleanSupplier mActive;
    private final Consumer<Boolean> mEditingChanged;
    private final Runnable mChanged;
    private List<Video> mOriginal;
    private Account mAccount;
    private Video mMoved;
    private boolean mSaving;
    private Disposable mSave;

    WatchLaterReorder(MobileActivity activity, VideoCardAdapter adapter, List<Video> videos,
                      BooleanSupplier active, Consumer<Boolean> editingChanged, Runnable changed) {
        mActivity = activity;
        mAdapter = adapter;
        mVideos = videos;
        mActive = active;
        mEditingChanged = editingChanged;
        mChanged = changed;
        activity.getLifecycle().addObserver(this);
        MediaServiceManager.instance().addAccountListener(this);
    }

    boolean isBusy() {
        return mOriginal != null && mAccount == MediaServiceManager.instance().getSelectedAccount();
    }

    boolean canStart(int index) {
        Account account = MediaServiceManager.instance().getSelectedAccount();
        return !isBusy() && mActive.getAsBoolean() && account != null && !account.isEmpty()
                && index >= 0 && index < mVideos.size() && mVideos.get(index).hasVideo();
    }

    boolean canDrag(int index) {
        return isBusy() ? !mSaving && mActive.getAsBoolean() && index >= 0
                && index < mVideos.size() && mVideos.get(index).hasVideo() : canStart(index);
    }

    void start(int index) {
        if (!canStart(index)) return;
        mAccount = MediaServiceManager.instance().getSelectedAccount();
        mOriginal = new ArrayList<>(mVideos);
        mMoved = mVideos.get(index);
        mEditingChanged.accept(true);
    }

    boolean move(int from, int to) {
        if (!isBusy() || mSaving || !mActive.getAsBoolean() || from < 0 || to < 0
                || from >= mVideos.size() || to >= mVideos.size() || !mVideos.get(to).hasVideo()) return false;
        Video video = mVideos.remove(from);
        mVideos.add(to, video);
        mAdapter.moveCompactItem(from, to);
        return true;
    }

    void finish() {
        if (!isBusy() || mSaving) return;
        int position = mVideos.indexOf(mMoved);
        if (!mActive.getAsBoolean() || position < 0 || mOriginal.equals(mVideos)) {
            reset();
            return;
        }
        String videoId = mMoved.videoId;
        String predecessor = position == 0 ? null : mVideos.get(position - 1).videoId;
        Account account = mAccount;
        List<Video> before = mOriginal;
        List<Video> after = new ArrayList<>(mVideos);
        mSaving = true;
        mSave = RxHelper.execute(Observable.fromCallable(() ->
                        PlaylistOrderStore.forAccount(account).move(videoId, predecessor))
                        .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()),
                order -> {
                    if (mAccount != account) return;
                    // Playback requests include the playlist index. Use the verified server positions.
                    for (Video video : after) {
                        int index = order.indexOf(video.videoId);
                        if (index >= 0) video.playlistIndex = index;
                    }
                    BrowsePresenter.instance(mActivity).onPlaylistEdited();
                    FeedCache.put(BrowseSection.WATCH_LATER_ID, after);
                    if (mActive.getAsBoolean()) mChanged.run();
                    reset();
                },
                error -> {
                    android.util.Log.w("WatchLaterReorder", "Unable to save the playlist position", error);
                    if (mAccount != account) return;
                    if (mActive.getAsBoolean()) {
                        mVideos.clear();
                        mVideos.addAll(before);
                        mChanged.run();
                        MobileSnackbar.showTransient(mActivity,
                                mActivity.getString(R.string.mobile_watch_later_reorder_failed), null, null);
                    }
                    FeedCache.put(BrowseSection.WATCH_LATER_ID, before);
                    BrowsePresenter.instance(mActivity).onPlaylistEdited();
                    reset();
                });
    }

    private void reset() {
        mOriginal = null;
        mMoved = null;
        mAccount = null;
        mSaving = false;
        mEditingChanged.accept(false);
    }

    @Override public void onAccountChanged(Account account) {
        mActivity.runOnUiThread(() -> {
            if (mOriginal == null || mAccount == account) return;
            if (mSave != null) mSave.dispose();
            reset();
        });
    }

    @Override public void onDestroy(@NonNull LifecycleOwner owner) {
        if (mSave != null) mSave.dispose();
        MediaServiceManager.instance().removeAccountListener(this);
    }
}
