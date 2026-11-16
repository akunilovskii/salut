package com.newtube.mobile.ui.playback;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.QueuePlaybackMode;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerConstants;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Keeps the watch page's visible following videos in their original order across playback. */
final class VisibleNextQueue {
    private final List<Video> mRemaining = new ArrayList<>();
    private final Set<String> mConsumedIds = new HashSet<>();
    private final Set<Integer> mSourceGroups = new HashSet<>();
    private boolean mLocked;
    private String mExpectedId;
    private final List<Video> mPlaylist = new ArrayList<>();
    private VideoGroup mPlaylistGroup;
    private boolean mPlaylistMode;
    private boolean mSourceSelection;
    private Video mShuffleNext;
    private final QueuePlaybackMode.Session mPlaybackMode = new QueuePlaybackMode.Session();
    private int mCurrentSourceIndex = -1;
    private boolean mExpectedFromQueue;
    private boolean mQueueInterruption;
    private boolean mExpectedRestart;
    private String mWrapTargetId;

    QueuePlaybackMode.Session playbackMode() { return mPlaybackMode; }
    int currentSourceIndex() { return mCurrentSourceIndex; }
    boolean isQueueInterruption() { return mQueueInterruption; }
    void invalidateNext() { mShuffleNext = null; mWrapTargetId = null; }

    boolean isLocked() {
        return mLocked;
    }

    boolean hasPlaylist() { return mPlaylistMode; }

    VideoGroup sourceGroup() { return mPlaylistGroup; }

    List<Video> displayedVideos() {
        return new ArrayList<>(mPlaylistMode ? mPlaylist : mRemaining);
    }

    int sourceIndex(Video video) {
        if (video == null) return -1;
        for (int index = 0; index < mPlaylist.size(); index++) {
            if (mPlaylist.get(index).videoId.equals(video.videoId)) return index;
        }
        return -1;
    }

    boolean startPlaylist(List<Video> videos, Video current) {
        reset();
        if (videos == null || current == null) return false;
        for (Video video : videos) {
            if (video != null && video.hasVideo()
                    && !com.newtube.mobile.ui.common.ShortsFilter.isShort(video)) mPlaylist.add(video);
        }
        int selected = sourceIndex(current);
        if (selected < 0) { reset(); return false; }
        mPlaylistGroup = mPlaylist.get(selected).getGroup();
        if (mPlaylistGroup != null) mSourceGroups.add(mPlaylistGroup.getId());
        mPlaylistMode = true;
        mCurrentSourceIndex = selected;
        mLocked = true;
        mConsumedIds.add(current.videoId);
        mRemaining.addAll(mPlaylist.subList(selected + 1, mPlaylist.size()));
        return true;
    }

    void expectSource(Video video) {
        expect(video);
        mSourceSelection = true;
    }

    Video peek(List<Video> visible, boolean shuffle) {
        return peek(visible, shuffle, false);
    }

    Video peek(List<Video> visible, boolean shuffle, boolean repeatAll) {
        if (!mPlaylistMode) return peek(visible);
        if (shuffle && mShuffleNext != null) return mShuffleNext;
        List<Video> candidates = new ArrayList<>();
        if (shuffle) {
            for (Video video : mPlaylist) {
                if (!video.isUpcoming && !mConsumedIds.contains(video.videoId)) candidates.add(video);
            }
            if (!candidates.isEmpty()) return cacheRandom(candidates);
        } else {
            Video following = peek(visible);
            if (following != null) return following;
        }
        // Finish loading the actual source before wrapping a partial page back to its start.
        String pageKey = mPlaylistGroup != null ? mPlaylistGroup.getNextPageKey() : null;
        if (!repeatAll || (pageKey != null && !pageKey.isEmpty())) return null;
        for (Video video : mPlaylist) if (!video.isUpcoming) candidates.add(video);
        if (candidates.isEmpty()) return null;
        Video next;
        if (shuffle) {
            if (candidates.size() > 1 && mCurrentSourceIndex >= 0) {
                String currentId = mPlaylist.get(mCurrentSourceIndex).videoId;
                candidates.removeIf(video -> currentId.equals(video.videoId));
            }
            next = cacheRandom(candidates);
        } else next = candidates.get(0);
        mWrapTargetId = next.videoId;
        return next;
    }

    private Video cacheRandom(List<Video> candidates) {
        mShuffleNext = candidates.get(new java.util.Random().nextInt(candidates.size()));
        return mShuffleNext;
    }

    Video peek(List<Video> visible) {
        for (Video video : mLocked ? mRemaining : visible) {
            if (video != null && video.hasVideo() && !video.isUpcoming) {
                return video;
            }
        }
        return null;
    }

    void lock(List<Video> visible) {
        if (mLocked || peek(visible) == null) {
            return;
        }
        mRemaining.addAll(visible);
        for (Video video : visible) {
            VideoGroup group = video != null ? video.getGroup() : null;
            if (group != null) {
                mSourceGroups.add(group.getId());
            }
        }
        mLocked = true;
    }

