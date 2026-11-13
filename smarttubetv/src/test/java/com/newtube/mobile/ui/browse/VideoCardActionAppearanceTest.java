package com.newtube.mobile.ui.browse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;

import androidx.core.content.ContextCompat;

import com.liskovsoft.smartyoutubetv2.tv.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class VideoCardActionAppearanceTest {
    @Test
    @Config(qualifiers = "notnight-mdpi")
    public void bothActionsUseThePrimaryLightThemeInk() {
        assertThemeTint();
    }

    @Test
    @Config(qualifiers = "night-mdpi")
    public void bothActionsUseWhiteInkInTheDarkTheme() {
        assertThemeTint();
    }

    private void assertThemeTint() {
        ContextThemeWrapper context = new ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_NewTube);
        View card = LayoutInflater.from(context).inflate(R.layout.item_video_card, new FrameLayout(context), false);
        new VideoCardAdapter.VideoViewHolder(card, video -> { });
        for (int id : new int[] {R.id.video_watch_later, R.id.video_overflow}) {
            ImageButton action = card.findViewById(id);
            assertNotNull(action.getColorFilter());
            assertEquals(ContextCompat.getColor(context, R.color.mobile_color_on_surface),
                    shadowOf((PorterDuffColorFilter) action.getColorFilter()).getColor());
            assertEquals(PorterDuff.Mode.SRC_IN,
                    shadowOf((PorterDuffColorFilter) action.getColorFilter()).getMode());
            // Theme refresh can replace the vector with its original secondary-tinted asset.
            action.setImageResource(id == R.id.video_watch_later
                    ? R.drawable.ic_nav_history_outline : R.drawable.ic_mobile_more_vert);
            assertEquals(ContextCompat.getColor(context, R.color.mobile_color_on_surface),
                    shadowOf((PorterDuffColorFilter) action.getColorFilter()).getColor());
            action.setVisibility(View.VISIBLE);
            LayerDrawable background = (LayerDrawable) action.getBackground();
            background.setBounds(0, 0, 48, 48);
            RippleDrawable ripple = (RippleDrawable) background.getDrawable(0);
            assertEquals(new Rect(4, 4, 44, 44), ripple.getBounds());
            assertNotNull(ripple.findDrawableByLayerId(android.R.id.mask));
        }
        for (int width : new int[] {411, 320}) {
            card.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST));
            card.layout(0, 0, width, card.getMeasuredHeight());
            View clock = card.findViewById(R.id.video_watch_later);
            View menu = card.findViewById(R.id.video_overflow);
            assertEquals(40, clock.getWidth());
            assertEquals(48, clock.getHeight());
            assertEquals(40, menu.getWidth());
            assertEquals(48, menu.getHeight());
            assertEquals(clock.getRight(), menu.getLeft());
            assertEquals(clock.getTop(), menu.getTop());
            assertEquals(40, menu.getLeft() - clock.getLeft());
            assertEquals(24, clock.getWidth() - clock.getPaddingLeft() - clock.getPaddingRight());
            assertEquals(24, menu.getWidth() - menu.getPaddingLeft() - menu.getPaddingRight());
        }
    }
}
