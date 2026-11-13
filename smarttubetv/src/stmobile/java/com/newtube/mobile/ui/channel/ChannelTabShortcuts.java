package com.newtube.mobile.ui.channel;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

import android.net.Uri;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * NEWTUBE(#63): the TV channel page ends with an untitled section of shortcut cards - channel cards
 * for the channel itself, one per tab (Videos, Live, Releases, Playlists), each carrying that tab's
 * browse params and nothing else. Labelled with the generic untitled fallback it read "Videos", the
 * same as the first tab; it is labelled "More" instead.
 */
final class ChannelTabShortcuts {
    private ChannelTabShortcuts() {
    }

    static final class Destination {
        final String title;
        final String params;
        Destination(String title, String params) { this.title = title; this.params = params; }
    }

    static List<Video> withoutVisibleTabs(List<Video> shortcuts, List<Destination> visible, String channelId) {
        List<Video> result = new ArrayList<>();
        for (Video video : shortcuts) {
            boolean repeated = false;
            if (channelId != null && channelId.equals(video.channelId)) {
                String key = destinationKey(video.title, video.playlistParams);
                for (Destination destination : visible) {
                    if (key.equals(destinationKey(destination.title, destination.params))) { repeated = true; break; }
                }
            }
            if (!repeated) result.add(video);
        }
        return result;
    }

    private static String destinationKey(String title, String params) {
        String decoded = params == null ? "" : Uri.decode(params);
        if (decoded.startsWith("8gYHEgVq") || decoded.startsWith("EgZ2aWRlb3MYAyAA")) return "videos";
        if (decoded.startsWith("8gYHEgVK") || decoded.startsWith("EgZ2aWRlb3MYAyACOAJ")) return "live";
        if (decoded.startsWith("EgZ2aWRlb3MYAyACOAR")) return "past-live";
        if (decoded.startsWith("8gYLEgli")) return "playlists";
        if (!decoded.isEmpty()) return "params:" + decoded;
        String name = title == null ? "" : title.trim().toLowerCase(Locale.ROOT);
        switch (name) {
            case "videos": case "видео": return "videos";
            case "live": case "трансляции": return "live";
            case "past live streams": case "past streams": case "прошедшие трансляции": return "past-live";
            case "playlists": case "плейлисты": return "playlists";
            default: return name.isEmpty() ? "params:" + decoded : "title:" + name;
        }
    }

    /** Whether every card is a channel-tab shortcut (and there is at least one). */
    static boolean isShortcutRow(List<Video> videos) {
        if (videos == null || videos.isEmpty()) {
            return false;
        }
        for (Video video : videos) {
            if (!isShortcut(video)) {
                return false;
            }
        }
        return true;
    }

    /** A channel card that opens one of a channel's tabs: a channel id plus tab params, no video or playlist. */
    static boolean isShortcut(Video video) {
        return video != null && video.videoId == null && video.playlistId == null
                && video.channelId != null && video.playlistParams != null && !video.playlistParams.isEmpty();
    }
}
