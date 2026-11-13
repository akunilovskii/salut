package com.newtube.mobile.ui.channel;

import android.text.TextUtils;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Filters the already loaded items of a playlist without changing its playback order. */
public final class PlaylistSearch {
    private PlaylistSearch() { }

    public static List<Video> filter(List<Video> videos, String query) {
        List<Video> matches = new ArrayList<>();
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        for (Video video : videos) {
            if (needle.isEmpty() || contains(video.getTitle(), needle)
                    || contains(video.getAuthor(), needle)
                    || contains(video.getSecondTitle(), needle)) {
                matches.add(video);
            }
        }
        return matches;
    }

    private static boolean contains(CharSequence text, String needle) {
        return !TextUtils.isEmpty(text) && text.toString().toLowerCase(Locale.ROOT).contains(needle);
    }
}
