package com.newtube.mobile.ui.playback;

import android.view.View;

import com.liskovsoft.smartyoutubetv2.tv.R;

/** Shared geometry for the browse and detail mini-player controls. */
public final class MiniPlayerControls {
    private MiniPlayerControls() {}

    public static void bind(View miniPlayer) {
        // Match the reference's pixel-offset rounding at fractional display densities.
        int padding = miniPlayer.getResources().getDimensionPixelOffset(R.dimen.mobile_mini_button_icon_padding);
        for (int id : new int[] {R.id.mobile_mini_play_pause, R.id.mobile_mini_close}) {
            miniPlayer.findViewById(id).setPadding(padding, padding, padding, padding);
        }
    }
}
