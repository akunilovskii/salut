package com.newtube.mobile.ui.channel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import android.app.Activity;
import android.app.Application;
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
@Config(sdk = 28, manifest = Config.NONE, application = Application.class, qualifiers = "mdpi")
@ConscryptMode(ConscryptMode.Mode.OFF)
public class ChannelHeaderLayoutTest {
    @Test public void narrowAndRegularHeadersKeepFullActionTargets() {
        for (int width : new int[] {320, 411}) {
            Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
            activity.setTheme(R.style.Theme_NewTube);
            activity.setContentView(R.layout.activity_mobile_channel);
            View root = activity.findViewById(R.id.mobile_channel_root);
            assertEquals(View.GONE, root.findViewById(R.id.mobile_watch_subscribe).getVisibility());
            root.findViewById(R.id.mobile_watch_notifications).setVisibility(View.VISIBLE);
            ((TextView) root.findViewById(R.id.channel_header_name)).setText("Anton Petrov");
            TextView handle = root.findViewById(R.id.channel_header_handle);
            handle.setText("@AntonPetrov");
            handle.setVisibility(View.VISIBLE);
            TextView counts = root.findViewById(R.id.channel_header_counts);
            counts.setText("1.2M subscribers · 1K videos");
            counts.setVisibility(View.VISIBLE);
            TextView description = root.findViewById(R.id.channel_header_description);
            description.setText("Channel description");
            description.setVisibility(View.VISIBLE);
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, width, 800);
            assertEquals(72, root.findViewById(R.id.channel_header_avatar).getHeight());
            for (int id : new int[] {R.id.mobile_watch_notifications, R.id.channel_header_more}) {
                View button = root.findViewById(id);
                assertTrue(button.getWidth() >= 40);
                assertEquals(48, button.getHeight());
            }
            assertEquals(68, root.findViewById(R.id.mobile_watch_notifications).getWidth());
            assertEquals(24, root.findViewById(R.id.mobile_watch_notification_icon).getWidth());
            View notifications = root.findViewById(R.id.mobile_watch_notifications);
            int pillBottom = centerY(notifications) + notifications.getHeight() / 2 - 4;
            int countsBottom = centerY(counts) + counts.getHeight() / 2;
            assertTrue(Math.abs(pillBottom - countsBottom) <= 1);
            assertTrue(centerY(counts) < centerY(description));
            View nameRow = root.findViewById(R.id.channel_header_name_row);
            assertEquals(4, handle.getTop() - nameRow.getBottom());
            assertEquals(4, counts.getTop() - handle.getBottom());
            assertEquals(nameRow.getLeft(), counts.getLeft());
            assertEquals(8, description.getTop() - root.findViewById(R.id.channel_header_identity).getBottom());
            View more = root.findViewById(R.id.channel_header_more);
            View name = root.findViewById(R.id.channel_header_name);
            android.graphics.Rect letters = new android.graphics.Rect();
            ((TextView) name).getPaint().getTextBounds("H", 0, 1, letters);
            int capTop = centerY(name) - name.getHeight() / 2 + ((TextView) name).getBaseline() + letters.top;
            int dotsTop = centerY(more) - more.getHeight() / 2 + more.getPaddingTop() + 4;
            assertTrue(Math.abs(dotsTop - capTop) <= 1);
            assertEquals(width - 16, right(more));
            assertEquals(width - 16, right(root.findViewById(R.id.mobile_watch_notifications)));
            assertEquals(2, ((android.view.ViewGroup) root.findViewById(R.id.mobile_watch_notifications)).getChildCount());
            assertTrue(root.findViewById(R.id.mobile_channel_grid).getHeight() > 200);
            activity.finish();
        }
    }
    @Test public void verificationBadgeFollowsTheNameAndFitsNarrowHeaders() {
        for (int width : new int[] {320, 411}) {
            for (String title : new String[] {"Anton", "A long verified channel name that wraps onto two lines"}) {
                Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
                activity.setTheme(R.style.Theme_NewTube);
                activity.setContentView(R.layout.activity_mobile_channel);
                View root = activity.findViewById(R.id.mobile_channel_root);
                TextView name = root.findViewById(R.id.channel_header_name);
                name.setText(title);
                View badge = root.findViewById(R.id.channel_header_verified);
                badge.setVisibility(View.VISIBLE);
                root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
                root.layout(0, 0, width, 800);
                assertEquals(20, badge.getWidth());
                assertEquals(8, badge.getLeft() - name.getRight());
                assertTrue(badge.getRight() <= ((View) badge.getParent()).getWidth());
                android.graphics.Rect letters = new android.graphics.Rect();
                name.getPaint().getTextBounds("H", 0, 1, letters);
                float capCenter = name.getTop() + name.getBaseline() + (letters.top + letters.bottom) / 2f;
                assertTrue(Math.abs(capCenter - (badge.getY() + badge.getHeight() / 2f)) <= 1f);
                activity.finish();
            }
        }
    }
    private int centerY(View view) {
        int top = Math.round(view.getY());
        for (android.view.ViewParent parent = view.getParent(); parent instanceof View; parent = parent.getParent()) top += Math.round(((View) parent).getY());
        return top + view.getHeight() / 2;
    }
    private int right(View view) {
        int left = view.getLeft();
        for (android.view.ViewParent parent = view.getParent(); parent instanceof View; parent = parent.getParent()) left += ((View) parent).getLeft();
        return left + view.getWidth();
    }
}
