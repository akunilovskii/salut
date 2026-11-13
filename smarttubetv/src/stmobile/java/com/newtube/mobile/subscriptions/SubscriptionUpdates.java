package com.newtube.mobile.subscriptions;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** The dot acknowledges a successful refresh following a tab tap, never a background check. */
public final class SubscriptionUpdates {
    private Set<String> mSeen;
    private boolean mHasNewVideos;
    private boolean mAwaitingTapRefresh;

    public SubscriptionUpdates(Set<String> seen, boolean hasNewVideos) {
        mSeen = seen == null ? null : new HashSet<>(seen);
        mHasNewVideos = hasNewVideos;
    }

    public void observe(Collection<String> ids) {
        for (String id : ids) {
            if (id != null && (mSeen == null || !mSeen.contains(id))) mHasNewVideos = true;
        }
    }

    public void tapped() { mAwaitingTapRefresh = true; }
    public void cancelTap() { mAwaitingTapRefresh = false; }
    public boolean isAwaitingRefresh() { return mAwaitingTapRefresh; }
    public boolean hasNewVideos() { return mHasNewVideos; }
    public Set<String> seen() { return mSeen == null ? null : new HashSet<>(mSeen); }

    public void refreshed(Collection<String> ids) {
        if (mAwaitingTapRefresh) {
            mSeen = new HashSet<>(ids);
            mSeen.remove(null);
            mHasNewVideos = false;
            mAwaitingTapRefresh = false;
        } else {
            observe(ids);
        }
    }
}
