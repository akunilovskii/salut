package com.newtube.mobile.ui.browse;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.MenuItem;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.MenuItemCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.YTSignInPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AccountSelectionPresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.newtube.mobile.accounts.AccountCycle;
import com.newtube.mobile.ui.common.MobileActivity;

import java.util.Objects;

/** Bottom-bar action that cycles directly through the account service's current saved list. */
final class BottomAccountSwitch implements DefaultLifecycleObserver, MediaServiceManager.AccountChangeListener {
    static final int ITEM_ID = 2_000_001;
    private final MobileActivity mActivity;
    private final BottomNavigationView mBar;
    private final RequestManager mGlide;
    private final Runnable mOnChanged;
    private CustomTarget<Bitmap> mAvatar;
    private MenuItem mBoundItem;
    private String mBoundPhoto;
    private Account mBoundAccount;
    private int mGeneration;
    private boolean mSwitching;

    BottomAccountSwitch(MobileActivity activity, BottomNavigationView bar, Runnable onChanged) {
        mActivity = activity;
        mBar = bar;
        mOnChanged = onChanged;
        mGlide = Glide.with(activity);
        alignMenuToEnd(bar);
        activity.getLifecycle().addObserver(this);
        MediaServiceManager.instance().addAccountListener(this);
    }

    static void alignMenuToEnd(BottomNavigationView bar) {
        View menu = bar.getChildAt(0);
        if (menu != null && menu.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams layout = (FrameLayout.LayoutParams) menu.getLayoutParams();
            layout.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
            menu.setLayoutParams(layout);
        }
    }

    void next() {
        if (mSwitching) {
            return;
        }
        mSwitching = true;
        MediaServiceManager.instance().loadAccounts(accounts -> {
            try {
                Account next = AccountCycle.next(accounts);
                if (next == null) {
                    YTSignInPresenter.instance(mActivity).start();
                } else if (!next.isSelected()) {
                    AccountSelectionPresenter.instance(mActivity).selectAccount(next);
                }
                refresh();
            } finally {
                mSwitching = false;
            }
        });
    }

    void refresh() {
        MenuItem item = mBar.getMenu().findItem(ITEM_ID);
        if (item == null || mActivity.isDestroyed()) {
            return;
        }
        Account account = MediaServiceManager.instance().getSelectedAccount();
        String photo = account == null ? null : account.getAvatarImageUrl();
        String name = account == null ? null : account.getName();
        MenuItemCompat.setContentDescription(item, name == null
                ? mActivity.getString(R.string.mobile_account_cycle_signed_out)
                : mActivity.getString(R.string.mobile_account_cycle_current, name));
        if (item == mBoundItem && account == mBoundAccount && photo != null
                && !photo.isEmpty() && Objects.equals(photo, mBoundPhoto)) {
            return;
        }
        mGeneration++;
        int generation = mGeneration;
        mBoundItem = item;
        mBoundAccount = account;
        mBoundPhoto = photo;
        item.setIcon(new AvatarDrawable(ContextCompat.getDrawable(mActivity, R.drawable.ic_mobile_account)));
        if (mAvatar != null) {
            mGlide.clear(mAvatar);
            mAvatar = null;
        }
        if (photo == null || photo.isEmpty()) {
            return;
        }
        int size = Math.round(24 * mActivity.getResources().getDisplayMetrics().density);
        mAvatar = new CustomTarget<Bitmap>(size, size) {
            @Override
            public void onResourceReady(@NonNull Bitmap bitmap, @Nullable Transition<? super Bitmap> transition) {
                if (generation == mGeneration && !mActivity.isDestroyed()) {
                    item.setIcon(new AvatarDrawable(new BitmapDrawable(mActivity.getResources(), bitmap)));
                }
            }

            @Override public void onLoadCleared(@Nullable Drawable placeholder) { }
        };
        mGlide.asBitmap().load(photo).circleCrop().into(mAvatar);
    }

    @Override public void onAccountChanged(Account account) {
        // Metadata refreshes may arrive from the account service's worker thread.
        mActivity.runOnUiThread(() -> {
            if (!mActivity.isDestroyed() && !mActivity.isFinishing()) {
                refresh();
                mOnChanged.run();
            }
        });
    }

    @Override public void onDestroy(@NonNull LifecycleOwner owner) {
        mGeneration++;
        if (mAvatar != null) {
            mGlide.clear(mAvatar);
        }
        MediaServiceManager.instance().removeAccountListener(this);
    }
}
