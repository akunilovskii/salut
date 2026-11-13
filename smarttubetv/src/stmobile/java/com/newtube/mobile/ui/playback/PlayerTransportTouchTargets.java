package com.newtube.mobile.ui.playback;

import android.graphics.Rect;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewGroup;

import com.liskovsoft.smartyoutubetv2.tv.R;

final class PlayerTransportTouchTargets {
    private PlayerTransportTouchTargets() {
    }

    static void bind(View transport) {
        for (int id : new int[] {R.id.mobile_player_previous_touch_area,
                R.id.mobile_player_center_touch_area, R.id.mobile_player_next_touch_area}) {
            ViewGroup target = transport.findViewById(id);
            View button = target.getChildAt(0);
            target.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                    target.setTouchDelegate(new TouchDelegate(new Rect(0, 0, right - left, bottom - top), button)));
        }
    }
}
