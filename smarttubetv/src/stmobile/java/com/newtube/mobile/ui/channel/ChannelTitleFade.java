package com.newtube.mobile.ui.channel;

import android.view.View;
import android.widget.TextView;
import com.google.android.material.appbar.AppBarLayout;
import com.liskovsoft.smartyoutubetv2.tv.R;

/** Maps the last part of channel-card scrolling directly to toolbar-title opacity. */
final class ChannelTitleFade {
    private final TextView title;

    ChannelTitleFade(TextView title) {
        this.title = title;
        title.setVisibility(View.VISIBLE);
        title.setAlpha(0f);
        title.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    static void bind(TextView title, AppBarLayout appBar) {
        ChannelTitleFade fade = new ChannelTitleFade(title);
        View tabs = appBar.findViewById(R.id.mobile_channel_tabs);
        appBar.addOnOffsetChangedListener((bar, offset) -> fade.onOffset(offset, bar.getTotalScrollRange(),
                tabs != null ? tabs.getHeight() : 0));
    }

    void onOffset(int offset, int range, int tabHeight) {
        float distance = Math.min(Math.max(0, tabHeight), range);
        float remaining = range - Math.min(range, Math.max(0L, -(long) offset));
        float alpha = distance > 0 ? Math.max(0f, Math.min(1f, 1f - remaining / distance)) : range > 0 && remaining <= 0 ? 1f : 0f;
        title.setAlpha(alpha);
        title.setImportantForAccessibility(alpha > 0f ? View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                : View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
}
