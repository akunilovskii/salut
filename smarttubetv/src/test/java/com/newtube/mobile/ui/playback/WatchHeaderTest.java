package com.newtube.mobile.ui.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Looper;
import android.os.SystemClock;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

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
public class WatchHeaderTest {
    private Activity mActivity;
    private View mHeader;
    private TextView mTitle;
    private TextView mMeta;

    @Before public void setUp() {
        mActivity = Robolectric.buildActivity(Activity.class).setup().get();
        inflateHeader(1f);
    }

    @After public void tearDown() { mActivity.finish(); }

    @Test
    @Config(qualifiers = "420dpi")
    public void oneLineHeaderMatchesTheMeasuredPixelReferenceHeight() {
        inflateHeader(1.15f);
        measure(1080);
        assertTrue("Reference header is 198px tall", Math.abs(mHeader.getHeight() - 198) <= 2);
        assertEquals(32, mHeader.getPaddingStart());
        assertEquals(42, mHeader.getPaddingTop());
        float scaledDensity = mHeader.getResources().getDisplayMetrics().scaledDensity;
        assertEquals(Math.round(18 * scaledDensity), mTitle.getTextSize(), 0.01f);
        assertEquals(Math.round(12 * scaledDensity), mMeta.getTextSize(), 0.01f);
        assertEquals(1, mTitle.getMaxLines());
    }

    @Test public void titleMetadataMoreAndPaddingRouteToOneClickAction() {
        measure(411);
        int[] clicks = {0};
        mHeader.setOnClickListener(view -> clicks[0]++);
        tap(mTitle.getLeft() + 10, mTitle.getTop() + mTitle.getHeight() / 2f);
        View row = mHeader.findViewById(R.id.mobile_watch_meta_row);
        tap(row.getLeft() + 10, row.getTop() + row.getHeight() / 2f);
        View expand = mHeader.findViewById(R.id.mobile_watch_expand);
        tap(row.getLeft() + expand.getLeft() + expand.getWidth() / 2f,
                row.getTop() + expand.getTop() + expand.getHeight() / 2f);
        tap(1, 1);
        assertEquals(4, clicks[0]);
        assertFalse(mTitle.isClickable());
        assertFalse(row.isClickable());
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, expand.getImportantForAccessibility());
        assertTrue(mHeader.getHeight() >= 48);
    }

    @Test public void longTitlesStayOnOneLineWithoutIncreasingTheHeaderHeight() {
        measure(320);
        int shortHeight = mHeader.getHeight();
        mTitle.setText("A very long title with enough words to fill several lines in a narrow phone "
                + "while leaving all of the video details and the description action reachable");
        measure(320);
        assertEquals(1, mTitle.getLineCount());
        assertEquals(shortHeight, mHeader.getHeight());
        assertTrue(mTitle.getBottom() <= mHeader.findViewById(R.id.mobile_watch_meta_row).getTop());
        assertEquals(1, mMeta.getMaxLines());
    }

    @Test public void largerTextAndExpandedMetadataKeepMoreReachable() {
        inflateHeader(1.5f);
        mTitle.setText("A longer video title at a larger accessibility font size");
        mMeta.setText("A long localized line of video views, publication date and additional details");
        measure(320);
        int collapsedHeight = mHeader.getHeight();
        mMeta.setSingleLine(false);
        mMeta.setMaxLines(Integer.MAX_VALUE);
        measure(320);
        assertTrue(mMeta.getLineCount() > 1);
        assertTrue(mHeader.getHeight() > collapsedHeight);
        assertEquals(1, mTitle.getLineCount());
        assertTrue(mMeta.getRight() <= mHeader.findViewById(R.id.mobile_watch_expand).getLeft());
    }

    @Test public void overflowingTitleFadesItsInkAtTheRightEdgeWithoutAnOpaqueOverlay() {
        mTitle.setText("MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM");
        measure(320);
        android.graphics.Bitmap faded = android.graphics.Bitmap.createBitmap(mTitle.getWidth(), mTitle.getHeight(),
                android.graphics.Bitmap.Config.ARGB_8888);
        mTitle.draw(new android.graphics.Canvas(faded));
        assertTrue(mTitle.getEllipsize() == null);
        mTitle.setMaxLines(2); // Same single-line layout, with the overflow mask disabled.
        measure(320);
        android.graphics.Bitmap original = android.graphics.Bitmap.createBitmap(mTitle.getWidth(), mTitle.getHeight(),
                android.graphics.Bitmap.Config.ARGB_8888);
        mTitle.draw(new android.graphics.Canvas(original));
        long originalAlpha = 0, fadedAlpha = 0;
        for (int x = original.getWidth() - 24; x < original.getWidth(); x++) {
            for (int y = 0; y < original.getHeight(); y++) {
                originalAlpha += android.graphics.Color.alpha(original.getPixel(x, y));
                fadedAlpha += android.graphics.Color.alpha(faded.getPixel(x, y));
            }
        }
        assertTrue("Only the overflowing text ink should fade", fadedAlpha < originalAlpha);
    }

    private void inflateHeader(float fontScale) {
        Configuration config = new Configuration(mActivity.getResources().getConfiguration());
        config.fontScale = fontScale;
        Context context = new ContextThemeWrapper(mActivity.createConfigurationContext(config), R.style.Theme_NewTube);
        FrameLayout host = new FrameLayout(mActivity);
        mHeader = LayoutInflater.from(context).inflate(R.layout.mobile_watch_header, host, false);
        host.addView(mHeader);
        mActivity.setContentView(host);
        mTitle = mHeader.findViewById(R.id.mobile_watch_title);
        mMeta = mHeader.findViewById(R.id.mobile_watch_meta);
        mTitle.setText("She Wasn't Expecting THIS");
        mMeta.setText("40K views · 1 hour ago");
    }

    private void measure(int width) {
        mHeader.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.AT_MOST));
        mHeader.layout(0, 0, width, mHeader.getMeasuredHeight());
    }

    private void tap(float x, float y) {
        long time = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(time, time + 20, MotionEvent.ACTION_UP, x, y, 0);
        mHeader.dispatchTouchEvent(down);
        mHeader.dispatchTouchEvent(up);
        down.recycle();
        up.recycle();
        shadowOf(Looper.getMainLooper()).idle();
    }
}
