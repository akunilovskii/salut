package com.liskovsoft.smartyoutubetv2.common.app.models.playback;

import static org.junit.Assert.*;

import android.app.Application;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerConstants;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

/**
 * The playlist page's Shuffle used to write the PERSISTED repeat mode, so one tap on one playlist
 * shuffled everything played afterwards. These pin the replacement: an override that is scoped to
 * the playlist that armed it, outranked by an explicit choice, and gone by itself once playback
 * moves on.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class QueuePlaybackModeTest {
    private static final int STORED = PlayerConstants.PLAYBACK_MODE_ALL;

    @After public void disarm() {
        QueuePlaybackMode.clear();
    }

    private static Video inPlaylist(String playlistId) {
        Video video = new Video();
        video.playlistId = playlistId;
        return video;
    }

    @Test public void withNothingArmedTheStoredModeIsWhatPlays() {
        assertFalse(QueuePlaybackMode.isArmed());
        assertEquals(STORED, QueuePlaybackMode.apply(inPlaylist("PL1"), STORED));
    }

    @Test public void theArmedPlaylistShuffles() {
        QueuePlaybackMode.shuffle("PL1");

        assertTrue(QueuePlaybackMode.isArmed());
        assertEquals(PlayerConstants.PLAYBACK_MODE_SHUFFLE,
                QueuePlaybackMode.apply(inPlaylist("PL1"), STORED));
    }

    @Test public void openingAnotherPlaylistDisarmsTheOverride() {
        QueuePlaybackMode.shuffle("PL1");
        QueuePlaybackMode.onNewVideo(inPlaylist("PL2"));

        assertFalse("Navigating away is what disarms it - nothing has to remember to clear",
                QueuePlaybackMode.isArmed());
        assertEquals(STORED, QueuePlaybackMode.apply(inPlaylist("PL2"), STORED));
    }

    @Test public void openingAVideoWithNoPlaylistDisarmsItToo() {
        QueuePlaybackMode.shuffle("PL1");
        QueuePlaybackMode.onNewVideo(new Video());

        assertFalse(QueuePlaybackMode.isArmed());
    }

    @Test public void advancingWITHINTheQueueKeepsShuffling() {
        QueuePlaybackMode.shuffle("PL1");
        QueuePlaybackMode.onNewVideo(inPlaylist("PL1"));

        assertTrue(QueuePlaybackMode.isArmed());
        assertEquals(PlayerConstants.PLAYBACK_MODE_SHUFFLE,
                QueuePlaybackMode.apply(inPlaylist("PL1"), STORED));
    }

    /**
     * The reason the disarm hangs off onNewVideo rather than off apply(): apply() is read from a
     * prefetch tick and from menus, where a half-resolved video can momentarily carry no playlist
     * id. Reading must never end the shuffle.
     */
    @Test public void aReadNeverDisarmsTheOverride() {
        QueuePlaybackMode.shuffle("PL1");

        assertEquals(STORED, QueuePlaybackMode.apply(new Video(), STORED));
        assertEquals(STORED, QueuePlaybackMode.apply(null, STORED));
        assertTrue("A passing read must not end the queue's shuffle", QueuePlaybackMode.isArmed());
        assertEquals(PlayerConstants.PLAYBACK_MODE_SHUFFLE,
                QueuePlaybackMode.apply(inPlaylist("PL1"), STORED));
    }

    @Test public void anExplicitChoiceOutranksTheQueue() {
        QueuePlaybackMode.shuffle("PL1");
        QueuePlaybackMode.clear();

        assertFalse(QueuePlaybackMode.isArmed());
        assertEquals(STORED, QueuePlaybackMode.apply(inPlaylist("PL1"), STORED));
    }

    @Test public void theStoredModeSurvivesTheWholeEpisode() {
        QueuePlaybackMode.shuffle("PL1");
        assertEquals(PlayerConstants.PLAYBACK_MODE_SHUFFLE,
                QueuePlaybackMode.apply(inPlaylist("PL1"), STORED));

        // The whole point: whatever was stored is handed back untouched the moment the queue ends.
        QueuePlaybackMode.onNewVideo(inPlaylist("PL2"));
        assertEquals(STORED, QueuePlaybackMode.apply(inPlaylist("PL2"), STORED));
    }
    @Test public void sessionRepeatAndShuffleAreIndependentAndDoNotChangeGlobalMode() {
        QueuePlaybackMode.Session session = new QueuePlaybackMode.Session();
        session.start(STORED);
        session.cycleRepeat();
        session.toggleShuffle();
        assertEquals(QueuePlaybackMode.Session.REPEAT_ALL, session.repeat());
        assertTrue(session.isShuffling());
        assertEquals(PlayerConstants.PLAYBACK_MODE_SHUFFLE, session.playbackMode());
        session.cycleRepeat();
        assertEquals(QueuePlaybackMode.Session.REPEAT_ONE, session.repeat());
        assertTrue(session.isShuffling());
        assertEquals(PlayerConstants.PLAYBACK_MODE_ONE, session.playbackMode());
        session.cycleRepeat();
        assertEquals(QueuePlaybackMode.Session.REPEAT_OFF, session.repeat());
        assertTrue(session.isShuffling());
        assertFalse(QueuePlaybackMode.isArmed());
        assertEquals(STORED, QueuePlaybackMode.apply(inPlaylist("PL1"), STORED));
    }

    @Test public void sessionInheritsPauseUntilTheUserExplicitlySelectsAnotherMode() {
        QueuePlaybackMode.Session session = new QueuePlaybackMode.Session();
        session.start(PlayerConstants.PLAYBACK_MODE_PAUSE);
        assertEquals(PlayerConstants.PLAYBACK_MODE_PAUSE, session.playbackMode());
        session.cycleRepeat();
        assertEquals(PlayerConstants.PLAYBACK_MODE_ALL, session.playbackMode());
        session.start(PlayerConstants.PLAYBACK_MODE_PAUSE);
        assertEquals(QueuePlaybackMode.Session.REPEAT_OFF, session.repeat());
        assertFalse(session.isShuffling());
    }

    @Test public void sessionReflectsAnExplicitGlobalPickerChoiceEvenIfItsValueHasNotChanged() {
        QueuePlaybackMode.Session session = new QueuePlaybackMode.Session();
        session.start(STORED);
        session.cycleRepeat();
        session.toggleShuffle();
        QueuePlaybackMode.clear();
        session.refresh(STORED);
        assertEquals(QueuePlaybackMode.Session.REPEAT_OFF, session.repeat());
        assertFalse(session.isShuffling());
        assertEquals(STORED, session.playbackMode());
    }

    @Test public void unrelatedNavigationCannotResetAnOwnedPlaylistSession() {
        QueuePlaybackMode.Session session = new QueuePlaybackMode.Session();
        session.start(STORED);
        session.cycleRepeat();
        session.toggleShuffle();
        QueuePlaybackMode.onNewVideo(new Video());
        session.refresh(STORED);
        assertTrue(session.isShuffling());
        assertEquals(QueuePlaybackMode.Session.REPEAT_ALL, session.repeat());
    }

}
