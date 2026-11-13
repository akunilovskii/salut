package com.newtube.mobile.ui.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.os.Looper;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;

import java.time.Duration;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MiniPlayerCloseTest {
    private final MiniPlayerClose mClose = new MiniPlayerClose();
    private boolean mPlaying = true;
    private int mFinishes;
    private int mAborts;

    private void start() {
        mClose.start(() -> mPlaying = false, () -> mPlaying = true,
                () -> mAborts++, () -> mFinishes++);
    }

    @Test public void pausesBeforeAnimationAndClosesWithoutAnimationCallback() {
        start();
        assertFalse(mPlaying);
        assertEquals(0, mFinishes);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300));
        assertEquals(1, mFinishes);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        assertEquals(1, mFinishes);
    }

    @Test public void completedAnimationCancelsFallback() {
        start();
        mClose.complete();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        assertFalse(mPlaying);
        assertEquals(0, mFinishes);
        assertEquals(0, mAborts);
    }

    @Test public void newVideoCancelsOldTeardownAndRestoresPlayback() {
        start();
        mClose.cancel();
        assertTrue(mPlaying);
        assertEquals(1, mAborts);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        assertEquals(0, mFinishes);
        mClose.cancel();
        assertEquals(1, mAborts);
    }

    @Test public void newCloseCannotInheritPreviousTimeout() {
        start();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200));
        start();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
        assertEquals(0, mFinishes);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200));
        assertEquals(1, mFinishes);
    }
}
