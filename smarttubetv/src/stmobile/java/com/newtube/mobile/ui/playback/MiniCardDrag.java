package com.newtube.mobile.ui.playback;

import android.annotation.SuppressLint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.liskovsoft.smartyoutubetv2.tv.R;

/** Moves the existing video card without replacing its surface or interrupting playback. */
public final class MiniCardDrag implements View.OnTouchListener {
    public interface Callback {
        boolean canDrag();

        void onPositionChanged();
    }

    private static final int GAP_DP = 8;
    private static final long SNAP_DURATION_MS = 500;

    private final View mCard;
    private final ViewGroup mParent;
    private final Callback mCallback;
    private final MiniPlayerPosition mPosition;
    private final int mTouchSlop;
    private final int mMaximumFlingVelocity;
    private final int mGap;
    private final float mDensity;
    private VelocityTracker mVelocityTracker;
    private float mDownX;
    private float mDownY;
    private float mStartX;
    private float mStartY;
    private boolean mDragging;
    private boolean mIgnoring;
    private boolean mSettling;

    public static MiniCardDrag attach(View card, MiniPlayerPosition position, Callback callback,
                                      View... touchTargets) {
        MiniCardDrag drag = new MiniCardDrag(card, position, callback);
        for (View target : touchTargets) {
            if (target != null) {
                target.setOnTouchListener(drag);
            }
        }
        return drag;
    }

    private MiniCardDrag(View card, MiniPlayerPosition position, Callback callback) {
        mCard = card;
        mParent = (ViewGroup) card.getParent();
        mPosition = position;
        mCallback = callback;
        ViewConfiguration configuration = ViewConfiguration.get(card.getContext());
        mTouchSlop = configuration.getScaledTouchSlop();
        mMaximumFlingVelocity = configuration.getScaledMaximumFlingVelocity();
        mDensity = card.getResources().getDisplayMetrics().density;
        mGap = Math.round(GAP_DP * mDensity);
        View.OnLayoutChangeListener layout = (view, left, top, right, bottom,
                                              oldLeft, oldTop, oldRight, oldBottom) -> {
            if (!mDragging && !mSettling && mCard.getVisibility() == View.VISIBLE
                    && mCallback.canDrag()) {
                restore();
            }
        };
        mParent.addOnLayoutChangeListener(layout);
        mCard.addOnLayoutChangeListener(layout);
    }

    /** Reapply the shared position after a host resumes or its available area changes. */
    public void restore() {
        ViewGroup.LayoutParams parameters = mCard.getLayoutParams();
        int width = MiniPlayerSize.width(mCard);
        int height = MiniPlayerSize.height(width);
        if (parameters.width != width || parameters.height != height) {
            parameters.width = width;
            parameters.height = height;
            mCard.setLayoutParams(parameters);
            return;
        }
        if (mCard.getWidth() <= 0 || mCard.getHeight() <= 0 || mParent.getWidth() <= 0) {
            return;
        }
        RectF bounds = movementBounds();
        moveTo(mPosition.left(bounds.left, bounds.right), mPosition.top(bounds.top, bounds.bottom));
    }

    /** The minimize animation must land at the moved card, including after an expand. */
    @Nullable
    public Rect boundsOnScreen() {
        if (mCard.getWidth() <= 0 || mCard.getHeight() <= 0) {
            return null;
        }
        restore();
        int[] location = new int[2];
        mCard.getLocationOnScreen(location);
        return new Rect(location[0], location[1], location[0] + mCard.getWidth(),
                location[1] + mCard.getHeight());
    }

