package com.newtube.mobile.ui.browse;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.app.Activity;
import android.app.Application;
import android.graphics.Rect;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.widget.TextView;

import com.liskovsoft.smartyoutubetv2.tv.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class, qualifiers = "w411dp-h900dp-mdpi")
@ConscryptMode(ConscryptMode.Mode.OFF)
public class BrowseToolbarLayoutTest {
    @Test
    public void phoneToolbarKeepsTheRequestedOrderAndCompactActionTargets() {
        assertToolbar(411);
    }

    @Test
    @Config(qualifiers = "w320dp-h800dp-mdpi")
    public void narrowToolbarFitsTheWordmarkWithoutOverlappingItsButtons() {
        assertToolbar(320);
    }

    @Test public void toolbarFeedbackCannotSpillOntoAdjacentControls() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.Theme_NewTube);
        activity.setContentView(R.layout.activity_mobile_browse);
        for (int id : new int[] {R.id.mobile_switch_audio, R.id.mobile_feedback_button, R.id.mobile_cast_button,
                R.id.mobile_search_button, R.id.mobile_title_back}) {
            View button = activity.findViewById(id);
            LayerDrawable background = (LayerDrawable) button.getBackground();
            background.setBounds(0, 0, 48, 48);
            RippleDrawable ripple = (RippleDrawable) background.getDrawable(0);
            assertEquals(new Rect(4, 4, 44, 44), ripple.getBounds());
            assertNotNull(ripple.findDrawableByLayerId(android.R.id.mask));
            button.setSelected(true);
            assertEquals(new Rect(4, 4, 44, 44), ripple.getBounds());
        }
        activity.finish();
    }

    private void assertToolbar(int width) {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.Theme_NewTube);
        activity.setContentView(R.layout.activity_mobile_browse);
        new BrowseTopBar(activity, () -> { }).show(false, null);
        View root = activity.findViewById(R.id.mobile_browse_root);
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, 800);
        TextView title = activity.findViewById(R.id.mobile_title_bar);
        int edge = title.getRight();
        for (int id : new int[] {R.id.mobile_switch_audio, R.id.mobile_feedback_button, R.id.mobile_cast_button,
                R.id.mobile_search_button, R.id.mobile_you_button_container}) {
            View button = activity.findViewById(id);
            assertTrue("Overlapping toolbar button " + id, button.getLeft() >= edge);
            assertTrue("Small toolbar touch target " + id, button.getWidth() >= 40);
            edge = button.getRight();
        }
        assertTrue(edge <= width);
        float textWidth = title.getPaint().measureText(title.getText().toString());
        assertTrue("Wordmark was clipped", textWidth <= title.getWidth() - title.getPaddingLeft() - title.getPaddingRight());
        activity.finish();
    }
}
