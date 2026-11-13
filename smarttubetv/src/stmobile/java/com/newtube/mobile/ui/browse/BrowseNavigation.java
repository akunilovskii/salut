package com.newtube.mobile.ui.browse;

import android.view.Menu;
import android.view.MenuItem;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;

import java.util.function.IntConsumer;

/** Coordinates selection between the You toolbar button and bottom destinations. */
final class BrowseNavigation {
    private final MaterialButton mYou;
    private final BottomNavigationView mBottom;
    private final int mYouId;
    private final IntConsumer mSelect;
    private final IntConsumer mReselect;
    private int mSelectedId;
    private boolean mUpdating;
    private int mActionId;
    private Runnable mAction;

    BrowseNavigation(MaterialButton you, BottomNavigationView bottom,
                     int homeId, int youId, IntConsumer select, IntConsumer reselect) {
        mYou = you;
        mBottom = bottom;
        mYouId = youId;
        mSelect = select;
        mReselect = reselect;
        mSelectedId = homeId;
        you.setOnClickListener(view -> choose(youId));
        bottom.setOnItemSelectedListener(item -> {
            if (item.getItemId() == mActionId && mAction != null) {
                if (!mUpdating) {
                    mAction.run();
                }
                return false;
            }
            if (!mUpdating) {
                choose(item.getItemId());
            }
            return true;
        });
        bottom.setOnItemReselectedListener(item -> {
            if (!mUpdating) {
                if (item.getItemId() == mActionId && mAction != null) {
                    mAction.run();
                } else {
                    choose(item.getItemId());
                }
            }
        });
    }

    void setActionItem(int itemId, Runnable action) {
        mActionId = itemId;
        mAction = action;
    }

    private void choose(int itemId) {
        if (itemId == mSelectedId) {
            mReselect.accept(itemId);
        } else {
            mSelect.accept(itemId);
        }
        // The You button toggles before onClick, so reassert its destination's checked state.
        sync(mSelectedId);
    }

    void sync(int itemId) {
        mSelectedId = itemId;
        mUpdating = true;
        try {
            mYou.setChecked(itemId == mYouId);
            Menu menu = mBottom.getMenu();
            // Exclusive MenuItems cannot be unchecked individually while a toolbar item is active.
            menu.setGroupCheckable(Menu.NONE, true, false);
            for (int i = 0; i < menu.size(); i++) {
                MenuItem item = menu.getItem(i);
                item.setChecked(item.getItemId() == itemId && item.getItemId() != mActionId);
            }
            menu.setGroupCheckable(Menu.NONE, true, true);
        } finally {
            mUpdating = false;
        }
    }
}
