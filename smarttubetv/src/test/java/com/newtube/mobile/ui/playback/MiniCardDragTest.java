package com.newtube.mobile.ui.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Application;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.liskovsoft.smartyoutubetv2.tv.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.LooperMode;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class, qualifiers = "w400dp-h800dp-mdpi")
@ConscryptMode(ConscryptMode.Mode.OFF)
@LooperMode(LooperMode.Mode.PAUSED)
public class MiniCardDragTest {
    private static final float EPSILON = 0.1f;
    private FrameLayout mParent;
    private FrameLayout mCard;
    private MiniPlayerPosition mPosition;
    private MiniCardDrag mDrag;
    private boolean mCanDrag;

    @Before
    public void setUp() {
        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class);
        Activity activity = controller.get();
        activity.setTheme(android.R.style.Theme_Material_Light_NoActionBar);
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        activity.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        controller.setup();
        mParent = new FrameLayout(activity);
        activity.setContentView(mParent);
        View toolbar = new View(activity);
        toolbar.setId(R.id.mobile_title_bar);
        mParent.addView(toolbar, new FrameLayout.LayoutParams(-1, 56, Gravity.TOP));
        View navigation = new View(activity);
        navigation.setId(R.id.mobile_bottom_nav);
        mParent.addView(navigation, new FrameLayout.LayoutParams(-1, 56, Gravity.BOTTOM));
        mCard = new FrameLayout(activity);
        mCard.setClickable(true);
        mParent.addView(mCard, new FrameLayout.LayoutParams(180, 102, Gravity.BOTTOM | Gravity.RIGHT));
        mPosition = new MiniPlayerPosition();
        mCanDrag = true;
        mDrag = MiniCardDrag.attach(mCard, mPosition, new MiniCardDrag.Callback() {
            @Override
            public boolean canDrag() {
                return mCanDrag;
            }

            @Override
            public void onPositionChanged() {
            }
        }, mCard);
        shadowOf(Looper.getMainLooper()).idle();
        layout(400, 800);
    }

    @Test
    public void verticalDragOverlaysTheToolbarAndStopsAboveNavigation() {
        touch(MotionEvent.ACTION_DOWN, 20f, 20f);
        assertTrue(touch(MotionEvent.ACTION_MOVE, 20f, -2000f));
        assertEquals(8f, mCard.getY(), EPSILON);
        assertTrue(touch(MotionEvent.ACTION_MOVE, 20f, 2000f));
        assertEquals(800f - 56f - mCard.getHeight(), mCard.getY(), EPSILON);
        touch(MotionEvent.ACTION_UP, 20f, 2000f);
    }

    @Test
    public void horizontalDragSnapsToTheChosenEdgeWithoutFadingOrClosing() {
        float expectedTop = 800f - 56f - mCard.getHeight();
        touch(MotionEvent.ACTION_DOWN, 20f, 20f);
        touch(MotionEvent.ACTION_MOVE, -140f, -261f);
        touch(MotionEvent.ACTION_UP, -140f, -261f);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(700));
        assertEquals(8f, mCard.getX(), EPSILON);
        assertEquals(expectedTop, mCard.getY(), EPSILON);
        assertEquals(1f, mCard.getAlpha(), EPSILON);
        assertEquals(View.VISIBLE, mCard.getVisibility());
    }

    @Test
    public void tapsStillExpandButDraggingCancelsTheClick() {
        AtomicInteger clicks = new AtomicInteger();
        mCard.setOnClickListener(view -> clicks.incrementAndGet());
        dispatch(MotionEvent.ACTION_DOWN, 20f, 20f);
        dispatch(MotionEvent.ACTION_UP, 20f, 20f);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, clicks.get());
        dispatch(MotionEvent.ACTION_DOWN, 20f, 20f);
        dispatch(MotionEvent.ACTION_MOVE, 20f, -200f);
        dispatch(MotionEvent.ACTION_UP, 20f, -200f);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(700));
        assertEquals(1, clicks.get());
    }

    @Test
    public void rotationRetainsTheChosenTopLeftCorner() {
        touch(MotionEvent.ACTION_DOWN, 20f, 20f);
        touch(MotionEvent.ACTION_MOVE, -140f, -400f);
        touch(MotionEvent.ACTION_UP, -140f, -400f);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(700));
        layout(800, 400);
        assertEquals(8f, mCard.getX(), EPSILON);
        assertEquals(8f, mCard.getY(), EPSILON);
    }

    @Test
    public void addingASecondFingerDoesNotJumpOrBecomeAClick() {
        touch(MotionEvent.ACTION_DOWN, 20f, 20f);
        touch(MotionEvent.ACTION_MOVE, -100f, -200f);
        touch(MotionEvent.ACTION_POINTER_DOWN, -100f, -200f);
        float top = mCard.getY();
        assertTrue(touch(MotionEvent.ACTION_MOVE, 2000f, 2000f));
        assertEquals(top, mCard.getY(), EPSILON);
        assertTrue(touch(MotionEvent.ACTION_UP, 2000f, 2000f));
    }

    @Test
    public void anUnavailableSessionCannotBeDragged() {
        mCanDrag = false;
        float top = mCard.getY();
        assertFalse(touch(MotionEvent.ACTION_DOWN, 20f, 20f));
        touch(MotionEvent.ACTION_MOVE, -100f, -200f);
        touch(MotionEvent.ACTION_UP, -100f, -200f);
        assertEquals(top, mCard.getY(), EPSILON);
    }

    @Test
    public void hidingNavigationReanchorsTheBottomCornerAboveTheSafeAreaGap() {
        mParent.findViewById(R.id.mobile_bottom_nav).setVisibility(View.GONE);
        layout(400, 800);
        assertEquals(800f - 8f - mCard.getHeight(), mCard.getY(), EPSILON);
    }

    @Test
    public void releaseInEachQuadrantSnapsToItsCorner() {
        for (boolean right : new boolean[] {false, true}) {
            for (boolean bottom : new boolean[] {false, true}) {
                mPosition.reset();
                mDrag.restore();
                float x = right ? 2000f : -2000f;
                float y = bottom ? 2000f : -2000f;
                touch(MotionEvent.ACTION_DOWN, 20f, 20f);
                touch(MotionEvent.ACTION_MOVE, x, y);
                touch(MotionEvent.ACTION_UP, x, y);
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(700));
                assertEquals(right ? 400f - 8f - mCard.getWidth() : 8f, mCard.getX(), EPSILON);
                assertEquals(bottom ? 800f - 56f - mCard.getHeight() : 8f, mCard.getY(), EPSILON);
            }
        }
    }

    @Test
    public void parentSafeInsetsAreRespectedWithoutAddingToolbarClearance() {
        mParent.setPadding(66, 24, 10, 36);
        mPosition.remember(-100f, -100f, 8f, 200f, 8f, 600f);
        layout(400, 800);
        assertEquals(74f, mCard.getX(), EPSILON);
        assertEquals(32f, mCard.getY(), EPSILON);
        mParent.findViewById(R.id.mobile_bottom_nav).setVisibility(View.GONE);
        mPosition.reset();
        mDrag.restore();
        assertEquals(400f - 10f - 8f - mCard.getWidth(), mCard.getX(), EPSILON);
        assertEquals(800f - 36f - 8f - mCard.getHeight(), mCard.getY(), EPSILON);
    }

    @Test
    public void aFastShortSwipeReachesTheOtherCornerBeforeCrossingTheMidpoint() {
        gesture(-80f, -80f, 60, MotionEvent.ACTION_UP);
        assertEquals(8f, mCard.getX(), EPSILON);
        assertEquals(8f, mCard.getY(), EPSILON);
    }

    @Test
    public void aSlowShortDragReturnsToTheStartingCorner() {
        gesture(-80f, -80f, 700, MotionEvent.ACTION_UP);
        assertEquals(400f - 8f - mCard.getWidth(), mCard.getX(), EPSILON);
        assertEquals(800f - 56f - mCard.getHeight(), mCard.getY(), EPSILON);
    }

    @Test
    public void cancellationDoesNotThrowTheCardUsingStaleVelocity() {
        gesture(-80f, -80f, 60, MotionEvent.ACTION_CANCEL);
        assertEquals(400f - 8f - mCard.getWidth(), mCard.getX(), EPSILON);
        assertEquals(800f - 56f - mCard.getHeight(), mCard.getY(), EPSILON);
    }

    @Test
    public void dockingHasTheMeasuredSmallOvershootAndFinishesAtTheExactCorner() {
        touch(MotionEvent.ACTION_DOWN, 20f, 20f);
        touch(MotionEvent.ACTION_MOVE, -2000f, -2000f);
        touch(MotionEvent.ACTION_UP, -2000f, -2000f);
        // Start a short return animation from inside the top-left quadrant.
        touch(MotionEvent.ACTION_DOWN, 20f, 20f);
        touch(MotionEvent.ACTION_MOVE, 80f, 80f);
        touch(MotionEvent.ACTION_UP, 80f, 80f);
        // The legacy animation shadow applies final positions, so inspect the reference curve here.
        assertEquals(500, mCard.animate().getDuration());
        assertEquals(1.037037f, mCard.animate().getInterpolator().getInterpolation(2f / 3f), 0.001f);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(700));
        assertEquals(8f, mCard.getX(), EPSILON);
        assertEquals(8f, mCard.getY(), EPSILON);
    }

    private void gesture(float dx, float dy, int duration, int finalAction) {
        float x = mCard.getX() + mCard.getWidth() / 2f;
        float y = mCard.getY() + mCard.getHeight() * 0.65f;
        for (int step = 0; step <= 4; step++) {
            int action = step == 0 ? MotionEvent.ACTION_DOWN
                    : step == 4 ? finalAction : MotionEvent.ACTION_MOVE;
            MotionEvent event = MotionEvent.obtain(1000, 1000 + duration * step / 4,
                    action, x + dx * step / 4, y + dy * step / 4, 0);
            // Simulate events delivered to a moving child while preserving screen coordinates.
            event.offsetLocation(-mCard.getX(), -mCard.getY());
            try {
                mDrag.onTouch(mCard, event);
            } finally {
                event.recycle();
            }
        }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(700));
    }

    private void layout(int width, int height) {
        RuntimeEnvironment.setQualifiers("w" + width + "dp-h" + height + "dp-"
                + (width > height ? "land" : "port") + "-mdpi");
        shadowOf(Looper.getMainLooper()).idle();
        View root = mParent.getRootView();
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
        mParent.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        mParent.layout(0, 0, width, height);
        mDrag.restore();
        mParent.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        mParent.layout(0, 0, width, height);
        mDrag.restore();
    }

    private boolean touch(int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(0, 20, action, x, y, 0);
        try {
            return mDrag.onTouch(mCard, event);
        } finally {
            event.recycle();
        }
    }

    private void dispatch(int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(0, 20, action, x, y, 0);
        try {
            mCard.dispatchTouchEvent(event);
        } finally {
            event.recycle();
        }
    }
}
