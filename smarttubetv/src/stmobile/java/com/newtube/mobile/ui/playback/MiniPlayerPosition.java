package com.newtube.mobile.ui.playback;

/** Session corner independent of a host's size and system insets. */
public final class MiniPlayerPosition {
    private boolean mRightEdge = true;
    private boolean mBottomEdge = true;

    public float left(float minimum, float maximum) {
        return mRightEdge ? Math.max(minimum, maximum) : minimum;
    }

    public float top(float minimum, float maximum) {
        return mBottomEdge ? Math.max(minimum, maximum) : minimum;
    }

    public void remember(float left, float top, float minimumX, float maximumX,
                         float minimumY, float maximumY) {
        // A temporarily constrained window must not overwrite the chosen corner.
        if (maximumX > minimumX) {
            mRightEdge = left >= minimumX + (maximumX - minimumX) / 2f;
        }
        if (maximumY > minimumY) {
            mBottomEdge = top >= minimumY + (maximumY - minimumY) / 2f;
        }
    }

    public void reset() {
        mRightEdge = true;
        mBottomEdge = true;
    }

    static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(Math.max(minimum, maximum), value));
    }
}
