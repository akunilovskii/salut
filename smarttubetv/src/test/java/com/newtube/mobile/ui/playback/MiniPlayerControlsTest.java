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
public class MiniPlayerControlsTest {
    private Activity mActivity;
    private FrameLayout mCard;

    @Before public void setUp() {
        mActivity = Robolectric.buildActivity(Activity.class).setup().get();
        mActivity.setTheme(R.style.Theme_NewTube);
        mCard = new FrameLayout(mActivity);
        LayoutInflater.from(mActivity).inflate(R.layout.mobile_mini_player_controls, mCard, true);
        MiniPlayerControls.bind(mCard);
        mActivity.setContentView(mCard);
        shadowOf(Looper.getMainLooper()).idle();
        int width = Math.round(198 * mActivity.getResources().getDisplayMetrics().density);
        int height = Math.round(width * 9f / 16);
        mCard.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        mCard.layout(0, 0, width, height);
    }

    @After public void tearDown() {
        mActivity.finish();
    }

    @Test public void buttonsOccupyTheTopCornersWithReferenceScrims() {
        ImageButton play = mCard.findViewById(R.id.mobile_mini_play_pause);
        ImageButton close = mCard.findViewById(R.id.mobile_mini_close);
        assertEquals(0, play.getLeft());
        assertEquals(0, play.getTop());
        assertEquals(mCard.getWidth(), close.getRight());
        assertEquals(0, close.getTop());
        for (ImageButton button : new ImageButton[] {play, close}) {
            assertEquals(48, button.getWidth());
            assertEquals(48, button.getHeight());
            assertEquals(new Rect(6, 6, 42, 42), paintedBounds(button, false));
            Bitmap bitmap = render(button);
            assertEquals(153, Color.alpha(bitmap.getPixel(24, 8)));
            bitmap.recycle();
        }
    }

    @Test
    @Config(qualifiers = "420dpi")
    public void fractionalDensityMatchesThePixelReference() {
        ImageButton play = mCard.findViewById(R.id.mobile_mini_play_pause);
        ImageButton close = mCard.findViewById(R.id.mobile_mini_close);
        for (ImageButton button : new ImageButton[] {play, close}) {
            assertEquals(126, button.getWidth());
            assertEquals(31, button.getPaddingLeft());
            assertEquals(new Rect(15, 15, 111, 111), paintedBounds(button, false));
        }
        play.setImageResource(R.drawable.ic_mini_play);
        assertRasterBounds(new Rect(45, 40, 88, 86), paintedBounds(play, true));
        play.setImageResource(R.drawable.ic_mini_pause);
        assertRasterBounds(new Rect(45, 39, 81, 87), paintedBounds(play, true));
        assertRasterBounds(new Rect(45, 45, 82, 81), paintedBounds(close, true));
    }

    @Test public void invisibleOuterBandsActivateOnlyTheirOwnButtons() {
        int[] clicks = new int[3];
        mCard.setOnClickListener(view -> clicks[2]++);
        mCard.findViewById(R.id.mobile_mini_play_pause).setOnClickListener(view -> clicks[0]++);
        mCard.findViewById(R.id.mobile_mini_close).setOnClickListener(view -> clicks[1]++);
        tap(2, 24);
        tap(46, 24);
        tap(mCard.getWidth() - 2, 24);
        tap(mCard.getWidth() - 46, 24);
        assertEquals(2, clicks[0]);
        assertEquals(2, clicks[1]);
        assertEquals(0, clicks[2]);
        tap(mCard.getWidth() / 2f, mCard.getHeight() / 2f);
        assertEquals(1, clicks[2]);
    }

    private Bitmap render(ImageButton button) {
        Bitmap bitmap = Bitmap.createBitmap(button.getWidth(), button.getHeight(), Bitmap.Config.ARGB_8888);
        button.draw(new Canvas(bitmap));
        return bitmap;
    }

    private Rect paintedBounds(ImageButton button, boolean whiteOnly) {
        Bitmap bitmap = render(button);
        Rect bounds = new Rect();
        for (int y = 0; y < bitmap.getHeight(); y++) for (int x = 0; x < bitmap.getWidth(); x++) {
            int pixel = bitmap.getPixel(x, y);
            boolean painted = whiteOnly ? Color.alpha(pixel) >= 220 && Color.red(pixel) >= 220
                    && Color.green(pixel) >= 220 && Color.blue(pixel) >= 220 : Color.alpha(pixel) > 16;
            if (painted) bounds.union(x, y, x + 1, y + 1);
        }
        bitmap.recycle();
        return bounds;
    }

    private void assertRasterBounds(Rect expected, Rect actual) {
        // Host Skia and the phone GPU can differ at one antialiased edge pixel.
        assertTrue("Expected " + expected + ", rendered " + actual,
                Math.abs(expected.left - actual.left) <= 1 && Math.abs(expected.top - actual.top) <= 1
                        && Math.abs(expected.right - actual.right) <= 1 && Math.abs(expected.bottom - actual.bottom) <= 1);
    }

    private void tap(float x, float y) {
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(now, now + 10, MotionEvent.ACTION_UP, x, y, 0);
        mCard.dispatchTouchEvent(down);
        mCard.dispatchTouchEvent(up);
        down.recycle();
        up.recycle();
        shadowOf(Looper.getMainLooper()).idle();
    }
}
