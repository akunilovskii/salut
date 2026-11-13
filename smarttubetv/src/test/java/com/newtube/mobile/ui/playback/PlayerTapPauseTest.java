package com.newtube.mobile.ui.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import com.github.vkay94.dtpv3.DoubleTapPlayerViewImpl;
import com.github.vkay94.dtpv3.PlayerDoubleTapListener;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.LooperMode;

import java.time.Duration;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@LooperMode(LooperMode.Mode.PAUSED)
public class PlayerTapPauseTest {
    private DoubleTapPlayerViewImpl view;
    private PlayerTapPause tapPause;
    private Object video = new Object();
    private boolean playing = true;
    private boolean visible;
    private boolean active = true;
    private int pauses;
    private int doubleTaps;
    private int holdStarts;
    private int holdEnds;
    private long downTime;

    @Before public void setUp() {
        view = new DoubleTapPlayerViewImpl(RuntimeEnvironment.getApplication());
        view.layout(0, 0, 600, 340);
        tapPause = new PlayerTapPause(() -> video, () -> playing, () -> active && visible, () -> {
            playing = false;
            pauses++;
        });
        view.setInstantSingleTap(true);
        view.setOnClickListener(ignored -> {
            tapPause.onOverlayTap(!visible);
            visible = !visible;
        });
        view.setSingleTapConfirmedListener(tapPause::onSingleTapConfirmed);
        view.setDoubleTapBeganListener(x -> {
            tapPause.cancel();
            visible = false;
        });
        view.controller(new PlayerDoubleTapListener() {
            @Override public void onDoubleTapStarted(float x, float y) { doubleTaps++; }
        });
        view.setHoldListener(new DoubleTapPlayerViewImpl.HoldListener() {
            @Override public boolean onHoldStart(float x, float y) { holdStarts++; return true; }
            @Override public void onHoldEnd() { holdEnds++; }
        });
    }

    @Test public void singleTapShowsControlsImmediatelyAndPausesOnlyAfterConfirmation() {
        tap();
        assertTrue(visible);
        assertTrue(playing);
        confirm();
        assertFalse(playing);
        assertEquals(1, pauses);
        confirm();
        assertEquals(1, pauses);
    }

    @Test public void accessibilityClickIsAlreadyAConfirmedPauseAction() {
        view.performClick();
        assertTrue(visible);
        assertFalse(playing);
        assertEquals(1, pauses);
    }

    @Test public void doubleTapKeepsPlaybackRunningForTheSeekGesture() {
        tap();
        idle(80);
        tap();
        confirm();
        assertEquals(1, doubleTaps);
        assertFalse(visible);
        assertTrue(playing);
        assertEquals(0, pauses);
    }

    @Test public void swipeDoesNotRevealControlsOrPausePlayback() {
        event(MotionEvent.ACTION_DOWN, 450, 200);
        idle(40);
        event(MotionEvent.ACTION_MOVE, 200, 200);
        idle(40);
        event(MotionEvent.ACTION_UP, 150, 200);
        confirm();
        assertFalse(visible);
        assertTrue(playing);
        assertEquals(0, pauses);
    }

    @Test public void parentTakingTheGestureForPinchOrSwipeCancelsTheTap() {
        event(MotionEvent.ACTION_DOWN, 450, 200);
        idle(40);
        event(MotionEvent.ACTION_CANCEL, 450, 200);
        confirm();
        assertFalse(visible);
        assertTrue(playing);
        assertEquals(0, pauses);
    }

    @Test public void holdStillStartsAndEndsWithoutAnOverlayTapOrPause() {
        event(MotionEvent.ACTION_DOWN, 450, 200);
        idle(ViewConfiguration.getLongPressTimeout() + ViewConfiguration.getTapTimeout() + 100);
        event(MotionEvent.ACTION_UP, 450, 200);
        confirm();
        assertEquals(1, holdStarts);
        assertEquals(1, holdEnds);
        assertFalse(visible);
        assertTrue(playing);
        assertEquals(0, pauses);
    }

    @Test public void tappingPausedVideoDoesNotResumeIt() {
        playing = false;
        tap();
        confirm();
        assertTrue(visible);
        assertFalse(playing);
        assertEquals(0, pauses);
    }

    @Test public void tappingEmptyOverlaySpaceOnlyHidesIt() {
        visible = true;
        tap();
        confirm();
        assertFalse(visible);
        assertTrue(playing);
        assertEquals(0, pauses);
    }

    @Test public void lateConfirmationCannotPauseAnotherVideo() {
        tap();
        video = new Object();
        confirm();
        assertTrue(playing);
        assertEquals(0, pauses);
    }

    @Test public void leavingThePlayerPreventsTheDeferredPause() {
        tap();
        active = false;
        confirm();
        assertTrue(playing);
        assertEquals(0, pauses);
    }

    @Test public void playButtonActionCancelsThePendingSurfacePause() {
        tap();
        tapPause.cancel();
        playing = true;
        confirm();
        assertTrue(playing);
        assertEquals(0, pauses);
    }

    private void tap() {
        event(MotionEvent.ACTION_DOWN, 450, 200);
        idle(20);
        event(MotionEvent.ACTION_UP, 450, 200);
    }

    private void event(int action, float x, float y) {
        if (action == MotionEvent.ACTION_DOWN) downTime = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0);
        view.onTouchEvent(event);
        event.recycle();
    }

    private void confirm() {
        idle(ViewConfiguration.getDoubleTapTimeout() + 50);
    }

    private void idle(long millis) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis));
    }
}
