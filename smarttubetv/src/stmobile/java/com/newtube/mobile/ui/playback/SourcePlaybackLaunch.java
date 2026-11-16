package com.newtube.mobile.ui.playback;

import android.os.SystemClock;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Playlist;
import java.util.ArrayList;
import java.util.List;

/** One-shot handoff of an already loaded source list to the existing player queue. */
public final class SourcePlaybackLaunch {
    private static String sVideoId;
    private static List<Video> sVideos;
    private static boolean sPlaylist;
    private static VideoGroup sGroup;
    private static List<Video> sPendingQueue;
    private static long sPreparedAt;
    private static String sTitle;
    private static PlaylistHeaderInfo sHeaderInfo;

    private SourcePlaybackLaunch() { }

    public static void prepare(Video video, List<Video> visible) {
        prepare(video, visible, false);
    }

    public static void preparePlaylist(Video video, List<Video> visible, String title) {
        prepare(video, visible, true);
        sTitle = title;
    }

    public static void preparePlaylist(Video video, List<Video> visible, Video playlist,
            String title, boolean watchLater) {
        preparePlaylist(video, visible, title);
        com.liskovsoft.mediaserviceinterfaces.oauth.Account account =
                com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager.instance().getSelectedAccount();
        sHeaderInfo = PlaylistHeaderInfo.from(playlist, title, watchLater, account != null ? account.getName() : null);
    }

    private static void prepare(Video video, List<Video> visible, boolean playlist) {
        sVideoId = video != null ? video.videoId : null;
        sVideos = new ArrayList<>(visible);
        sPlaylist = playlist;
        sTitle = null;
        sHeaderInfo = null;
        sGroup = video != null ? video.getGroup() : null;
        List<Video> pending = Playlist.instance().getAllAfterCurrent();
        sPendingQueue = playlist && pending != null ? new ArrayList<>(pending) : null;
        sPreparedAt = SystemClock.elapsedRealtime();
    }

    static final class Snapshot {
        final List<Video> videos;
        final boolean playlist;
        final VideoGroup group;
        final String title;
        final PlaylistHeaderInfo headerInfo;

        Snapshot(List<Video> videos, boolean playlist, VideoGroup group, String title) {
            this.videos = videos;
            this.playlist = playlist;
            this.group = group;
            this.title = title;
            this.headerInfo = sHeaderInfo;
        }
    }

    static Snapshot takeSource(Video video) {
        if (sVideos == null || video == null || video.videoId == null) return null;
        Snapshot result = sVideoId != null && sVideoId.equals(video.videoId)
                && SystemClock.elapsedRealtime() - sPreparedAt < 30_000
                ? new Snapshot(sVideos, sPlaylist, sGroup, sTitle) : null;
        if (result != null && result.playlist) {
            Playlist.instance().setCurrentFromSource(video, sPendingQueue);
        }
        sPendingQueue = null;
        sVideos = null;
        sVideoId = null;
        sGroup = null;
        sTitle = null;
        sHeaderInfo = null;
        return result;
    }

    static List<Video> take(Video video) {
        Snapshot source = takeSource(video);
        return source != null ? source.videos : null;
    }
}
