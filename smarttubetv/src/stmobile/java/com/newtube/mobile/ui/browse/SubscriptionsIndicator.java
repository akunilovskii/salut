package com.newtube.mobile.ui.browse;

import android.content.Context;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.core.view.MenuItemCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;
import com.newtube.mobile.subscriptions.SubscriptionSeenStore;
import com.newtube.mobile.subscriptions.SubscriptionUpdates;
import com.newtube.mobile.ui.common.MobileActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/** Foreground-only subscription checks; polling does not acknowledge the tab. */
final class SubscriptionsIndicator implements DefaultLifecycleObserver, MediaServiceManager.AccountChangeListener {
    private static final long CHECK_INTERVAL_MS = 5 * 60_000;
    private final MobileActivity mActivity;
    private final BottomNavigationView mBar;
    private final int mItemId;
    private final BooleanSupplier mShowingSubscriptions;
    private final SubscriptionSeenStore mStore;
    private final SubscriptionsDot mDot = new SubscriptionsDot();
    private final Runnable mCheck = this::check;
    private SubscriptionUpdates mUpdates;
    private Account mAccount;
    private String mKey;
    private boolean mResumed;
    private boolean mForeground;
    private int mGeneration;
    private Disposable mPoll;
    private final DefaultLifecycleObserver mAppLifecycle = new DefaultLifecycleObserver() {
        @Override public void onStart(@NonNull LifecycleOwner owner) {
            mForeground = true;
            check();
        }

        @Override public void onStop(@NonNull LifecycleOwner owner) {
            mForeground = false;
            mBar.removeCallbacks(mCheck);
            cancelPoll();
        }
    };

    SubscriptionsIndicator(MobileActivity activity, BottomNavigationView bar, int itemId, BooleanSupplier showing) {
        mActivity = activity;
        mBar = bar;
        mItemId = itemId;
        mShowingSubscriptions = showing;
        mStore = new SubscriptionSeenStore(activity.getSharedPreferences("newtube_subscriptions_seen", Context.MODE_PRIVATE));
        loadAccount();
        ProcessLifecycleOwner.get().getLifecycle().addObserver(mAppLifecycle);
        activity.getLifecycle().addObserver(this);
        MediaServiceManager.instance().addAccountListener(this);
    }

    void tapped() {
        loadAccount();
        cancelPoll();
        if (mUpdates != null) mUpdates.tapped();
    }

    void cancelTap() {
        if (mUpdates != null) mUpdates.cancelTap();
    }

    boolean isAwaitingRefresh() {
        return mUpdates != null && mAccount == MediaServiceManager.instance().getSelectedAccount()
                && mUpdates.isAwaitingRefresh();
    }

    void refreshed(List<Video> videos) {
        if (!mResumed || !mShowingSubscriptions.getAsBoolean()
                || mUpdates == null || mAccount != MediaServiceManager.instance().getSelectedAccount()) return;
        List<String> ids = new ArrayList<>();
        if (videos != null) for (Video video : videos) {
            if (video.hasVideo() && !video.isShorts && !video.isUpcoming) ids.add(video.videoId);
        }
        mUpdates.refreshed(ids);
        persistAndRender();
    }

    void render() {
        MenuItem item = mBar.getMenu().findItem(mItemId);
        if (item == null) {
            mDot.clear();
            return;
        }
        mBar.removeBadge(mItemId);
        boolean visible = mUpdates != null && mUpdates.hasNewVideos();
        MenuItemCompat.setContentDescription(item, visible
                ? item.getTitle() + ", " + mActivity.getString(R.string.mobile_subscriptions_new_videos) : null);
        mDot.bind(mBar, mItemId, visible);
    }

    private void loadAccount() {
        Account account = MediaServiceManager.instance().getSelectedAccount();
        String key = SubscriptionSeenStore.accountKey(account);
        if (java.util.Objects.equals(mKey, key) && mUpdates != null) {
            mAccount = account;
            return;
        }
        cancelPoll();
        mAccount = account;
        mKey = key;
        mUpdates = key == null ? null : mStore.load(key);
        render();
    }

    private void check() {
        mBar.removeCallbacks(mCheck);
        if (!mForeground) return;
        mBar.postDelayed(mCheck, CHECK_INTERVAL_MS);
        loadAccount();
        if (mUpdates == null || mUpdates.isAwaitingRefresh()) return;
        cancelPoll();
        int generation = mGeneration;
        Account account = mAccount;
        mPoll = Observable.fromCallable(() -> {
            if (MediaServiceManager.instance().getSelectedAccount() != account) {
                throw new IllegalStateException("The selected account changed");
            }
            MediaGroup group = YouTubeServiceManager.instance().getContentService().getSubscriptions();
            if (group == null) throw new IllegalStateException("Subscriptions were not available");
            List<String> ids = new ArrayList<>();
            if (group.getMediaItems() != null) for (MediaItem item : group.getMediaItems()) {
                if (item.getVideoId() != null && !item.isShorts() && !item.isUpcoming()) ids.add(item.getVideoId());
            }
            return ids;
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(ids -> {
            if (generation != mGeneration || mAccount != account || !mForeground) return;
            mUpdates.observe(ids);
            persistAndRender();
        }, error -> {
            // Failed checks retain the previous dot and acknowledged snapshot until a later check.
        });
    }

    private void persistAndRender() {
        mStore.save(mKey, mUpdates);
        render();
    }

    private void cancelPoll() {
        mGeneration++;
        if (mPoll != null) mPoll.dispose();
    }

    @Override public void onResume(@NonNull LifecycleOwner owner) {
        mResumed = true;
        if (mForeground && (mPoll == null || mPoll.isDisposed())) check();
    }

    @Override public void onPause(@NonNull LifecycleOwner owner) {
        mResumed = false;
        cancelTap();
    }

    @Override public void onAccountChanged(Account account) {
        mActivity.runOnUiThread(() -> {
            if (mActivity.isDestroyed()) return;
            loadAccount();
            if (mForeground) check();
        });
    }

    @Override public void onDestroy(@NonNull LifecycleOwner owner) {
        mDot.clear();
        mBar.removeCallbacks(mCheck);
        cancelPoll();
        ProcessLifecycleOwner.get().getLifecycle().removeObserver(mAppLifecycle);
        MediaServiceManager.instance().removeAccountListener(this);
    }
}
