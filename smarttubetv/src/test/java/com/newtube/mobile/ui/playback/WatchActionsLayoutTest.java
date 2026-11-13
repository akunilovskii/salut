package com.newtube.mobile.ui.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.liskovsoft.smartyoutubetv2.tv.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class, qualifiers = "mdpi")
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WatchActionsLayoutTest {
    @Test public void actionsFitOnThePixelWidthWithoutPillsOrScrolling() {
        LinearLayout row = inflate();
        measure(row, 411);

        assertEquals(411, row.getWidth());
        assertEquals(48, row.getHeight());
        for (int id : new int[] {R.id.mobile_watch_like, R.id.mobile_watch_dislike,
                R.id.mobile_watch_save, R.id.mobile_watch_share, R.id.mobile_watch_download}) {
            ViewGroup action = row.findViewById(id);
            assertEquals(41, action.getWidth());
            assertEquals(48, action.getHeight());
            assertNull(action.getBackground());
            assertFalse(action.getContentDescription().toString().isEmpty());
            View icon = action.getChildAt(0);
            assertEquals(id == R.id.mobile_watch_save ? 22 : 20, icon.getWidth());
            assertEquals(action.getWidth() / 2, icon.getLeft() + icon.getWidth() / 2);
            assertEquals(action.getHeight() / 2, icon.getTop() + icon.getHeight() / 2);
        }
        assertTrue(row.findViewById(R.id.mobile_watch_download).getRight()
                <= row.getWidth() - row.getPaddingRight());
    }

    @Test public void iconCentersMatchTheCompactReferenceSpacing() {
        LinearLayout row = inflate();
        measure(row, 411);
        int previousCenter = -1;
        for (int id : new int[] {R.id.mobile_watch_like, R.id.mobile_watch_dislike, R.id.mobile_watch_save,
                R.id.mobile_watch_share, R.id.mobile_watch_download}) {
            View action = row.findViewById(id);
            android.graphics.Rect bounds = new android.graphics.Rect(0, 0, action.getWidth(), action.getHeight());
            row.offsetDescendantRectToMyCoords(action, bounds);
            int center = bounds.centerX();
            if (previousCenter >= 0) assertEquals(41, center - previousCenter);
            previousCenter = center;
        }
        assertEquals(21, row.getWidth() - previousCenter);
        for (int id : new int[] {R.id.mobile_watch_save_label, R.id.mobile_watch_share_label,
                R.id.mobile_watch_download_label}) {
            assertEquals(View.GONE, row.findViewById(id).getVisibility());
        }
        View avatar = row.findViewById(R.id.mobile_watch_avatar);
        assertEquals(12, avatar.getLeft() + avatar.getPaddingLeft() + row.getPaddingLeft());
    }

    private LinearLayout inflate() {
        android.content.Context context = new ContextThemeWrapper(
                RuntimeEnvironment.getApplication(), R.style.Theme_NewTube);
        return (LinearLayout) LayoutInflater.from(context).inflate(
                R.layout.mobile_watch_actions, new FrameLayout(context), false);
    }

    private void measure(View row, int width) {
        row.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.AT_MOST));
        row.layout(0, 0, width, row.getMeasuredHeight());
    }
}
