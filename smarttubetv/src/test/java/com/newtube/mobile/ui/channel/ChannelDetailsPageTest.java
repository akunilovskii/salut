package com.newtube.mobile.ui.channel;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.liskovsoft.mediaserviceinterfaces.data.ChannelProfile;
import com.liskovsoft.smartyoutubetv2.tv.R;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class, qualifiers = "mdpi")
@ConscryptMode(ConscryptMode.Mode.OFF)
public class ChannelDetailsPageTest {
    @Test public void aboutKeepsItsLastRowsClearOfTheMiniPlayerAndRestoresAccessibility() {
        Activity activity = activity();
        android.widget.FrameLayout root = activity.findViewById(R.id.mobile_channel_root);
        View underlay = root.getChildAt(0);
        underlay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        View card = activity.findViewById(R.id.mobile_mini_player);
        card.setVisibility(View.VISIBLE);
        card.layout(0, 0, 200, 150);
        ChannelDetailsPage page = new ChannelDetailsPage(activity);
        page.show(new ChannelProfile("Name", null, null, null, null, null, "Description", false, Collections.emptyList()), "UCtest");
        android.widget.ScrollView scroll = findScroll(root.getChildAt(1));
        card.getViewTreeObserver().dispatchOnGlobalLayout();
        assertTrue(scroll.getPaddingBottom() >= 150);
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS, underlay.getImportantForAccessibility());
        page.close();
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_YES, underlay.getImportantForAccessibility());
    }
    @Test public void fullDetailsRetainTheChannelFeedAndReturnWithoutRebuildingIt() {
        Activity activity = activity();
        View feed = activity.findViewById(R.id.mobile_channel_grid);
        ChannelDetailsPage page = new ChannelDetailsPage(activity);
        ChannelProfile profile = new ChannelProfile("Name", "@name", null, null, "10K subscribers", null,
                "A full description", true, Collections.emptyList(), "https://www.youtube.com/@name", "Country", "Joined 2011", "100 views");
        page.show(profile, "UCtest");
        String contents = texts(activity.findViewById(R.id.mobile_channel_root));
        assertTrue(contents.contains("A full description"));
        assertTrue(contents.contains("Country"));
        assertTrue(contents.contains("Joined 2011"));
        assertTrue(contents.contains("100 views"));
        assertSame(feed, activity.findViewById(R.id.mobile_channel_grid));
        assertTrue(page.close());
        assertFalse(page.close());
        assertSame(feed, activity.findViewById(R.id.mobile_channel_grid));
    }
    @Test public void linksShowBothNameAndAddressAndOpenOnlyWhenTapped() {
        Activity activity = activity();
        View row = ChannelDetailsPage.linkRow(activity, new ChannelProfile.Link("Telegram", "https://t.me/example"), false);
        assertTrue(texts(row).contains("Telegram"));
        assertTrue(texts(row).contains("t.me/example"));
        assertNull(shadowOf(activity).getNextStartedActivity());
        row.performClick();
        Intent intent = shadowOf(activity).getNextStartedActivity();
        assertEquals(Intent.ACTION_VIEW, intent.getAction());
        assertEquals("https://t.me/example", intent.getDataString());
    }
    @Test public void narrowLinkRowsRetainAnIconAndAFullTouchTarget() {
        Activity activity = activity();
        View row = ChannelDetailsPage.linkRow(activity, new ChannelProfile.Link("A long service name", "https://example.com/a/long/address"), false);
        row.measure(View.MeasureSpec.makeMeasureSpec(288, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.AT_MOST));
        row.layout(0, 0, 288, row.getMeasuredHeight());
        assertTrue(row.getHeight() >= 48);
        assertEquals(24, ((ViewGroup) row).getChildAt(0).getWidth());
        assertTrue(((ViewGroup) row).getChildAt(1).getWidth() > 100);
    }
    @Test public void firstLinkSummaryCountsOnlyTheAdditionalLinks() {
        Activity activity = activity();
        java.util.List<ChannelProfile.Link> links = new java.util.ArrayList<>();
        assertEquals("", ChannelHeaderController.linkSummary(activity, links));
        links.add(new ChannelProfile.Link("Example", "https://www.example.com/profile"));
        assertEquals("example.com/profile", ChannelHeaderController.linkSummary(activity, links));
        links.add(new ChannelProfile.Link("Other", "https://other.example"));
        assertEquals("example.com/profile and 1 more link", ChannelHeaderController.linkSummary(activity, links));
        links.add(new ChannelProfile.Link("Third", "https://third.example"));
        assertEquals("example.com/profile and 2 more links", ChannelHeaderController.linkSummary(activity, links));
    }

    @Test public void linksSheetWrapsShortListsAndScrollsWithinSixtyPercentForLongLists() {
        Activity activity = activity();
        int cap = Math.round(activity.getResources().getDisplayMetrics().heightPixels * 0.6f);
        java.util.List<ChannelProfile.Link> links = new java.util.ArrayList<>();
        links.add(new ChannelProfile.Link("Example", "https://example.com"));
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = ChannelDetailsPage.showLinks(activity, links);
        assertEquals(cap, dialog.getBehavior().getMaxHeight());
        View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        sheet.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(cap, View.MeasureSpec.AT_MOST));
        sheet.layout(0, 0, 320, sheet.getMeasuredHeight());
        assertTrue(sheet.getHeight() < cap);
        android.widget.ScrollView scroll = findScroll(sheet);
        assertEquals("Short lists leave no blank viewport below their rows", scroll.getChildAt(0).getHeight(), scroll.getHeight());
        dialog.dismiss();
        for (int i = 0; i < 20; i++) links.add(new ChannelProfile.Link("Service " + i, "https://example.com/" + i));
        dialog = ChannelDetailsPage.showLinks(activity, links);
        sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        sheet.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(cap, View.MeasureSpec.AT_MOST));
        sheet.layout(0, 0, 320, sheet.getMeasuredHeight());
        assertTrue(sheet.getHeight() <= cap);
        scroll = findScroll(sheet);
        assertTrue(scroll.getChildAt(0).getHeight() > scroll.getHeight());
        dialog.dismiss();
    }

    private Activity activity() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.Theme_NewTube);
        activity.setContentView(R.layout.activity_mobile_channel);
        return activity;
    }
    private String texts(View view) {
        StringBuilder result = new StringBuilder();
        if (view instanceof TextView) result.append(((TextView) view).getText()).append('\n');
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) result.append(texts(((ViewGroup) view).getChildAt(i)));
        return result.toString();
    }
    private android.widget.ScrollView findScroll(View view) {
        if (view instanceof android.widget.ScrollView) return (android.widget.ScrollView) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            android.widget.ScrollView found = findScroll(((ViewGroup) view).getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }
}
