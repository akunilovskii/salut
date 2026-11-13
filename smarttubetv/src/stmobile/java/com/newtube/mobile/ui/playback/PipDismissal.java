package com.newtube.mobile.ui.playback;

/** Distinguishes system PiP dismissal from expansion for either lifecycle callback order. */
final class PipDismissal {
    private boolean mEntered;
    private boolean mStopped;
    private boolean mExitPending;

    void onEntered() {
        mEntered = true;
        mExitPending = false;
    }

    void onResumed() {
        mStopped = false;
        mEntered = false;
        mExitPending = false;
    }

    boolean onStopped() {
        mStopped = true;
        return consumeDismissal();
    }

    boolean onExited() {
        if (!mEntered) return false;
        mEntered = false;
        mExitPending = true;
        return consumeDismissal();
    }

    private boolean consumeDismissal() {
        if (!mStopped || !mExitPending) return false;
        mExitPending = false;
        return true;
    }
}
