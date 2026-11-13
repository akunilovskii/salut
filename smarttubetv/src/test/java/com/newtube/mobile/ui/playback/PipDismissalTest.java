package com.newtube.mobile.ui.playback;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PipDismissalTest {
    private final PipDismissal mDismissal = new PipDismissal();

    @Test public void dismissalClosesWhenExitArrivesBeforeStop() {
        mDismissal.onEntered();
        assertFalse(mDismissal.onExited());
        assertTrue(mDismissal.onStopped());
        assertFalse(mDismissal.onStopped());
    }

    @Test public void dismissalClosesWhenStopArrivesBeforeExit() {
        mDismissal.onEntered();
        assertFalse(mDismissal.onStopped());
        assertTrue(mDismissal.onExited());
        assertFalse(mDismissal.onExited());
    }

    @Test public void expansionDoesNotCloseAndLaterBackgroundingIsIndependent() {
        mDismissal.onEntered();
        assertFalse(mDismissal.onExited());
        mDismissal.onResumed();
        assertFalse(mDismissal.onStopped());
    }

    @Test public void expansionWithResumeBeforeExitDoesNotClose() {
        mDismissal.onEntered();
        mDismissal.onResumed();
        assertFalse(mDismissal.onExited());
        assertFalse(mDismissal.onStopped());
    }

    @Test public void ordinaryBackgroundingDoesNotCloseAudio() {
        mDismissal.onResumed();
        assertFalse(mDismissal.onStopped());
        assertFalse(mDismissal.onExited());
    }

    @Test public void anotherPipSessionAfterExpansionCanStillBeDismissed() {
        mDismissal.onEntered();
        assertFalse(mDismissal.onExited());
        mDismissal.onResumed();
        mDismissal.onEntered();
        assertFalse(mDismissal.onStopped());
        assertTrue(mDismissal.onExited());
    }
}
