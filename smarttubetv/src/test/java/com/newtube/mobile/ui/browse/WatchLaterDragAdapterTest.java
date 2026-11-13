package com.newtube.mobile.ui.browse;

import static org.junit.Assert.*;

import android.app.Application;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.tv.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.Arrays;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class WatchLaterDragAdapterTest {
    @Test public void dragMoveUpdatesTheListBeforeNotifyingRecyclerView() {
        VideoCardAdapter adapter = new VideoCardAdapter(null);
        Video a = video("a"), b = video("b"), c = video("c");
        adapter.setCompact(true);
        adapter.submitList(Arrays.asList(a, b, c));
        int[] moves = {0};
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override public void onItemRangeMoved(int from, int to, int count) {
                assertEquals(Arrays.asList(b, c, a), adapter.getCurrentList());
                moves[0]++;
            }
        });
        adapter.moveCompactItem(0, 2);
        assertEquals(1, moves[0]);
    }

    @Test public void failureRestoresTheOriginalListSynchronously() {
        VideoCardAdapter adapter = new VideoCardAdapter(null);
        Video a = video("a"), b = video("b");
        adapter.setCompact(true);
        adapter.submitList(Arrays.asList(a, b));
        adapter.moveCompactItem(1, 0);
        boolean[] committed = {false};
        adapter.submitList(Arrays.asList(a, b), () -> {
            assertEquals(Arrays.asList(a, b), adapter.getCurrentList());
            committed[0] = true;
        });
        assertTrue(committed[0]);
        assertEquals(2, adapter.getItemCount());
    }

    @Test public void theSharedUpNextRowDoesNotShowAHandleByDefault() {
        FrameLayout parent = new FrameLayout(new android.view.ContextThemeWrapper(
                RuntimeEnvironment.getApplication(), R.style.Theme_NewTube));
        android.view.View row = android.view.LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_mobile_related_video, parent, false);
        assertEquals(android.view.View.GONE, row.findViewById(R.id.related_drag_handle).getVisibility());
    }

    @Test public void leftHandleAndThumbnailLeaveUsableTitleSpaceAt320Dp() {
        android.content.Context context = new android.view.ContextThemeWrapper(
                RuntimeEnvironment.getApplication(), R.style.Theme_NewTube);
        android.view.View row = android.view.LayoutInflater.from(context)
                .inflate(R.layout.item_mobile_related_video, new FrameLayout(context), false);
        android.view.View handle = row.findViewById(R.id.related_drag_handle);
        handle.setVisibility(android.view.View.VISIBLE);
        int width = Math.round(320 * context.getResources().getDisplayMetrics().density);
        row.measure(android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED));
        row.layout(0, 0, width, row.getMeasuredHeight());
        android.graphics.Rect handleBounds = new android.graphics.Rect();
        android.graphics.Rect thumbnailBounds = new android.graphics.Rect();
        handle.getGlobalVisibleRect(handleBounds);
        row.findViewById(R.id.related_thumbnail).getGlobalVisibleRect(thumbnailBounds);
        assertTrue(handleBounds.right <= thumbnailBounds.left);
        assertTrue(row.findViewById(R.id.related_title).getMeasuredWidth() > 0);
        assertTrue(handle.getMeasuredHeight() >= Math.round(48 * context.getResources().getDisplayMetrics().density));
    }

    private static Video video(String id) {
        Video video = new Video();
        video.videoId = id;
        video.title = id;
        return video;
    }
}
