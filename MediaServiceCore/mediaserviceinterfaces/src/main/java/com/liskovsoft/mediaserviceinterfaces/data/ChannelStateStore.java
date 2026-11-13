package com.liskovsoft.mediaserviceinterfaces.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;

/** Account-owned, confirmed channel state. Missing entries mean unknown, never unsubscribed. */
public final class ChannelStateStore {
    public static final long MAX_AGE_MS = 5 * 60_000L;
    public static final class Snapshot {
        public final String account, channel;
        public final boolean subscribed;
        public final List<NotificationState> notifications;
        public final int notificationSelection;
        public final long revision, actionRevision, updatedAt;
        private Snapshot(String account, String channel, boolean subscribed, List<NotificationState> notifications,
                int notificationSelection, long revision, long actionRevision, long updatedAt) {
            this.account = account; this.channel = channel; this.subscribed = subscribed;
            this.notifications = notifications == null ? null : Collections.unmodifiableList(new ArrayList<>(notifications));
            this.notificationSelection = notificationSelection;
            this.revision = revision; this.actionRevision = actionRevision; this.updatedAt = updatedAt;
        }
    }

    private final Map<String, Snapshot> entries = new LinkedHashMap<>();
    private final Subject<Snapshot> changes = PublishSubject.<Snapshot>create().toSerialized();
    private final LongSupplier clock;
    private long revision;

    public ChannelStateStore() { this(() -> System.nanoTime() / 1_000_000L); }
    public ChannelStateStore(LongSupplier clock) { this.clock = clock; }
    private static String key(String account, String channel) { return account + "\n" + channel; }
    public synchronized long revision() { return revision; }
    public synchronized Snapshot get(String account, String channel) { return entries.get(key(account, channel)); }
    public synchronized Snapshot fresh(String account, String channel) {
        Snapshot value = get(account, channel);
        return value != null && clock.getAsLong() - value.updatedAt < MAX_AGE_MS ? value : null;
    }
    public Observable<Snapshot> observe(String account, String channel) {
        return changes.filter(value -> value.account.equals(account) && value.channel.equals(channel));
    }
    public synchronized void accept(String account, String channel, boolean subscribed,
            List<NotificationState> notifications, long startedAtRevision) {
        Snapshot previous = get(account, channel);
        if (previous != null && previous.actionRevision > startedAtRevision) return;
        put(account, channel, subscribed, notifications, previous != null ? previous.actionRevision : 0);
    }
    public synchronized void acceptSubscribed(String account, String channel, long startedAtRevision) {
        Snapshot previous = get(account, channel);
        if (previous != null && previous.actionRevision > startedAtRevision) return;
        if (previous != null && previous.subscribed) {
            Snapshot value = new Snapshot(account, channel, true, previous.notifications, previous.notificationSelection,
                    ++revision, previous.actionRevision, clock.getAsLong());
            entries.put(key(account, channel), value);
            changes.onNext(value);
        } else put(account, channel, true, null, previous != null ? previous.actionRevision : 0);
    }
    public synchronized void confirm(String account, String channel, boolean subscribed) {
        Snapshot previous = get(account, channel);
        if (previous != null && previous.subscribed == subscribed) {
            Snapshot value = new Snapshot(account, channel, subscribed, previous.notifications,
                    previous.notificationSelection, ++revision, revision, clock.getAsLong());
            entries.put(key(account, channel), value);
            changes.onNext(value);
            return;
        }
        put(account, channel, subscribed, null, revision + 1);
    }
    public synchronized void selectNotification(String account, String channel, int selected) {
        Snapshot previous = get(account, channel);
        if (previous == null || !previous.subscribed || previous.notifications == null) return;
        Snapshot value = new Snapshot(account, channel, true, previous.notifications, selected, ++revision, revision, previous.updatedAt);
        entries.put(key(account, channel), value);
        changes.onNext(value);
    }
    private void put(String account, String channel, boolean subscribed, List<NotificationState> notifications, long actionRevision) {
        if (channel == null) return;
        int selected = -1;
        if (notifications != null) for (int i = 0; i < notifications.size(); i++) if (notifications.get(i).isSelected()) { selected = i; break; }
        Snapshot value = new Snapshot(account, channel, subscribed, notifications, selected, ++revision, actionRevision, clock.getAsLong());
        entries.put(key(account, channel), value);
        while (entries.size() > 256) entries.remove(entries.keySet().iterator().next());
        changes.onNext(value);
    }
}
