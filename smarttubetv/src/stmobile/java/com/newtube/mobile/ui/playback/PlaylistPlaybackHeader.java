package com.newtube.mobile.ui.playback;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.liskovsoft.smartyoutubetv2.common.app.models.playback.QueuePlaybackMode.Session;
import com.liskovsoft.smartyoutubetv2.tv.R;

/** Shared source-playlist identity and independent, session-scoped transport controls. */
public final class PlaylistPlaybackHeader extends LinearLayout {
    private final TextView mTitle;
    private final TextView mPosition;
    private final TextView mOwner;
    private final ImageView mPrivacy;
    private final ImageButton mRepeat;
    private final ImageButton mShuffle;

    public PlaylistPlaybackHeader(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.mobile_watch_playlist_heading, this, true);
        mTitle = findViewById(R.id.playlist_heading_title);
        mPosition = findViewById(R.id.playlist_heading_position);
        mOwner = findViewById(R.id.playlist_heading_owner);
        mPrivacy = findViewById(R.id.playlist_heading_privacy);
        mRepeat = findViewById(R.id.playlist_heading_repeat);
        mShuffle = findViewById(R.id.playlist_heading_shuffle);
        mTitle.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        mPosition.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        mOwner.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setActions(Runnable repeat, Runnable shuffle) {
        mRepeat.setOnClickListener(view -> repeat.run());
        mShuffle.setOnClickListener(view -> shuffle.run());
    }

    void bind(String title, PlaylistHeaderInfo info, int position, int total, Session mode) {
        mTitle.setText(title);
        mPosition.setText(position > 0 && total >= position ? "· " + position + "/" + total : "");
        String owner = info != null ? info.owner : null;
        mOwner.setText(owner);
        mOwner.setVisibility(owner != null && !owner.isEmpty() ? VISIBLE : GONE);
        int privacy = info != null ? info.privacy : PlaylistHeaderInfo.PRIVACY_UNKNOWN;
        int privacyLabel = privacy == PlaylistHeaderInfo.PRIVACY_PRIVATE ? R.string.mobile_playlist_private
                : privacy == PlaylistHeaderInfo.PRIVACY_PUBLIC ? R.string.mobile_playlist_public
                : R.string.mobile_playlist_unlisted;
        mPrivacy.setVisibility(privacy != PlaylistHeaderInfo.PRIVACY_UNKNOWN ? VISIBLE : GONE);
        mPrivacy.setImageTintList(android.content.res.ColorStateList.valueOf(mOwner.getCurrentTextColor()));
        mPrivacy.setImageResource(privacy == PlaylistHeaderInfo.PRIVACY_PRIVATE ? R.drawable.ic_playlist_private
                : privacy == PlaylistHeaderInfo.PRIVACY_PUBLIC ? R.drawable.ic_channel_globe : R.drawable.ic_channel_link);
        findViewById(R.id.playlist_heading_owner_row).setVisibility(mOwner.getVisibility() == VISIBLE
                || mPrivacy.getVisibility() == VISIBLE ? VISIBLE : GONE);
        String description = title + (position > 0 ? ". " + getResources().getString(
                R.string.mobile_playlist_position, position, total) : "");
        if (owner != null && !owner.isEmpty()) description += ". " + owner;
        if (privacy != PlaylistHeaderInfo.PRIVACY_UNKNOWN) description += ". " + getResources().getString(privacyLabel);
        findViewById(R.id.playlist_heading_details).setContentDescription(description);
        int repeat = mode.repeat();
        boolean shuffle = mode.isShuffling();
        mRepeat.setSelected(repeat != Session.REPEAT_OFF);
        mShuffle.setSelected(shuffle);
        mRepeat.setContentDescription(getResources().getString(repeat == Session.REPEAT_OFF
                ? R.string.mobile_playlist_repeat_off : repeat == Session.REPEAT_ALL
                ? R.string.mobile_playlist_repeat_all : R.string.mobile_playlist_repeat_one));
        mShuffle.setContentDescription(getResources().getString(shuffle
                ? R.string.mobile_playlist_shuffle_on : R.string.mobile_playlist_shuffle_off));
        findViewById(R.id.playlist_heading_repeat_one).setVisibility(repeat == Session.REPEAT_ONE ? VISIBLE : GONE);
        findViewById(R.id.playlist_heading_repeat_dot).setVisibility(repeat != Session.REPEAT_OFF ? VISIBLE : GONE);
        findViewById(R.id.playlist_heading_shuffle_dot).setVisibility(shuffle ? VISIBLE : GONE);
    }
}
