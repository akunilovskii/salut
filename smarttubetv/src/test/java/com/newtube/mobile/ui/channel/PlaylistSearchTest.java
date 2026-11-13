package com.newtube.mobile.ui.channel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import android.app.Application;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class PlaylistSearchTest {
    @Test
    public void matchesTitleChannelAndMetadataWithoutReordering() {
        Video first = video("Guitar lessons", "Music Academy", "Music Academy • 5K views • yesterday");
        Video second = video("Astronomy", "Night Sky", "Night Sky • 2K views • 3 weeks ago");
        List<Video> videos = Arrays.asList(first, second);

        assertSame(first, PlaylistSearch.filter(videos, "  GUITAR  ").get(0));
        assertSame(second, PlaylistSearch.filter(videos, "night sky").get(0));
        assertSame(second, PlaylistSearch.filter(videos, "3 WEEKS").get(0));
        assertEquals(2, PlaylistSearch.filter(videos, "").size());
        assertSame(first, PlaylistSearch.filter(videos, "").get(0));
        assertEquals(0, PlaylistSearch.filter(videos, "not present").size());
    }

    private static Video video(String title, String author, String secondTitle) {
        Video video = new Video();
        video.title = title;
        video.author = author;
        video.secondTitle = secondTitle;
        return video;
    }
}