    /** Stop gesture/snap work before a host folds, closes or animates the card itself. */
    public void stop() {
        mIgnoring = mDragging;
        mDragging = false;
        mSettling = false;
        mCard.animate().cancel();
        recycleVelocityTracker();
        mParent.requestDisallowInterceptTouchEvent(false);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                recycleVelocityTracker();
                if (!mCallback.canDrag()) {
                    mIgnoring = true;
                    return false;
                }
                mCard.animate().cancel();
                mSettling = false;
                mDownX = event.getRawX();
                mDownY = event.getRawY();
                mStartX = mCard.getX();
                mStartY = mCard.getY();
                mVelocityTracker = VelocityTracker.obtain();
                trackRawMovement(event);
                mDragging = false;
                mIgnoring = false;
                return false;
            case MotionEvent.ACTION_MOVE:
                if (mIgnoring) {
                    return true;
                }
                trackRawMovement(event);
                if (!mDragging) {
                    float dx = event.getRawX() - mDownX;
                    float dy = event.getRawY() - mDownY;
                    if (dx * dx + dy * dy <= mTouchSlop * mTouchSlop || !mCallback.canDrag()) {
                        return false;
                    }
                    mDragging = true;
                    // A drag cancels the original control's click, including pause and close.
                    MotionEvent cancel = MotionEvent.obtain(event);
                    cancel.setAction(MotionEvent.ACTION_CANCEL);
                    view.onTouchEvent(cancel);
                    cancel.recycle();
                    mParent.requestDisallowInterceptTouchEvent(true);
                }
                moveWithFinger(event);
                return true;
            case MotionEvent.ACTION_UP:
                if (mIgnoring) {
                    mIgnoring = false;
                    recycleVelocityTracker();
                    return true;
                }
                if (!mDragging) {
                    recycleVelocityTracker();
                    return false;
                }
                trackRawMovement(event);
                moveWithFinger(event);
                finishDrag(event, true);
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                if (mDragging) {
                    finishDrag(event, false);
                    mIgnoring = true;
                }
                return mIgnoring;
            case MotionEvent.ACTION_CANCEL:
                mIgnoring = false;
                if (mDragging) {
                    finishDrag(event, false);
                    return true;
                }
                recycleVelocityTracker();
                return false;
            default:
                return mDragging || mIgnoring;
        }
    }

    private void moveWithFinger(MotionEvent event) {
        RectF bounds = movementBounds();
        moveTo(MiniPlayerPosition.clamp(mStartX + event.getRawX() - mDownX, bounds.left, bounds.right),
                MiniPlayerPosition.clamp(mStartY + event.getRawY() - mDownY, bounds.top, bounds.bottom));
    }

    private void finishDrag(MotionEvent event, boolean allowFling) {
        mDragging = false;
        mParent.requestDisallowInterceptTouchEvent(false);
        RectF bounds = movementBounds();
        float velocityX = 0f;
        float velocityY = 0f;
        if (allowFling && mVelocityTracker != null) {
            mVelocityTracker.computeCurrentVelocity(1000, mMaximumFlingVelocity);
            velocityX = mVelocityTracker.getXVelocity();
            velocityY = mVelocityTracker.getYVelocity();
        }
        recycleVelocityTracker();
        float releaseX = MiniPlayerFling.releaseCoordinate(mCard.getX(), event.getRawX() - mDownX,
                velocityX, bounds.left, bounds.right, mDensity);
        float releaseY = MiniPlayerFling.releaseCoordinate(mCard.getY(), event.getRawY() - mDownY,
                velocityY, bounds.top, bounds.bottom, mDensity);
        mPosition.remember(releaseX, releaseY, bounds.left, bounds.right, bounds.top, bounds.bottom);
        mSettling = true;
        mCard.animate().translationX(mPosition.left(bounds.left, bounds.right) - mCard.getLeft())
                .translationY(mPosition.top(bounds.top, bounds.bottom) - mCard.getTop())
                .setDuration(SNAP_DURATION_MS).setInterpolator(new OvershootInterpolator(1f))
                .withEndAction(() -> {
                    mSettling = false;
                    mCallback.onPositionChanged();
                }).start();
        mCallback.onPositionChanged();
    }

    private void trackRawMovement(MotionEvent event) {
        if (mVelocityTracker == null) {
            return;
        }
        // Local coordinates change as the card moves; velocity must stay in screen coordinates.
        MotionEvent raw = MotionEvent.obtain(event);
        raw.offsetLocation(event.getRawX() - event.getX(), event.getRawY() - event.getY());
        mVelocityTracker.addMovement(raw);
        raw.recycle();
    }

    private void recycleVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    private void moveTo(float left, float top) {
        mCard.setTranslationX(left - mCard.getLeft());
        mCard.setTranslationY(top - mCard.getTop());
        mCallback.onPositionChanged();
    }

    private RectF movementBounds() {
        float minimumX = mParent.getPaddingLeft() + mGap;
        int[] parentLocation = new int[2];
        mParent.getLocationOnScreen(parentLocation);
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(mParent);
        int statusBottom = insets != null ? insets.getInsets(WindowInsetsCompat.Type.statusBars()
                | WindowInsetsCompat.Type.displayCutout()).top : 0;
        // The host may already start below the status bar; only add the missing clearance.
        float minimumY = Math.max(mParent.getPaddingTop(), statusBottom - parentLocation[1]) + mGap;
        float maximumX = mParent.getWidth() - mParent.getPaddingRight() - mGap - mCard.getWidth();
        float bottom = mParent.getHeight() - mParent.getPaddingBottom() - mGap;
        View navigation = mParent.findViewById(R.id.mobile_bottom_nav);
        if (navigation != null && navigation.getVisibility() == View.VISIBLE && navigation.getHeight() > 0) {
            Rect rectangle = new Rect();
            navigation.getDrawingRect(rectangle);
            mParent.offsetDescendantRectToMyCoords(navigation, rectangle);
            // YouTube docks flush above bottom navigation; upper toolbars remain underneath.
            bottom = Math.min(bottom, rectangle.top);
        }
        return new RectF(minimumX, minimumY, Math.max(minimumX, maximumX),
                Math.max(minimumY, bottom - mCard.getHeight()));
    }
}
