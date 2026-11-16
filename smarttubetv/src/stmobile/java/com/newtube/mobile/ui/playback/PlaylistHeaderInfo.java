package com.newtube.mobile.ui.playback;

import com.liskovsoft.mediaserviceinterfaces.data.PlaylistInfo;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Source identity comes from the playlist card, never from the playing video's channel. */
final class PlaylistHeaderInfo {
    static final int PRIVACY_UNKNOWN = 0;
    static final int PRIVACY_PRIVATE = 1;
    static final int PRIVACY_PUBLIC = 2;
    static final int PRIVACY_UNLISTED = 3;
    final String playlistId;
    final String title;
    final String owner;
    final int privacy;
    private final int mKnownSize;

    private PlaylistHeaderInfo(String playlistId, String title, String owner, int privacy, int size) {
        this.playlistId = playlistId;
        this.title = title;
        this.owner = owner;
        this.privacy = privacy;
        mKnownSize = size;
    }

    static PlaylistHeaderInfo from(Video playlist, String title, boolean watchLater, String accountName) {
        String id = playlist != null ? playlist.getPlaylistId() : null;
        if (watchLater && (id == null || id.isEmpty())) id = "WL";
        boolean personal = watchLater || "WL".equals(id) || "LL".equals(id);
        String rawOwner = playlist != null ? playlist.author : null;
        String owner = rawOwner != null ? rawOwner.replaceFirst("(?i)^(?:private|public|unlisted)\\s*[•·]\\s*", "").trim() : null;
        if (owner != null && owner.matches("(?i)private|public|unlisted")) owner = null;
        if (personal && accountName != null && !accountName.isEmpty()) owner = accountName;
        String details = playlist != null ? String.valueOf(playlist.getSecondTitle()) : "";
        String lower = (details + " " + rawOwner + " " + (playlist != null ? playlist.badge : "")).toLowerCase(Locale.ROOT);
        int privacy = personal || lower.contains("private") ? PRIVACY_PRIVATE
                : lower.contains("unlisted") ? PRIVACY_UNLISTED
                : lower.contains("public") ? PRIVACY_PUBLIC : PRIVACY_UNKNOWN;
        int size = playlist != null && playlist.playlistInfo != null ? playlist.playlistInfo.getSize() : 0;
        if (size <= 0 && playlist != null) size = countBadge(playlist.badge);
        return new PlaylistHeaderInfo(id, title, owner, privacy, size);
    }

    int total(Video current, int loaded) {
        PlaylistInfo info = current != null ? current.playlistInfo : null;
        int serverSize = info != null && playlistId != null && playlistId.equals(info.getPlaylistId())
                ? info.getSize() : 0;
        return Math.max(loaded, Math.max(mKnownSize, serverSize));
    }

    int position(Video current, int sourceIndex, int total) {
        PlaylistInfo info = current != null ? current.playlistInfo : null;
        int serverIndex = info != null && playlistId != null && playlistId.equals(info.getPlaylistId())
                ? info.getCurrentIndex() + 1 : 0;
        return serverIndex > 0 && serverIndex <= total ? serverIndex : sourceIndex + 1;
    }

    private static int countBadge(String badge) {
        if (badge == null) return 0;
        Matcher match = Pattern.compile("^\\s*([\\d][\\d,\\s\\u00a0]*)\\s*(?:[^\\d.,\\s].*)?$").matcher(badge);
        if (!match.matches()) return 0;
        try { return Integer.parseInt(match.group(1).replaceAll("[^0-9]", "")); }
        catch (NumberFormatException ignored) { return 0; }
    }
}
