package com.newtube.mobile.ui.channel;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.liskovsoft.smartyoutubetv2.tv.R;

/** Aligns full touch targets to compact text rows without taking space from the channel card. */
public final class ChannelHeaderLayout extends FrameLayout {
    private final Rect anchorBounds = new Rect();
    private final Rect letterBounds = new Rect();

    public ChannelHeaderLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, heightSpec);
        View subscription = findViewById(R.id.channel_header_subscription);
        if (subscription == null) return;
        int reserved = subscription.getMeasuredWidth() + Math.round(8 * getResources().getDisplayMetrics().density);
        boolean changed = reserve(R.id.channel_header_handle, reserved);
        changed |= reserve(R.id.channel_header_counts, reserved);
        TextView name = findViewById(R.id.channel_header_name);
        View badge = findViewById(R.id.channel_header_verified);
        View row = findViewById(R.id.channel_header_name_row);
        int badgeSpace = badge.getVisibility() == View.GONE ? 0
                : badge.getMeasuredWidth() + ((ViewGroup.MarginLayoutParams) badge.getLayoutParams()).getMarginStart();
        int nameWidth = Math.max(0, row.getMeasuredWidth() - badgeSpace);
        if (name.getMaxWidth() != nameWidth) {
            name.setMaxWidth(nameWidth);
            changed = true;
        }
        if (changed) super.onMeasure(widthSpec, heightSpec);
    }

    private boolean reserve(int id, int width) {
        View view = findViewById(id);
        if (view == null) return false;
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        if (params.getMarginEnd() == width) return false;
        params.setMarginEnd(width);
        view.setLayoutParams(params);
        return true;
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        TextView name = findViewById(R.id.channel_header_name);
        View badge = findViewById(R.id.channel_header_verified);
        if (badge.getVisibility() != View.GONE) {
            name.getPaint().getTextBounds("H", 0, 1, letterBounds);
            float capCenter = name.getTop() + name.getBaseline() + (letterBounds.top + letterBounds.bottom) / 2f;
            badge.setTranslationY(capCenter - badge.getHeight() / 2f - badge.getTop());
        }
        align(R.id.channel_header_actions, R.id.channel_header_name, false);
        align(R.id.channel_header_subscription, R.id.channel_header_counts, true);
    }

    private void align(int targetId, int anchorId, boolean alignVisibleBottom) {
        View target = findViewById(targetId);
        View anchor = findViewById(anchorId);
        if (target == null || anchor == null) return;
        anchor.getDrawingRect(anchorBounds);
        offsetDescendantRectToMyCoords(anchor, anchorBounds);
        float density = getResources().getDisplayMetrics().density;
        float top;
        if (alignVisibleBottom) {
            top = anchorBounds.bottom - target.getHeight() + 4 * density;
        } else {
            TextView name = (TextView) anchor;
            name.getPaint().getTextBounds("H", 0, 1, letterBounds);
            View more = findViewById(R.id.channel_header_more);
            // The dots start 4 viewport units below the top of their 24 dp canvas.
            top = anchorBounds.top + name.getBaseline() + letterBounds.top - more.getPaddingTop() - 4 * density;
        }
        target.setTranslationY(top - target.getTop());
    }
}
