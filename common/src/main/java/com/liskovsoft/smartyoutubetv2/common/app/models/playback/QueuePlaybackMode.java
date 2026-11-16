package com.liskovsoft.smartyoutubetv2.common.app.models.playback;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerConstants;

/**
 * Shuffle that belongs to ONE playlist instead of to the whole app.
 *
 * <p>The playlist page's Shuffle button used to call {@code PlayerData.setPlaybackMode(SHUFFLE)},
 * which is PERSISTED: one tap on one playlist left every later video shuffling until the user
 * went looking for the repeat picker and turned it back. The toast on that button existed only to
 * warn about the side effect. YouTube scopes shuffle to the queue you started it from, and that is
 * what this holds - an override read on top of the stored mode, alive only while the playing video
 * still belongs to the playlist that armed it, and never written to disk.</p>
 *
 * <p>Deliberately static and transient: it describes the playback session in front of the user, so
 * a process restart should drop it, and every reader ({@code VideoLoaderController}) already
 * reaches the stored mode through a singleton anyway.</p>
 */
public final class QueuePlaybackMode {
    private static String sPlaylistId;
    private static long sExplicitModeRevision;

    private QueuePlaybackMode() {}

    /** Shuffle whatever plays from {@code playlistId}, until playback leaves it. */
    public static void shuffle(String playlistId) {
        sPlaylistId = playlistId;
    }

    /**
     * Drop the override. Called whenever the user states a repeat mode outright
     * ({@code PlayerData.setPlaybackMode}) - an explicit choice outranks a queue-scoped one.
     */
    public static void clear() {
        sPlaylistId = null;
        sExplicitModeRevision++;
    }

    public static boolean isArmed() {
        return sPlaylistId != null;
    }

    /**
     * Whether the override covers {@code video} right now - for UI that has to SHOW the state.
     * Unlike {@link #apply} this never disarms anything: opening a menu must not change how
     * playback behaves.
     */
    public static boolean coversQueueOf(Video video) {
        return sPlaylistId != null && video != null
                && Helpers.equals(sPlaylistId, video.getPlaylistId());
    }

    /**
     * Playback moved to {@code video} - drop the override if that video is outside the armed
     * playlist. This is the ONLY thing that disarms by navigation, so no screen has to remember to
     * call {@link #clear()} when the user simply walks off to an unrelated video.
     *
     * <p>Deliberately not folded into {@link #apply}: that one is read from a prefetch tick and
     * from menus, and a video can momentarily carry no playlist id while it is being resolved.
     * A read that disarmed would end the shuffle on a passing glance rather than on a decision.</p>
     */
    public static void onNewVideo(Video video) {
        if (sPlaylistId != null && video != null
                && !Helpers.equals(sPlaylistId, video.getPlaylistId())) {
            sPlaylistId = null;
        }
    }

    /**
     * The mode to actually play by - a pure read.
     *
     * @param video the video about to play, or {@code null} between videos
     * @param storedMode {@code PlayerData.getPlaybackMode()}
     */
    public static int apply(Video video, int storedMode) {
        return coversQueueOf(video) ? PlayerConstants.PLAYBACK_MODE_SHUFFLE : storedMode;
    }
    /** Independent transient controls owned by one source-playlist playback session. */
    public static final class Session {
        public static final int REPEAT_OFF = 0;
        public static final int REPEAT_ALL = 1;
        public static final int REPEAT_ONE = 2;
        private int mInheritedMode = PlayerConstants.PLAYBACK_MODE_ALL;
        private Integer mRepeatOverride;
        private Boolean mShuffleOverride;
        private long mRevision;

        public void start(int effectiveMode) {
            mInheritedMode = effectiveMode;
            mRepeatOverride = null;
            mShuffleOverride = null;
            mRevision = sExplicitModeRevision;
        }

        public void refresh(int storedMode) {
            // Choosing a mode explicitly in the existing global picker also applies here.
            // Merely opening another video or reading a setting must not reset these controls.
            if (mRevision != sExplicitModeRevision) start(storedMode);
        }

        public int repeat() {
            return mRepeatOverride != null ? mRepeatOverride
                    : mInheritedMode == PlayerConstants.PLAYBACK_MODE_ONE ? REPEAT_ONE : REPEAT_OFF;
        }

        public boolean isShuffling() {
            return mShuffleOverride != null ? mShuffleOverride
                    : mInheritedMode == PlayerConstants.PLAYBACK_MODE_SHUFFLE;
        }

        public void cycleRepeat() { mRepeatOverride = (repeat() + 1) % 3; }

        public void toggleShuffle() { mShuffleOverride = !isShuffling(); }

        public int playbackMode() {
            if (repeat() == REPEAT_ONE) return PlayerConstants.PLAYBACK_MODE_ONE;
            if (mRepeatOverride != null || mShuffleOverride != null) {
                return isShuffling() ? PlayerConstants.PLAYBACK_MODE_SHUFFLE : PlayerConstants.PLAYBACK_MODE_ALL;
            }
            return mInheritedMode;
        }
    }

}
