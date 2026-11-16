package com.newtube.mobile.ui.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Application;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class, qualifiers = "mdpi")
@ConscryptMode(ConscryptMode.Mode.OFF)
public class PlaylistWatchScrollViewTest {
    private PlaylistWatchScrollView scroll;
    private RecyclerView list;
    private LinearLayoutManager manager;
    private View details;
    private View heading;

    @Before public void setUp() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        scroll = new PlaylistWatchScrollView(activity, null);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, 24);
        scroll.addView(content, new android.widget.FrameLayout.LayoutParams(-1, -2));
        details = new View(activity);
        content.addView(details, new LinearLayout.LayoutParams(-1, 200));
        heading = new View(activity);
        content.addView(heading, new LinearLayout.LayoutParams(-1, 48));
        list = new RecyclerView(activity);
        manager = new LinearLayoutManager(activity);
        list.setLayoutManager(manager);
        list.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @Override public int getItemCount() { return 20; }
            @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
                TextView text = new TextView(parent.getContext());
                text.setLayoutParams(new RecyclerView.LayoutParams(-1, 100));
                return new RecyclerView.ViewHolder(text) { };
            }
            @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                ((TextView) holder.itemView).setText("Video " + position);
            }
        });
        content.addView(list, new LinearLayout.LayoutParams(-1, -2));
        scroll.bindPlaylist(heading, list);
        scroll.setPlaylistMode(true);
        android.widget.FrameLayout host = new android.widget.FrameLayout(activity);
        host.addView(scroll, new android.widget.FrameLayout.LayoutParams(320, 500));
        activity.setContentView(host);
    }

    @Test public void middleItemStartsExactlyBelowThePinnedPlaylistHeading() {
        scroll.positionPlaylistItem(8);
        layout();
        assertEquals(0, heading.getTop() - scroll.getScrollY());
        assertNotNull("First row=" + manager.findFirstVisibleItemPosition() + ", list height=" + list.getHeight(),
                manager.findViewByPosition(8));
        assertEquals(0, manager.findViewByPosition(8).getTop());
        assertEquals(452, list.getHeight());
    }

    @Test public void lastItemStopsAtTheEndWithoutEmptyRowsBelowIt() {
        scroll.positionPlaylistItem(19);
        layout();
        assertEquals(list.getHeight(), manager.findViewByPosition(19).getBottom());
        assertEquals(200, scroll.getScrollY());
    }

    @Test public void pullingPastTheFirstRowRevealsDetailsAndUpwardTravelClosesThemFirst() {
        scroll.positionPlaylistItem(0);
        layout();
        scroll.onNestedScroll(list, 0, 0, 0, -80, ViewCompat.TYPE_TOUCH, new int[2]);
        assertEquals(120, scroll.getScrollY());
        int[] consumed = new int[2];
        scroll.onNestedPreScroll(list, 0, 80, consumed, ViewCompat.TYPE_TOUCH);
        assertEquals(80, consumed[1]);
        assertEquals(200, scroll.getScrollY());
        assertEquals(0, manager.findViewByPosition(0).getTop());
    }

    @Test public void metadataHeightChangesKeepCollapsedDetailsOutOfView() {
        scroll.positionPlaylistItem(8);
        layout();
        details.getLayoutParams().height = 280;
        details.requestLayout();
        layout();
        assertEquals(280, scroll.getScrollY());
        scroll.scrollTo(0, 100);
        details.getLayoutParams().height = 320;
        details.requestLayout();
        layout();
        assertEquals(100, scroll.getScrollY());
    }

    @Test public void leavingPlaylistModeRestoresTheOrdinaryWatchContent() {
        scroll.positionPlaylistItem(8);
        layout();
        scroll.setPlaylistMode(false);
        layout();
        assertEquals(0, scroll.getScrollY());
        assertEquals(24, scroll.getChildAt(0).getPaddingBottom());
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, list.getLayoutParams().height);
    }

    private void layout() {
        for (int pass = 0; pass < 4; pass++) {
            // This host is measured explicitly instead of waiting for a device traversal.
            scroll.forceLayout();
            scroll.getChildAt(0).forceLayout();
            details.forceLayout();
            heading.forceLayout();
            list.forceLayout();
            scroll.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY));
            scroll.layout(0, 0, 320, 500);
            shadowOf(Looper.getMainLooper()).idle();
        }
    }
}
