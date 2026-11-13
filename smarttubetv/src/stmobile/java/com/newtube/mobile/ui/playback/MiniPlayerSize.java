package com.newtube.mobile.ui.playback;

import android.util.DisplayMetrics;
import android.view.View;

/** Default YouTube mini-player proportions, measured in portrait and landscape on Pixel 10. */
public final class MiniPlayerSize {
    private MiniPlayerSize() {
    }

    public static int width(View view) {
        View window = view.getRootView();
        DisplayMetrics metrics = view.getResources().getDisplayMetrics();
        int width = window.getWidth() > 0 ? window.getWidth() : metrics.widthPixels;
        int height = window.getHeight() > 0 ? window.getHeight() : metrics.heightPixels;
        return width(width, height, metrics.density);
    }

    static int width(int windowWidth, int windowHeight, float density) {
        return Math.max(1, Math.round(Math.min(windowWidth, windowHeight) / 2f - 8f * density));
    }

    public static int height(int width) {
        return Math.max(1, Math.round(width * 9f / 16f));
    }
}
