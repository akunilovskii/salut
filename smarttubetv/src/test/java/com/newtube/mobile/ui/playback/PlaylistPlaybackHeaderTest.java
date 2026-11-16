package com.newtube.mobile.ui.playback;

import static org.junit.Assert.*;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.TextView;

import com.liskovsoft.mediaserviceinterfaces.data.PlaylistInfo;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.QueuePlaybackMode.Session;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerConstants;
import com.liskovsoft.smartyoutubetv2.tv.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class, qualifiers = "mdpi")
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PlaylistPlaybackHeaderTest {
    @Test public void watchLaterShowsItsOwnerPrivacyAndPositionWithActionsOnTheRight() {
        PlaylistPlaybackHeader header = header(1.15f);
        Session mode = mode();
        header.bind("Watch later", PlaylistHeaderInfo.from(null, "Watch later", true, "Owner"), 4, 22, mode);
        measure(header, 411);
        assertEquals("Watch later", ((TextView) header.findViewById(R.id.playlist_heading_title)).getText().toString());
        assertEquals("· 4/22", ((TextView) header.findViewById(R.id.playlist_heading_position)).getText().toString());
        assertEquals("Owner", ((TextView) header.findViewById(R.id.playlist_heading_owner)).getText().toString());
        assertEquals(View.VISIBLE, header.findViewById(R.id.playlist_heading_privacy).getVisibility());
        assertEquals(header.getWidth(), right(header.findViewById(R.id.playlist_heading_shuffle)));
        assertEquals(40, header.findViewById(R.id.playlist_heading_repeat).getWidth());
        assertEquals(48, header.findViewById(R.id.playlist_heading_shuffle).getHeight());
    }

    @Test public void longTitlesAndLargeTextLeaveRoomForTheCounterAndBothControls() {
        PlaylistPlaybackHeader header = header(1.5f);
        header.bind("A long playlist title that cannot fit on a narrow screen",
                PlaylistHeaderInfo.from(null, "", true, "A long owner name that also needs truncation"),
                120, 376, mode());
        measure(header, 320);
        TextView title = header.findViewById(R.id.playlist_heading_title);
        View count = header.findViewById(R.id.playlist_heading_position);
        View repeat = header.findViewById(R.id.playlist_heading_repeat);
        assertEquals(1, title.getLineCount());
        assertTrue(title.getWidth() > 0);
        assertTrue(right(title) <= left(count));
        assertTrue(right(count) <= left(repeat));
        assertEquals(320, right(header.findViewById(R.id.playlist_heading_shuffle)));
    }

    @Test public void repeatOneAndShuffleCanBothShowTheirActualStateAndRespondToTaps() {
        PlaylistPlaybackHeader header = header(1f);
        Session mode = mode();
        mode.cycleRepeat();
        mode.cycleRepeat();
        mode.toggleShuffle();
        header.bind("Playlist", null, 1, 5, mode);
        int[] calls = {0, 0};
        header.setActions(() -> calls[0]++, () -> calls[1]++);
        View repeat = header.findViewById(R.id.playlist_heading_repeat);
        View shuffle = header.findViewById(R.id.playlist_heading_shuffle);
        assertTrue(repeat.isSelected());
        assertTrue(shuffle.isSelected());
        assertEquals(View.VISIBLE, header.findViewById(R.id.playlist_heading_repeat_one).getVisibility());
        assertEquals(View.VISIBLE, header.findViewById(R.id.playlist_heading_shuffle_dot).getVisibility());
        repeat.performClick();
        shuffle.performClick();
        assertArrayEquals(new int[] {1, 1}, calls);
    }

    @Test public void watchLaterWithoutACardUsesMatchingMetadataForItsFullSizeAndPosition() {
        PlaylistHeaderInfo info = PlaylistHeaderInfo.from(null, "Watch later", true, "Owner");
        Video current = new Video();
        current.playlistInfo = info("WL", 376, 100);
        assertEquals(376, info.total(current, 50));
        assertEquals(101, info.position(current, 2, 376));
        current.playlistInfo = info("PLother", 999, 500);
        assertEquals(50, info.total(current, 50));
        assertEquals(3, info.position(current, 2, 50));
    }

    @Test public void knownTotalIsNotReplacedByTheFirstLoadedPage() {
        Video playlist = new Video();
        playlist.playlistId = "PLchosen";
        playlist.author = "Playlist owner";
        playlist.badge = "376 videos";
        playlist.secondTitle = "Private";
        PlaylistHeaderInfo info = PlaylistHeaderInfo.from(playlist, "Chosen", false, "Other account");
        assertEquals(376, info.total(null, 50));
        assertEquals("Playlist owner", info.owner);
        assertEquals(PlaylistHeaderInfo.PRIVACY_PRIVATE, info.privacy);
        Video current = new Video();
        current.playlistInfo = info("PLchosen", 400, 100);
        assertEquals(400, info.total(current, 50));
        assertEquals(101, info.position(current, 2, 400));
        current.playlistInfo = info("PLother", 999, 500);
        assertEquals(376, info.total(current, 50));
        assertEquals(3, info.position(current, 2, 376));
    }

    @Test public void unknownAndApproximateMetadataDoNotInventAnOwnerPrivacyOrExactTotal() {
        Video playlist = new Video();
        playlist.badge = "1.2K videos";
        PlaylistHeaderInfo info = PlaylistHeaderInfo.from(playlist, "Unknown", false, "Signed-in viewer");
        assertNull(info.owner);
        assertEquals(PlaylistHeaderInfo.PRIVACY_UNKNOWN, info.privacy);
        assertEquals(50, info.total(null, 50));
    }

    @Test public void watchLaterUsesTheAccountNameInsteadOfACompositeProviderAuthor() {
        Video playlist = new Video();
        playlist.playlistId = "WL";
        playlist.author = "Private • @handle";
        assertEquals("Account name", PlaylistHeaderInfo.from(playlist, "Watch later", true, "Account name").owner);
        playlist.playlistId = "PLother";
        PlaylistHeaderInfo other = PlaylistHeaderInfo.from(playlist, "Other", false, "Viewer");
        assertEquals("@handle", other.owner);
        assertEquals(PlaylistHeaderInfo.PRIVACY_PRIVATE, other.privacy);
    }

    private static Session mode() {
        Session mode = new Session();
        mode.start(PlayerConstants.PLAYBACK_MODE_ALL);
        return mode;
    }

    private static PlaylistInfo info(String id, int size, int index) {
        return new PlaylistInfo() {
            @Override public String getTitle() { return "Playlist"; }
            @Override public String getPlaylistId() { return id; }
            @Override public boolean isSelected() { return true; }
            @Override public int getSize() { return size; }
            @Override public int getCurrentIndex() { return index; }
        };
    }

    private static PlaylistPlaybackHeader header(float fontScale) {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Configuration config = new Configuration(activity.getResources().getConfiguration());
        config.fontScale = fontScale;
        Context context = new ContextThemeWrapper(activity.createConfigurationContext(config), R.style.Theme_NewTube);
        return new PlaylistPlaybackHeader(context, null);
    }

    private static void measure(View view, int width) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.AT_MOST));
        view.layout(0, 0, width, view.getMeasuredHeight());
    }

    private static int left(View view) {
        int left = view.getLeft();
        while (view.getParent() instanceof View) {
            view = (View) view.getParent();
            left += view.getLeft();
        }
        return left;
    }

    private static int right(View view) { return left(view) + view.getWidth(); }
}
