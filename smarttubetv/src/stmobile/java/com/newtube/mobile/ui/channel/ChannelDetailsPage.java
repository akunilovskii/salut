package com.newtube.mobile.ui.channel;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.liskovsoft.mediaserviceinterfaces.data.ChannelProfile;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.newtube.mobile.ui.common.MobileSnackbar;
import java.util.List;

/** About stays in the channel's view stack, retaining tabs, scroll and the existing mini host. */
final class ChannelDetailsPage {
    private final Activity activity;
    private View page;
    private View underlay;
    private int previousAccessibility;
    private Runnable detachMiniInset = () -> { };
    ChannelDetailsPage(Activity activity) { this.activity = activity; }

    void show(ChannelProfile profile, String channelId) {
        close();
        FrameLayout host = activity.findViewById(R.id.mobile_channel_root);
        underlay = host.getChildAt(0);
        previousAccessibility = underlay.getImportantForAccessibility();
        underlay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        LinearLayout layout = column(activity);
        layout.setBackgroundColor(background(activity));
        layout.setClickable(true);
        LinearLayout toolbar = new LinearLayout(activity);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        ImageView back = new ImageView(activity);
        back.setImageResource(R.drawable.ic_mobile_back_arrow);
        back.setColorFilter(ink(activity));
        back.setPadding(dp(activity, 12), dp(activity, 12), dp(activity, 12), dp(activity, 12));
        back.setContentDescription(activity.getString(R.string.mobile_channel_back));
        back.setOnClickListener(view -> close());
        toolbar.addView(back, new LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 48)));
        TextView title = text(activity, profile.title, 20, false);
        title.setMaxLines(1); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        layout.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(activity, 56)));
        ScrollView scroll = new ScrollView(activity);
        detachMiniInset = com.newtube.mobile.ui.playback.MiniPlayerListInset.attachScrollingView(
                activity.findViewById(R.id.mobile_mini_player), scroll);
        scroll.setFillViewport(true);
        LinearLayout body = column(activity);
        body.setPadding(dp(activity, 16), 0, dp(activity, 16), dp(activity, 24));
        if (profile.description != null && !profile.description.trim().isEmpty()) {
            heading(body, R.string.mobile_channel_description);
            TextView description = text(activity, profile.description, 16, false);
            description.setTextIsSelectable(true);
            body.addView(description);
        }
        if (!profile.links.isEmpty()) {
            heading(body, R.string.mobile_channel_links);
            for (ChannelProfile.Link link : profile.links) body.addView(linkRow(activity, link, true));
        }
        heading(body, R.string.mobile_channel_more_info);
        String url = profile.channelUrl != null ? profile.channelUrl : "https://www.youtube.com/channel/" + Uri.encode(channelId);
        body.addView(infoRow(activity, displayUrl(url), R.drawable.ic_channel_globe, () -> open(activity, url)));
        if (profile.country != null) body.addView(infoRow(activity, profile.country, R.drawable.ic_channel_globe, null));
        if (profile.joined != null) body.addView(infoRow(activity, profile.joined, R.drawable.ic_channel_info, null));
        if (profile.views != null) body.addView(infoRow(activity, profile.views, R.drawable.ic_channel_views, null));
        scroll.addView(body);
        layout.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        // Insert below the existing mini-player overlay, without rebuilding the channel feed.
        host.addView(layout, Math.max(0, host.getChildCount() - 1), new FrameLayout.LayoutParams(-1, -1));
        page = layout;
        title.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
    }

    boolean close() {
        if (page == null) return false;
        detachMiniInset.run();
        detachMiniInset = () -> { };
        underlay.setImportantForAccessibility(previousAccessibility);
        underlay = null;
        ((ViewGroup) page.getParent()).removeView(page);
        page = null;
        return true;
    }

    static BottomSheetDialog showLinks(Activity activity, List<ChannelProfile.Link> links) {
        if (links.isEmpty()) return null;
        BottomSheetDialog dialog = new BottomSheetDialog(activity, R.style.ThemeOverlay_NewTube_BottomSheetDialog_WatchNotifications);
        LinearLayout outer = column(activity);
        outer.setPadding(dp(activity, 8), 0, dp(activity, 8), dp(activity, 8));
        LinearLayout surface = column(activity);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(background(activity)); shape.setCornerRadius(dp(activity, 16));
        surface.setBackground(shape);
        View handle = new View(activity);
        GradientDrawable grip = new GradientDrawable();
        grip.setColor(ContextCompat.getColor(activity, R.color.mobile_color_on_surface_secondary));
        grip.setCornerRadius(dp(activity, 2)); handle.setBackground(grip);
        LinearLayout.LayoutParams gripParams = new LinearLayout.LayoutParams(dp(activity, 32), dp(activity, 4));
        gripParams.gravity = Gravity.CENTER_HORIZONTAL; gripParams.topMargin = dp(activity, 8);
        surface.addView(handle, gripParams);
        TextView heading = text(activity, activity.getString(R.string.mobile_channel_links), 20, false);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setPadding(dp(activity, 16), dp(activity, 16), dp(activity, 16), dp(activity, 12));
        surface.addView(heading);
        ScrollView scroll = new ScrollView(activity);
        LinearLayout rows = column(activity);
        rows.setPadding(dp(activity, 8), 0, dp(activity, 8), dp(activity, 16));
        for (ChannelProfile.Link link : links) rows.addView(linkRow(activity, link, false));
        scroll.addView(rows);
        scroll.setFillViewport(false);
        scroll.setNestedScrollingEnabled(true);
        surface.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
        outer.addView(surface, new LinearLayout.LayoutParams(-1, -2));
        dialog.setContentView(outer);
        // Cap the complete surface, including its heading and padding, rather than estimating row heights.
        dialog.getBehavior().setMaxHeight(Math.round(activity.getResources().getDisplayMetrics().heightPixels * 0.6f));
        dialog.show();
        dialog.getBehavior().setSkipCollapsed(true);
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        return dialog;
    }

    static View linkRow(Activity activity, ChannelProfile.Link link, boolean blueAddress) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(activity, 12), dp(activity, 12), dp(activity, 12), dp(activity, 12));
        row.setMinimumHeight(dp(activity, 72));
        ImageView icon = new ImageView(activity);
        icon.setImageResource(R.drawable.ic_channel_globe);
        icon.setColorFilter(ink(activity));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(activity, 24), dp(activity, 24));
        iconParams.setMarginEnd(dp(activity, 20)); row.addView(icon, iconParams);
        if (link.iconUrl != null) {
            android.graphics.drawable.Drawable fallback = ContextCompat.getDrawable(activity, R.drawable.ic_channel_globe).mutate();
            fallback.setTint(ink(activity));
            icon.clearColorFilter();
            Glide.with(icon).load(link.iconUrl).placeholder(fallback).error(fallback).into(icon);
        } else if (isDomain(link.url, "x.com") || isDomain(link.url, "twitter.com")) icon.setImageResource(R.drawable.ic_channel_x);
        else if (isDomain(link.url, "t.me") || isDomain(link.url, "telegram.me")) icon.setImageResource(R.drawable.ic_channel_telegram);
        LinearLayout labels = column(activity);
        labels.addView(text(activity, link.title, 16, false));
        TextView address = text(activity, displayUrl(link.url), 14, !blueAddress);
        if (blueAddress) address.setTextColor(ContextCompat.getColor(activity, R.color.mobile_channel_link));
        address.setMaxLines(2); address.setEllipsize(android.text.TextUtils.TruncateAt.END);
        labels.addView(address);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        row.setFocusable(true); row.setOnClickListener(view -> open(activity, link.url));
        return row;
    }

    private static View infoRow(Activity activity, String value, int image, Runnable action) {
        LinearLayout row = new LinearLayout(activity); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(activity, 56));
        ImageView icon = new ImageView(activity); icon.setImageResource(image); icon.setColorFilter(ink(activity));
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(dp(activity, 24), dp(activity, 24));
        size.setMarginEnd(dp(activity, 16)); row.addView(icon, size);
        TextView label = text(activity, value, 16, false);
        if (action != null) { label.setTextColor(ContextCompat.getColor(activity, R.color.mobile_channel_link)); row.setOnClickListener(view -> action.run()); }
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        return row;
    }

    private void heading(LinearLayout body, int label) {
        TextView heading = text(activity, activity.getString(label), 20, false);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setPadding(0, dp(activity, 24), 0, dp(activity, 12));
        ViewCompat.setAccessibilityHeading(heading, true);
        body.addView(heading);
    }
    private static LinearLayout column(Activity activity) { LinearLayout view = new LinearLayout(activity); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private static TextView text(Activity activity, String value, int sp, boolean secondary) {
        TextView text = new TextView(activity); text.setText(value); text.setTextSize(sp);
        text.setTextColor(ContextCompat.getColor(activity, secondary ? R.color.mobile_color_on_surface_secondary : R.color.mobile_color_on_surface)); return text;
    }
    private static int dp(Activity activity, int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
    private static int ink(Activity activity) { return ContextCompat.getColor(activity, R.color.mobile_color_on_surface); }
    private static int background(Activity activity) { TypedValue value = new TypedValue(); activity.getTheme().resolveAttribute(R.attr.newtubeColorBackground, value, true); return value.data; }
    private static boolean isDomain(String url, String domain) { String host = Uri.parse(url).getHost(); return domain.equalsIgnoreCase(host) || ("www." + domain).equalsIgnoreCase(host); }
    static String displayUrl(String url) { return url.replaceFirst("^https?://(www\\.)?", ""); }
    private static void open(Activity activity, String url) {
        Uri uri = Uri.parse(url);
        if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) || uri.getHost() == null) return;
        try { activity.startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
        catch (ActivityNotFoundException error) { MobileSnackbar.show(activity, R.string.mobile_channel_link_unavailable); }
    }
}
