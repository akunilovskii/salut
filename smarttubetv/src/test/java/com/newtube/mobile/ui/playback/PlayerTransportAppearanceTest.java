package com.newtube.mobile.ui.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Looper;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;

import androidx.core.graphics.PathParser;

import com.liskovsoft.smartyoutubetv2.tv.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class, qualifiers = "mdpi")
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PlayerTransportAppearanceTest {
    private Activity mActivity;
    private View mTransport;

    @Before public void setUp() {
        mActivity = Robolectric.buildActivity(Activity.class).setup().get();
        mActivity.setTheme(R.style.Theme_NewTube);
        FrameLayout host = new FrameLayout(mActivity);
        mTransport = LayoutInflater.from(mActivity).inflate(R.layout.mobile_player_transport, host, false);
        PlayerTransportTouchTargets.bind(mTransport);
        host.addView(mTransport);
        mActivity.setContentView(host);
        shadowOf(Looper.getMainLooper()).idle();
        host.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(180, View.MeasureSpec.EXACTLY));
        host.layout(0, 0, 320, 180);
    }

    @After public void tearDown() {
        mActivity.finish();
    }

    @Test public void portraitPaintsTheReferenceCirclesAndGlyphSizes() {
        assertRenderedGeometry();
    }

    @Test
    @Config(qualifiers = "land-mdpi")
    public void fullscreenPaintsTheSameReferenceGeometry() {
        assertRenderedGeometry();
    }

    @Test
    @Config(qualifiers = "420dpi")
    public void pixelDensityKeepsTheReferenceGlyphBoundsWithinRasterRounding() {
        ImageButton center = mTransport.findViewById(R.id.mobile_player_play_pause);
        assertEquals(147, center.getWidth());
        center.setImageResource(R.drawable.ic_player_transport_play);
        assertRasterBounds(new Rect(43, 36, 115, 111), paintedBounds(center, true));
        center.setImageResource(R.drawable.ic_player_transport_pause);
        assertRasterBounds(new Rect(43, 35, 104, 112), paintedBounds(center, true));
        ImageButton next = mTransport.findViewById(R.id.mobile_player_next);
        assertEquals(105, next.getWidth());
        assertRasterBounds(new Rect(29, 32, 76, 73), paintedBounds(next, true));
    }

    @Test public void outerTouchBandsWorkWithoutMakingTheGapsClickable() {
        int[] clicks = new int[1];
        for (int id : new int[] {R.id.mobile_player_previous, R.id.mobile_player_play_pause, R.id.mobile_player_next}) {
            mTransport.findViewById(id).setOnClickListener(view -> clicks[0]++);
        }
        View center = mTransport.findViewById(R.id.mobile_player_center_touch_area);
        View next = mTransport.findViewById(R.id.mobile_player_next_touch_area);
        tap(1, 40);
        tap(center.getLeft() + 1, 40);
        tap(next.getRight() - 1, 40);
        assertEquals(3, clicks[0]);
        tap(center.getLeft() - 12, 40);
        assertEquals(3, clicks[0]);
    }

    @Test public void roundedPlayAndPausePathsCanStillMorph() {
        assertTrue(PathParser.canMorph(
                PathParser.createNodesFromPathData(mActivity.getString(R.string.path_player_play)),
                PathParser.createNodesFromPathData(mActivity.getString(R.string.path_player_pause))));
    }

    @Test public void outerTouchBandRespectsDisabledAndNonClickableButtons() {
        int[] clicks = new int[1];
        View center = mTransport.findViewById(R.id.mobile_player_center_touch_area);
        ImageButton button = mTransport.findViewById(R.id.mobile_player_play_pause);
        button.setOnClickListener(view -> clicks[0]++);
        button.setEnabled(false);
        tap(center.getLeft() + 1, 40);
        button.setEnabled(true);
        button.setClickable(false);
        tap(center.getLeft() + 1, 40);
        assertEquals(0, clicks[0]);
    }

    private void assertRenderedGeometry() {
        ImageButton center = mTransport.findViewById(R.id.mobile_player_play_pause);
        ImageButton previous = mTransport.findViewById(R.id.mobile_player_previous);
        ImageButton next = mTransport.findViewById(R.id.mobile_player_next);
        assertEquals(new Rect(0, 0, 56, 56), paintedBounds(center, false));
        assertEquals(new Rect(0, 0, 40, 40), paintedBounds(previous, false));
        assertEquals(new Rect(0, 0, 40, 40), paintedBounds(next, false));
        View centerArea = mTransport.findViewById(R.id.mobile_player_center_touch_area);
        View previousArea = mTransport.findViewById(R.id.mobile_player_previous_touch_area);
        assertEquals(88, centerArea.getLeft() + centerArea.getWidth() / 2
                - previousArea.getLeft() - previousArea.getWidth() / 2);
        center.setImageResource(R.drawable.ic_player_transport_play);
        assertGlyph(center, 26, 30, 28, 31);
        center.setImageResource(R.drawable.ic_player_transport_pause);
        assertGlyph(center, 22, 24, 28, 31);
        assertGlyph(previous, 17, 19, 15, 17);
        assertGlyph(next, 17, 19, 15, 17);
    }

    private void assertGlyph(ImageButton button, int minWidth, int maxWidth, int minHeight, int maxHeight) {
        Rect bounds = paintedBounds(button, true);
        assertTrue("Unexpected glyph bounds: " + bounds,
                bounds.width() >= minWidth && bounds.width() <= maxWidth
                        && bounds.height() >= minHeight && bounds.height() <= maxHeight);
    }

    private void assertRasterBounds(Rect expected, Rect actual) {
        // Host Skia and the phone GPU can differ at one antialiased edge pixel.
        assertTrue("Expected " + expected + ", rendered " + actual,
                Math.abs(expected.left - actual.left) <= 1 && Math.abs(expected.top - actual.top) <= 1
                        && Math.abs(expected.right - actual.right) <= 1 && Math.abs(expected.bottom - actual.bottom) <= 1
                        && Math.abs(expected.width() - actual.width()) <= 1
                        && Math.abs(expected.height() - actual.height()) <= 1);
    }

    private Rect paintedBounds(ImageButton button, boolean whiteOnly) {
        Bitmap bitmap = Bitmap.createBitmap(button.getWidth(), button.getHeight(), Bitmap.Config.ARGB_8888);
        if (whiteOnly) bitmap.eraseColor(Color.rgb(40, 40, 40));
        button.draw(new Canvas(bitmap));
        Rect result = new Rect();
        for (int y = 0; y < bitmap.getHeight(); y++) for (int x = 0; x < bitmap.getWidth(); x++) {
            int color = bitmap.getPixel(x, y);
            boolean painted = whiteOnly
                    ? Color.red(color) >= 220 && Color.green(color) >= 220 && Color.blue(color) >= 220
                    : Color.alpha(color) > 16;
            if (painted) result.union(x, y, x + 1, y + 1);
        }
        bitmap.recycle();
        return result;
    }

    private void tap(float x, float y) {
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(now, now + 10, MotionEvent.ACTION_UP, x, y, 0);
        mTransport.dispatchTouchEvent(down);
        mTransport.dispatchTouchEvent(up);
        down.recycle();
        up.recycle();
        shadowOf(Looper.getMainLooper()).idle();
    }
}
