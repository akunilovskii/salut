package com.newtube.mobile.ui.browse;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.smartyoutubetv2.tv.R;

final class WatchLaterSwipe {
    interface Callback {
        boolean canRemove(RecyclerView.ViewHolder holder);
        void onRemove(RecyclerView.ViewHolder holder);
        boolean canDrag(RecyclerView.ViewHolder holder);
        boolean onMove(RecyclerView.ViewHolder from, RecyclerView.ViewHolder to);
        void onDragStart(RecyclerView.ViewHolder holder);
        void onDragEnd();
    }

    private WatchLaterSwipe() {
    }

    static ItemTouchHelper attach(RecyclerView recycler, Callback callback) {
        Paint background = new Paint();
        background.setColor(ContextCompat.getColor(recycler.getContext(), R.color.mobile_color_primary_dark));
        Drawable delete = ContextCompat.getDrawable(recycler.getContext(), R.drawable.ic_comment_delete).mutate();
        delete.setTint(Color.WHITE);
        float density = recycler.getResources().getDisplayMetrics().density;
        int size = Math.round(24 * density);
        int inset = Math.round(20 * density);
        ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, ItemTouchHelper.LEFT) {
            private boolean mDragging;

            @Override public boolean isLongPressDragEnabled() { return false; }

            @Override public int getDragDirs(@NonNull RecyclerView view, @NonNull RecyclerView.ViewHolder holder) {
                return callback.canDrag(holder) ? ItemTouchHelper.UP | ItemTouchHelper.DOWN : 0;
            }

            @Override public boolean canDropOver(@NonNull RecyclerView view, @NonNull RecyclerView.ViewHolder from,
                                                 @NonNull RecyclerView.ViewHolder to) {
                return callback.canDrag(to);
            }

            @Override public void onSelectedChanged(RecyclerView.ViewHolder holder, int state) {
                if (state == ItemTouchHelper.ACTION_STATE_DRAG && holder != null) {
                    mDragging = true;
                    callback.onDragStart(holder);
                }
                super.onSelectedChanged(holder, state);
            }

            @Override public void clearView(@NonNull RecyclerView view, @NonNull RecyclerView.ViewHolder holder) {
                super.clearView(view, holder);
                if (mDragging) {
                    mDragging = false;
                    callback.onDragEnd();
                }
            }
            @Override
            public int getSwipeDirs(@NonNull RecyclerView view, @NonNull RecyclerView.ViewHolder holder) {
                return callback.canRemove(holder) ? ItemTouchHelper.LEFT : 0;
            }

            @Override
            public boolean onMove(@NonNull RecyclerView view, @NonNull RecyclerView.ViewHolder from,
                                  @NonNull RecyclerView.ViewHolder to) {
                return callback.onMove(from, to);
            }

            @Override
            public void onMoved(@NonNull RecyclerView view, @NonNull RecyclerView.ViewHolder from,
                                int fromPosition, @NonNull RecyclerView.ViewHolder to,
                                int toPosition, int x, int y) {
                // GridLayoutManager.prepareForDrop reanchors the viewport on every swap when
                // the playlist has a full-span header. The target is already visible, and
                // ItemTouchHelper's edge scrolling can reach further rows without that jump.
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder holder, int direction) {
                callback.onRemove(holder);
            }

            @Override
            public void onChildDraw(@NonNull Canvas canvas, @NonNull RecyclerView view,
                                    @NonNull RecyclerView.ViewHolder holder, float dx, float dy,
                                    int state, boolean active) {
                if (dx < 0 && callback.canRemove(holder)) {
                    int right = holder.itemView.getRight();
                    int top = holder.itemView.getTop();
                    int bottom = holder.itemView.getBottom();
                    int save = canvas.save();
                    canvas.clipRect(right + dx, top, right, bottom);
                    canvas.drawRect(right + dx, top, right, bottom, background);
                    int iconTop = top + (bottom - top - size) / 2;
                    delete.setBounds(right - inset - size, iconTop, right - inset, iconTop + size);
                    delete.draw(canvas);
                    canvas.restoreToCount(save);
                }
                super.onChildDraw(canvas, view, holder, dx, dy, state, active);
            }
        });
        helper.attachToRecyclerView(recycler);
        return helper;
    }
}
