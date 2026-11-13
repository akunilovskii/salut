package com.newtube.mobile.ui.browse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.graphics.Rect;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.smartyoutubetv2.tv.R;
import com.newtube.mobile.ui.common.FeedCardSpacing;

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
public class FeedCardLayoutTest {
    private Activity mActivity;
    private RecyclerView mGrid;
    private GridLayoutManager mLayout;
    private boolean mShowAvatar = true;

    @Before public void setUp() {
        mActivity = Robolectric.buildActivity(Activity.class).setup().get();
        mActivity.getApplicationInfo().flags |= ApplicationInfo.FLAG_SUPPORTS_RTL;
        mActivity.setTheme(R.style.Theme_NewTube);
        mGrid = new RecyclerView(mActivity);
        mLayout = new GridLayoutManager(mActivity, 1);
        mLayout.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int position) {
                return position == 0 ? mLayout.getSpanCount() : 1;
            }
        });
        mGrid.setLayoutManager(mLayout);
        mGrid.setItemAnimator(null);
        FeedCardSpacing.attach(mGrid);
        mGrid.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @Override public int getItemViewType(int position) { return position == 0 ? 0 : 1; }
            @Override public int getItemCount() { return 7; }
            @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(
                    @NonNull ViewGroup parent, int type) {
                View view;
                if (type == 0) {
                    view = new View(parent.getContext());
                    view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 32));
                } else {
                    view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_video_card, parent, false);
                    view.findViewById(R.id.video_channel_avatar).setVisibility(mShowAvatar ? View.VISIBLE : View.GONE);
                    view.findViewById(R.id.video_watch_later).setVisibility(View.VISIBLE);
                    TextView title = view.findViewById(R.id.video_title);
                    title.setText("A longer video title for testing wrapping and alignment across feed widths");
                    ((TextView) view.findViewById(R.id.video_meta)).setText("Channel name and video details");
                }
                return new RecyclerView.ViewHolder(view) { };
            }
            @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) { }
        });
        mActivity.setContentView(mGrid);
    }

    @After public void tearDown() { mActivity.finish(); }

    @Test public void portraitAndNarrowFeedsHaveFullWidthThumbnailsAndReferenceTextInsets() {
        for (int width : new int[] {411, 320}) {
            layout(width, 1);
            View card = mLayout.findViewByPosition(1);
            assertEquals(0, bounds(card, R.id.video_thumbnail_frame).left);
            assertEquals(width, bounds(card, R.id.video_thumbnail_frame).right);
            assertEquals(12, bounds(card, R.id.video_channel_avatar).left);
            assertEquals(56, bounds(card, R.id.video_title).left);
            assertEquals(bounds(card, R.id.video_thumbnail_frame).right, bounds(card, R.id.video_overflow).right);
            assertEquals(bounds(card, R.id.video_watch_later).right, bounds(card, R.id.video_overflow).left);
            TextView title = card.findViewById(R.id.video_title);
            assertEquals(16f, title.getTextSize(), 0.01f);
            assertTrue(title.getLineCount() <= 2);
        }
    }

    @Test public void cardsWithoutAnAvatarKeepATwelveDpTextInset() {
        mShowAvatar = false;
        layout(411, 1);
        View card = mLayout.findViewByPosition(1);
        assertEquals(12, bounds(card, R.id.video_title).left);
        assertEquals(12, bounds(card, R.id.video_meta).left);
    }

    @Test public void avatarStaysAtTheTitleTopWhenItsTextWrapsOrShrinks() {
        layout(411, 1);
        View card = mLayout.findViewByPosition(1);
        TextView title = card.findViewById(R.id.video_title);
        int avatarTop = bounds(card, R.id.video_channel_avatar).top;
        assertEquals(2, title.getLineCount());
        assertEquals(bounds(card, R.id.video_title).top, avatarTop);

        title.setText("Short title");
        layout(411, 1);
        assertEquals(1, title.getLineCount());
        assertEquals(bounds(card, R.id.video_title).top, bounds(card, R.id.video_channel_avatar).top);
        assertEquals(avatarTop, bounds(card, R.id.video_channel_avatar).top);

        title.setText("A longer video title for testing wrapping and alignment across feed widths");
        layout(411, 1);
        assertEquals(2, title.getLineCount());
        assertEquals(avatarTop, bounds(card, R.id.video_channel_avatar).top);
    }

    @Test public void landscapeKeepsAnEqualGutterAfterAFullSpanHeader() {
        layout(920, 2);
        assertColumnEdges(920, false);
        assertEquals(0, mLayout.findViewByPosition(0).getLeft());
        assertEquals(920, mLayout.findViewByPosition(0).getRight());
    }

    @Test public void rotationRecalculatesOffsetsForExistingCards() {
        layout(920, 2);
        layout(411, 1);
        View card = mLayout.findViewByPosition(1);
        assertEquals(0, bounds(card, R.id.video_thumbnail_frame).left);
        assertEquals(411, bounds(card, R.id.video_thumbnail_frame).right);
        layout(920, 2);
        assertColumnEdges(920, false);
    }

    @Test public void rightToLeftGridsRetainTheSameOuterEdgesAndGutter() {
        ViewCompat.setLayoutDirection(mGrid, ViewCompat.LAYOUT_DIRECTION_RTL);
        layout(920, 2);
        assertColumnEdges(920, true);
    }

    private void assertColumnEdges(int width, boolean rtl) {
        Rect first = bounds(mLayout.findViewByPosition(1), R.id.video_thumbnail_frame);
        Rect second = bounds(mLayout.findViewByPosition(2), R.id.video_thumbnail_frame);
        Rect left = rtl ? second : first;
        Rect right = rtl ? first : second;
        assertEquals(0, left.left);
        assertEquals(width, right.right);
        assertEquals(16, right.left - left.right);
        assertEquals(left.width(), right.width());
    }

    private Rect bounds(View card, int id) {
        View view = card.findViewById(id);
        Rect rect = new Rect(0, 0, view.getWidth(), view.getHeight());
        mGrid.offsetDescendantRectToMyCoords(view, rect);
        return rect;
    }

    private void layout(int width, int columns) {
        mLayout.setSpanCount(columns);
        mGrid.invalidateItemDecorations();
        shadowOf(Looper.getMainLooper()).idle();
        mGrid.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1800, View.MeasureSpec.EXACTLY));
        mGrid.layout(0, 0, width, 1800);
    }
}