    boolean startChannel(List<Video> visible, Video current) {
        reset();
        if (current == null) return false;
        int selected = -1;
        for (int index = 0; index < visible.size(); index++) {
            if (current.videoId != null && current.videoId.equals(visible.get(index).videoId)) {
                selected = index;
                break;
            }
        }
        if (selected < 0) return false;
        for (int index = 0; index < visible.size(); index++) {
            Video video = visible.get(index);
            VideoGroup group = video.getGroup();
            if (group != null) mSourceGroups.add(group.getId());
            if (index <= selected) mConsumedIds.add(video.videoId);
            else mRemaining.add(video);
        }
        // Exhausted channel tabs must not fall through to unrelated recommendations.
        mLocked = true;
        return true;
    }

    void expect(Video video) {
        expect(video, video != null && video.fromQueue);
    }

    void expect(Video video, boolean queued) {
        mExpectedId = video != null ? video.videoId : null;
        mExpectedFromQueue = queued;
        mExpectedRestart = !queued && mExpectedId != null && mExpectedId.equals(mWrapTargetId);
        mSourceSelection = false;
    }

    boolean consume(Video video) {
        if (!mLocked || video == null || mExpectedId == null
                || !mExpectedId.equals(video.videoId)) {
            reset();
            return false;
        }
        boolean queued = mExpectedFromQueue || video.fromQueue;
        mShuffleNext = null;
        mWrapTargetId = null;
        if (mPlaylistMode && (mSourceSelection || !queued)) {
            int selected = sourceIndex(video);
            if (selected < 0) { reset(); return false; }
            if (mSourceSelection || mExpectedRestart) mConsumedIds.clear();
            mCurrentSourceIndex = selected;
            mQueueInterruption = false;
            mConsumedIds.add(video.videoId);
            mRemaining.clear();
            for (int index = selected + 1; index < mPlaylist.size(); index++) {
                Video following = mPlaylist.get(index);
                if (!mConsumedIds.contains(following.videoId)) mRemaining.add(following);
            }
            mExpectedId = null;
            mSourceSelection = false;
            return true;
        }
        int selected = indexOf(video.videoId);
        if (queued) {
            mQueueInterruption = true;
            // An explicit queue item interrupts the source order without consuming earlier rows.
            if (selected >= 0) {
                mRemaining.remove(selected);
            }
            mConsumedIds.add(video.videoId);
            mExpectedId = null;
            return true;
        }
        if (selected < 0) {
            reset();
            return false;
        }
        for (int index = 0; index <= selected; index++) {
            Video consumed = mRemaining.remove(0);
            if (consumed.videoId != null) {
                mConsumedIds.add(consumed.videoId);
            }
        }
        mExpectedId = null;
        return true;
    }

    boolean append(VideoGroup group) {
        if (!mLocked || group == null || !mSourceGroups.contains(group.getId())
                || group.getAction() != VideoGroup.ACTION_APPEND) {
            return false;
        }
        boolean changed = false;
        if (mPlaylistMode) {
            mPlaylistGroup = group;
            for (Video video : group.getVideos()) {
                if (video != null && video.hasVideo()
                        && !com.newtube.mobile.ui.common.ShortsFilter.isShort(video)
                        && sourceIndex(video) < 0) {
                    mPlaylist.add(video);
                    if (!mConsumedIds.contains(video.videoId)) mRemaining.add(video);
                    changed = true;
                }
            }
            return changed;
        }
        for (Video video : group.getVideos()) {
            if (video != null && video.videoId != null && !mConsumedIds.contains(video.videoId)
                    && !contains(video.videoId) && !com.newtube.mobile.ui.common.ShortsFilter.isShort(video)) {
                mRemaining.add(video);
                changed = true;
            }
        }
        return changed;
    }

    List<Video> remaining() {
        return new ArrayList<>(mRemaining);
    }

    void reset() {
        mRemaining.clear();
        mConsumedIds.clear();
        mSourceGroups.clear();
        mExpectedId = null;
        mLocked = false;
        mPlaylist.clear();
        mPlaylistGroup = null;
        mPlaylistMode = false;
        mSourceSelection = false;
        mShuffleNext = null;
        mWrapTargetId = null;
        mExpectedFromQueue = false;
        mQueueInterruption = false;
        mExpectedRestart = false;
        mCurrentSourceIndex = -1;
        mPlaybackMode.start(PlayerConstants.PLAYBACK_MODE_ALL);
    }

    private int indexOf(String videoId) {
        for (int index = 0; index < mRemaining.size(); index++) {
            if (videoId.equals(mRemaining.get(index).videoId)) {
                return index;
            }
        }
        return -1;
    }

    private boolean contains(String videoId) {
        return indexOf(videoId) >= 0;
    }
}
