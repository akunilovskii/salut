package com.newtube.mobile.ui.browse;

import android.graphics.drawable.Drawable;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.content.res.AppCompatResources;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.liskovsoft.smartyoutubetv2.tv.R;

/** YouTube's 12 dp badge: an 8 dp red center and a 2 dp background-colored rim. */
final class SubscriptionsDot {
    private View mIcon;
    private ViewGroup mHost;
    private Drawable mDot;
    private final View.OnLayoutChangeListener mLayout = (view, left, top, right, bottom,
            oldLeft, oldTop, oldRight, oldBottom) -> position();

    void bind(BottomNavigationView bar, int itemId, boolean visible) {
        View item = bar.findViewById(itemId);
        View icon = !(item instanceof ViewGroup) ? null
                : item.findViewById(com.google.android.material.R.id.navigation_bar_item_icon_view);
        if (icon != mIcon) {
            clear();
            if (icon == null) return;
            mIcon = icon;
            mHost = (ViewGroup) item;
            mDot = AppCompatResources.getDrawable(icon.getContext(), R.drawable.ic_subscriptions_new_content).mutate();
            icon.addOnLayoutChangeListener(mLayout);
            mHost.addOnLayoutChangeListener(mLayout);
            // The 12 dp rim extends 2 dp beyond the 24 dp icon, so use the item's overlay.
        }
        if (mDot != null) {
            mDot.setVisible(visible, false);
            position();
            // ViewOverlay draws its entries even when Drawable visibility is false.
            if (visible) {
                mHost.getOverlay().add(mDot);
            } else {
                mHost.getOverlay().remove(mDot);
            }
            mHost.invalidate();
        }
    }

    private void position() {
        if (mIcon == null || mDot == null) return;
        float density = mIcon.getResources().getDisplayMetrics().density;
        Rect icon = new Rect(0, 0, mIcon.getWidth(), mIcon.getHeight());
        mHost.offsetDescendantRectToMyCoords(mIcon, icon);
        int size = Math.round(12 * density);
        int left = icon.right - Math.round(10 * density);
        int top = icon.top - Math.round(2 * density);
        mDot.setBounds(left, top, left + size, top + size);
    }

    void clear() {
        if (mIcon != null) {
            mIcon.removeOnLayoutChangeListener(mLayout);
            mHost.removeOnLayoutChangeListener(mLayout);
            mHost.getOverlay().remove(mDot);
        }
        mIcon = null;
        mHost = null;
        mDot = null;
    }
}
