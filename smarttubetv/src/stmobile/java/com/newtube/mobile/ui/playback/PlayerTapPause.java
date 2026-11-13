package com.newtube.mobile.ui.playback;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Defers a surface-tap pause without letting it affect a later video or control action. */
final class PlayerTapPause {
    private final Supplier<?> currentVideo;
    private final BooleanSupplier isPlaying;
    private final BooleanSupplier canPause;
    private final Runnable pause;
    private Object pendingVideo;

    PlayerTapPause(Supplier<?> currentVideo, BooleanSupplier isPlaying,
            BooleanSupplier canPause, Runnable pause) {
        this.currentVideo = currentVideo;
        this.isPlaying = isPlaying;
        this.canPause = canPause;
        this.pause = pause;
    }

    void onOverlayTap(boolean revealing) {
        pendingVideo = revealing && isPlaying.getAsBoolean() ? currentVideo.get() : null;
    }

    void onSingleTapConfirmed() {
        Object video = pendingVideo;
        cancel();
        if (video != null && video == currentVideo.get()
                && isPlaying.getAsBoolean() && canPause.getAsBoolean()) {
            pause.run();
        }
    }

    void cancel() {
        pendingVideo = null;
    }
}
