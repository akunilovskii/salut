package com.newtube.mobile.ui.common;

import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.smartyoutubetv2.tv.R;

/** Keeps feed edges flush while retaining equal gaps between grid columns. */
public final class FeedCardSpacing extends RecyclerView.ItemDecoration {
    private final int mGap;

    private FeedCardSpacing(int gap) {
        mGap = gap;
    }

    public static void attach(RecyclerView grid) {
        int gap = 2 * grid.getResources().getDimensionPixelSize(R.dimen.mobile_card_spacing);
        grid.addItemDecoration(new FeedCardSpacing(gap));
    }

    @Override public void getItemOffsets(@NonNull Rect out, @NonNull View view,
            @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        out.setEmpty();
        if (view.getId() != R.id.video_card_root
                || !(parent.getLayoutManager() instanceof GridLayoutManager)
                || !(view.getLayoutParams() instanceof GridLayoutManager.LayoutParams)) {
            return;
        }
        GridLayoutManager manager = (GridLayoutManager) parent.getLayoutManager();
        GridLayoutManager.LayoutParams params = (GridLayoutManager.LayoutParams) view.getLayoutParams();
        int index = params.getSpanIndex();
        if (index < 0) {
            return;
        }
        int columns = manager.getSpanCount();
        int start = mGap * index / columns;
        int end = mGap - mGap * (index + params.getSpanSize()) / columns;
        if (ViewCompat.getLayoutDirection(parent) == ViewCompat.LAYOUT_DIRECTION_RTL) {
            out.set(end, 0, start, 0);
        } else {
            out.set(start, 0, end, 0);
        }
    }
}
