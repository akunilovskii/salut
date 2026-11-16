package com.newtube.mobile.ui.playback;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/** Keeps playlist details above a pinned heading and hands pull-down travel back to them. */
public final class PlaylistWatchScrollView extends NestedScrollView {
    private View mHeading;
    private RecyclerView mList;
    private boolean mPlaylistMode;
    private boolean mCollapsePending;
    private int mCollapseOffset;
    private int mPendingPosition = RecyclerView.NO_POSITION;
    private int mContentBottomPadding;

    public PlaylistWatchScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    void bindPlaylist(View heading, RecyclerView list) {
        mHeading = heading;
        mList = list;
        mContentBottomPadding = getChildAt(0).getPaddingBottom();
        list.getAdapter().registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override public void onChanged() { requestLayout(); }
            @Override public void onItemRangeInserted(int start, int count) { requestLayout(); }
        });
    }

    void setPlaylistMode(boolean enabled) {
        if (mPlaylistMode == enabled) return;
        mPlaylistMode = enabled;
        mCollapsePending = enabled;
        mPendingPosition = RecyclerView.NO_POSITION;
        View content = getChildAt(0);
        content.setPadding(content.getPaddingLeft(), content.getPaddingTop(), content.getPaddingRight(),
                enabled ? 0 : mContentBottomPadding);
        mList.setNestedScrollingEnabled(enabled);
        ViewGroup.LayoutParams params = mList.getLayoutParams();
        params.height = enabled ? Math.max(1, getHeight() - mHeading.getHeight())
                : ViewGroup.LayoutParams.WRAP_CONTENT;
        mList.setLayoutParams(params);
        if (!enabled) scrollTo(0, 0);
        requestLayout();
    }

    void positionPlaylistItem(int position) {
        if (!mPlaylistMode || position < 0) return;
        mPendingPosition = position;
        mCollapsePending = true;
        requestLayout();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        if (mPlaylistMode && mHeading != null && MeasureSpec.getSize(heightSpec) > 0) {
            int width = Math.max(0, MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight());
            int headingHeightSpec = ViewGroup.getChildMeasureSpec(
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), 0, mHeading.getLayoutParams().height);
            mHeading.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), headingHeightSpec);
            mList.getLayoutParams().height = Math.max(1, MeasureSpec.getSize(heightSpec)
                    - getPaddingTop() - getPaddingBottom() - mHeading.getMeasuredHeight());
        }
        super.onMeasure(widthSpec, heightSpec);
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        boolean keepCollapsed = mPlaylistMode && (mCollapsePending || getScrollY() >= mCollapseOffset);
        super.onLayout(changed, left, top, right, bottom);
        if (!mPlaylistMode || mHeading == null) return;
        mCollapseOffset = mHeading.getTop();
        if (keepCollapsed) scrollTo(0, mCollapseOffset);
        mCollapsePending = false;
        if (mPendingPosition != RecyclerView.NO_POSITION && mList.getAdapter() != null
                && mPendingPosition < mList.getAdapter().getItemCount()) {
            ((LinearLayoutManager) mList.getLayoutManager()).scrollToPositionWithOffset(mPendingPosition, 0);
            mPendingPosition = RecyclerView.NO_POSITION;
        }
    }

    @Override public void onNestedPreScroll(@NonNull View target, int dx, int dy,
            @NonNull int[] consumed, int type) {
        // Upward travel hides the details before moving rows. Downward unconsumed travel
        // reaches NestedScrollView only after the playlist itself reaches its first row.
        if (mPlaylistMode && target == mList && dy > 0 && getScrollY() < mCollapseOffset) {
            int travel = Math.min(dy, mCollapseOffset - getScrollY());
            scrollBy(0, travel);
            consumed[1] += travel;
        }
        super.onNestedPreScroll(target, dx, dy - consumed[1], consumed, type);
    }
}
