package com.newtube.mobile.subscriptions;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class SubscriptionUpdatesTest {
    @Test public void newVideoShowsTheDot() {
        SubscriptionUpdates updates = seen("old");
        updates.observe(Arrays.asList("new", "old"));
        assertTrue(updates.hasNewVideos());
    }

    @Test public void alreadySeenVideosAndReorderingDoNotShowTheDot() {
        SubscriptionUpdates updates = seen("a", "b");
        updates.observe(Arrays.asList("b", "a"));
        assertFalse(updates.hasNewVideos());
    }

    @Test public void backgroundChecksCannotClearAnExistingDot() {
        SubscriptionUpdates updates = seen("old");
        updates.observe(Collections.singleton("new"));
        updates.observe(Collections.singleton("old"));
        updates.observe(Collections.emptyList());
        assertTrue(updates.hasNewVideos());
    }

    @Test public void tappingAloneDoesNotClearTheDot() {
        SubscriptionUpdates updates = new SubscriptionUpdates(set("old"), true);
        updates.tapped();
        assertTrue(updates.hasNewVideos());
        assertTrue(updates.isAwaitingRefresh());
    }

    @Test public void aSuccessfulRefreshFollowingATapClearsAndAcknowledges() {
        SubscriptionUpdates updates = new SubscriptionUpdates(set("old"), true);
        updates.tapped();
        updates.refreshed(Arrays.asList("new", "old"));
        assertFalse(updates.hasNewVideos());
        assertFalse(updates.isAwaitingRefresh());
        updates.observe(Arrays.asList("new", "old"));
        assertFalse(updates.hasNewVideos());
    }

    @Test public void anAutomaticVisibleFeedRefreshDoesNotAcknowledge() {
        SubscriptionUpdates updates = new SubscriptionUpdates(set("old"), true);
        updates.refreshed(Arrays.asList("new", "old"));
        assertTrue(updates.hasNewVideos());
        assertEquals(set("old"), updates.seen());
    }

    @Test public void failedOrAbandonedTapCannotClearOnALaterAutomaticRefresh() {
        SubscriptionUpdates updates = new SubscriptionUpdates(set("old"), true);
        updates.tapped();
        updates.cancelTap();
        updates.refreshed(Arrays.asList("new", "old"));
        assertTrue(updates.hasNewVideos());
    }

    @Test public void backgroundResultDuringATapStillCannotAcknowledge() {
        SubscriptionUpdates updates = new SubscriptionUpdates(set("old"), true);
        updates.tapped();
        updates.observe(Arrays.asList("new", "old"));
        assertTrue(updates.hasNewVideos());
        assertTrue(updates.isAwaitingRefresh());
    }

    @Test public void successfulEmptyFeedAfterATapClears() {
        SubscriptionUpdates updates = new SubscriptionUpdates(set("old"), true);
        updates.tapped();
        updates.refreshed(Collections.emptyList());
        assertFalse(updates.hasNewVideos());
        assertEquals(Collections.emptySet(), updates.seen());
    }

    @Test public void firstUseShowsUnacknowledgedContent() {
        SubscriptionUpdates updates = new SubscriptionUpdates(null, false);
        updates.observe(Collections.singleton("new"));
        assertTrue(updates.hasNewVideos());
    }

    @Test public void snapshotsAreIsolatedFromCallers() {
        Set<String> original = set("old");
        SubscriptionUpdates updates = new SubscriptionUpdates(original, false);
        original.add("new");
        updates.seen().add("new");
        updates.observe(Collections.singleton("new"));
        assertTrue(updates.hasNewVideos());
    }

    private static SubscriptionUpdates seen(String... ids) { return new SubscriptionUpdates(set(ids), false); }
    private static Set<String> set(String... ids) { return new HashSet<>(Arrays.asList(ids)); }
}
