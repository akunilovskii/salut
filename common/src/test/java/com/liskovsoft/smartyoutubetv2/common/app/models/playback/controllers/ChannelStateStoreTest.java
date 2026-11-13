package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import com.liskovsoft.mediaserviceinterfaces.data.ChannelStateStore;
import com.liskovsoft.mediaserviceinterfaces.data.SharedRequest;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;
import static org.junit.Assert.*;

public class ChannelStateStoreTest {
    @Test public void aFeedArrivingFirstDoesNotDiscardNotificationDataAlreadyBeingLoaded() {
        ChannelStateStore store = new ChannelStateStore();
        long started = store.revision();
        store.acceptSubscribed("A", "channel", started);
        com.liskovsoft.mediaserviceinterfaces.data.NotificationState mode = new com.liskovsoft.mediaserviceinterfaces.data.NotificationState() {
            public boolean isSelected() { return true; }
            public String getTitle() { return "All"; }
        };
        store.accept("A", "channel", true, java.util.Collections.singletonList(mode), started);
        assertEquals(1, store.get("A", "channel").notifications.size());
        assertEquals(0, store.get("A", "channel").notificationSelection);
    }
    @Test public void stateConsumersCanJoinThePlayersReadWithoutChangingItsPlaylistContext() {
        SharedRequest<String> shared = new SharedRequest<>();
        PublishSubject<String> network = PublishSubject.create();
        var player = shared.get("A\nvideo\nplaylist-context", () -> network).test();
        assertNull(shared.running("B\nvideo\n"));
        var channel = shared.running("A\nvideo\n").test();
        network.onNext("playlist metadata"); network.onComplete();
        player.assertValue("playlist metadata");
        channel.assertValue("playlist metadata");
        assertNull(shared.running("A\nvideo\n"));
    }
    @Test public void subscriptionFeedsRefreshKnownMembershipButCannotUndoANewerAction() {
        ChannelStateStore store = new ChannelStateStore();
        long oldFeed = store.revision();
        store.confirm("A", "channel", false);
        store.acceptSubscribed("A", "channel", oldFeed);
        assertFalse(store.get("A", "channel").subscribed);
        store.acceptSubscribed("A", "channel", store.revision());
        assertTrue(store.get("A", "channel").subscribed);
    }
    @Test public void providerSelectionMutationCannotChangeConfirmedNotificationState() {
        AtomicLong clock = new AtomicLong();
        ChannelStateStore store = new ChannelStateStore(clock::get);
        java.util.concurrent.atomic.AtomicBoolean selected = new java.util.concurrent.atomic.AtomicBoolean(true);
        com.liskovsoft.mediaserviceinterfaces.data.NotificationState all = new com.liskovsoft.mediaserviceinterfaces.data.NotificationState() {
            public boolean isSelected() { return selected.get(); }
            public String getTitle() { return "All"; }
        };
        store.accept("A", "channel", true, java.util.Collections.singletonList(all), store.revision());
        selected.set(false);
        assertEquals(0, store.get("A", "channel").notificationSelection);
        store.confirm("A", "channel", true);
        assertEquals(0, store.get("A", "channel").notificationSelection);
        store.confirm("A", "channel", false);
        assertNull(store.get("A", "channel").notifications);
    }
    @Test public void notificationUpdateDoesNotExtendSubscriptionFreshness() {
        AtomicLong clock = new AtomicLong();
        ChannelStateStore store = new ChannelStateStore(clock::get);
        store.accept("A", "channel", true, java.util.Collections.emptyList(), store.revision());
        clock.set(ChannelStateStore.MAX_AGE_MS);
        store.selectNotification("A", "channel", -1);
        assertNull(store.fresh("A", "channel"));
    }
    @Test public void unknownIsNotUnsubscribedAndAccountsRemainIsolated() {
        ChannelStateStore store = new ChannelStateStore();
        assertNull(store.get("A", "channel"));
        store.confirm("A", "channel", true);
        store.confirm("B", "channel", false);
        assertTrue(store.get("A", "channel").subscribed);
        assertFalse(store.get("B", "channel").subscribed);
        assertNull(store.get("C", "channel"));
    }
    @Test public void freshnessExpiresAfterFiveMinutesWithoutDiscardingVisibleState() {
        AtomicLong clock = new AtomicLong();
        ChannelStateStore store = new ChannelStateStore(clock::get);
        store.confirm("A", "channel", true);
        clock.set(ChannelStateStore.MAX_AGE_MS - 1);
        assertNotNull(store.fresh("A", "channel"));
        clock.incrementAndGet();
        assertNull(store.fresh("A", "channel"));
        assertTrue(store.get("A", "channel").subscribed);
    }
    @Test public void responseStartedBeforeAnActionCannotUndoItsConfirmedResult() {
        ChannelStateStore store = new ChannelStateStore();
        long pendingRead = store.revision();
        store.confirm("A", "channel", true);
        store.accept("A", "channel", false, null, pendingRead);
        assertTrue(store.get("A", "channel").subscribed);
        long nextRead = store.revision();
        store.accept("A", "channel", false, null, nextRead);
        assertFalse(store.get("A", "channel").subscribed);
    }
    @Test public void observersReceiveOnlyTheirOwnAccountAndChannel() {
        ChannelStateStore store = new ChannelStateStore();
        var observer = store.observe("A", "one").test();
        store.confirm("B", "one", true);
        store.confirm("A", "two", true);
        store.confirm("A", "one", false);
        observer.assertValueCount(1).assertValue(value -> !value.subscribed);
    }
    @Test public void parallelScreensShareOneReadAndDetachingDoesNotRestartIt() {
        SharedRequest<String> shared = new SharedRequest<>();
        PublishSubject<String> network = PublishSubject.create();
        AtomicInteger calls = new AtomicInteger();
        java.util.function.Supplier<Observable<String>> loader = () -> { calls.incrementAndGet(); return network; };
        var first = shared.get("account:channel", loader).test();
        first.dispose();
        var second = shared.get("account:channel", loader).test();
        assertEquals(1, calls.get());
        network.onNext("subscribed"); network.onComplete();
        second.assertValue("subscribed").assertComplete();
        shared.get("account:channel", () -> Observable.just("new")).test().assertValue("new");
    }
    @Test public void failedReadsCanBeRetriedAndDifferentAccountsDoNotShareWork() {
        SharedRequest<String> shared = new SharedRequest<>();
        shared.get("A:channel", () -> Observable.error(new IllegalStateException())).test().assertError(IllegalStateException.class);
        shared.get("A:channel", () -> Observable.just("retry")).test().assertValue("retry");
        shared.get("B:channel", () -> Observable.just("other")).test().assertValue("other");
    }
    @Test public void copyingAVideoRetainsKnownSubscriptionAndOwnership() {
        Video source = new Video(); source.isSubscribed = true; source.isSynced = true; source.subscriptionAccountKey = "A";
        Video copy = Video.from(source);
        assertTrue(copy.isSubscribed); assertTrue(copy.isSynced); assertEquals("A", copy.subscriptionAccountKey);
    }
}
