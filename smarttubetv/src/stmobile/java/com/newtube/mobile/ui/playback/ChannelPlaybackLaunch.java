package com.newtube.mobile.ui.playback;

import android.os.SystemClock;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import java.util.ArrayList;
import java.util.List;

/** One-shot handoff from the visible channel tab to the existing player queue. */
public final class ChannelPlaybackLaunch {
    private static String sVideoId;
    private static List<Video> sVideos;
    private static long sPreparedAt;

    private ChannelPlaybackLaunch() { }

    public static void prepare(Video video, List<Video> visible) {
        sVideoId = video != null ? video.videoId : null;
        sVideos = new ArrayList<>(visible);
        sPreparedAt = SystemClock.elapsedRealtime();
    }

    static List<Video> take(Video video) {
        if (sVideos == null) return null;
        List<Video> result = video != null && sVideoId != null && sVideoId.equals(video.videoId)
                && SystemClock.elapsedRealtime() - sPreparedAt < 30_000 ? sVideos : null;
        sVideos = null;
        sVideoId = null;
        return result;
    }
}
