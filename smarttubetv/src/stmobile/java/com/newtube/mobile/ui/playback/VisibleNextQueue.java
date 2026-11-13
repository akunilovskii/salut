package com.newtube.mobile.ui.playback;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;

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

    boolean isLocked() {
        return mLocked;
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
        mExpectedId = video != null ? video.videoId : null;
    }

    boolean consume(Video video) {
        if (!mLocked || video == null || mExpectedId == null
                || !mExpectedId.equals(video.videoId)) {
            reset();
            return false;
        }
        int selected = indexOf(video.videoId);
        if (video.fromQueue) {
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
