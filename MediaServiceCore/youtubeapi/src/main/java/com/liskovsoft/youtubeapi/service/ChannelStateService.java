package com.liskovsoft.youtubeapi.service;

import com.liskovsoft.mediaserviceinterfaces.data.ChannelStateStore;
import com.liskovsoft.mediaserviceinterfaces.data.ChannelStateStore.Snapshot;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.googlecommon.service.oauth.YouTubeAccount;
import java.util.HashMap;
import java.util.Map;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/** Shared runtime state and single-flight reads/writes over the existing YouTube providers. */
public final class ChannelStateService {
    private static final ChannelStateStore STORE = new ChannelStateStore();
    private static final com.liskovsoft.mediaserviceinterfaces.data.SharedRequest<Snapshot> READS =
            new com.liskovsoft.mediaserviceinterfaces.data.SharedRequest<>();
    private static final class Write {
        final boolean desired;
        final Observable<Void> request;
        Write(boolean desired, Observable<Void> request) { this.desired = desired; this.request = request; }
    }
    private static final Map<String, Write> WRITES = new HashMap<>();
    private static final Map<String, NotificationWrite> NOTIFICATION_WRITES = new HashMap<>();
    private static final class NotificationWrite {
        final int desired;
        final Observable<Void> request;
        NotificationWrite(int desired, Observable<Void> request) { this.desired = desired; this.request = request; }
    }
    private ChannelStateService() { }

    public static String accountKey() {
        Account account = YouTubeSignInService.instance().getSelectedAccount();
        if (account == null || account.isEmpty()) return "anonymous";
        String page = account instanceof YouTubeAccount ? ((YouTubeAccount) account).getPageIdToken() : null;
        return account.getId() + ":" + account.getEmail() + ":" + page;
    }
    public static ChannelStateStore store() { return STORE; }
    public static Snapshot current(String channel) { return STORE.get(accountKey(), channel); }
    public static Observable<Snapshot> observe(String channel) { return STORE.observe(accountKey(), channel); }
    public static void metadata(String owner, MediaItemMetadata metadata, long revision) {
        if (metadata != null && metadata.hasSubscriptionState() && metadata.getChannelId() != null && owner.equals(accountKey())) {
            STORE.accept(owner, metadata.getChannelId(), metadata.isSubscribed(), metadata.getNotificationStates(), revision);
        }
    }
    public static void subscriptions(String owner, MediaGroup group, long revision) {
        if (!owner.equals(accountKey()) || group == null || group.getMediaItems() == null) return;
        for (MediaItem item : group.getMediaItems()) if (item.getChannelId() != null) {
            STORE.acceptSubscribed(owner, item.getChannelId(), revision);
        }
    }
    public static void refreshKnown(String channel, String videoId) {
        if (channel == null || videoId == null || current(channel) == null || STORE.fresh(accountKey(), channel) != null) return;
        load(channel, videoId, false).subscribe(ignored -> { }, error ->
                android.util.Log.d("ChannelState", "Background status refresh unavailable"));
    }
    public static Observable<Snapshot> load(String channel, String videoId, boolean force) {
        String owner = accountKey();
        Snapshot fresh = STORE.fresh(owner, channel);
        if (!force && fresh != null) return Observable.just(fresh);
        if (videoId == null) return Observable.empty();
        String key = owner + "\n" + channel;
        return READS.get(key, () -> Observable.defer(() -> {
                if (!owner.equals(accountKey())) return Observable.error(new IllegalStateException("Account changed"));
                android.util.Log.d("ChannelState", "subscription-read channel=" + channel);
                return YouTubeMediaItemService.instance().getChannelMetadataObserve(videoId).map(metadata -> {
                    if (!channel.equals(metadata.getChannelId())) throw new IllegalStateException("Channel mismatch");
                    Snapshot value = STORE.get(owner, channel);
                    if (value == null) throw new IllegalStateException("Channel status unavailable");
                    return value;
                });
            }).subscribeOn(Schedulers.io()));
    }
    public static Observable<Void> mutation(String channel, boolean desired, Runnable operation) {
        String owner = accountKey(), key = owner + "\n" + channel;
        return Observable.defer(() -> {
            synchronized (WRITES) {
                if (NOTIFICATION_WRITES.containsKey(key)) return Observable.error(new IllegalStateException("Notification update already pending"));
                Write running = WRITES.get(key);
                if (running != null) return running.desired == desired ? running.request
                        : Observable.error(new IllegalStateException("Subscription update already pending"));
                Observable<Void> request = Observable.<Void>fromRunnable(() -> {
                    if (!owner.equals(accountKey())) throw new IllegalStateException("Account changed");
                    operation.run();
                }).subscribeOn(Schedulers.io()).doFinally(() -> { synchronized (WRITES) { WRITES.remove(key); } }).cache();
                WRITES.put(key, new Write(desired, request));
                return request;
            }
        });
    }
    public static Observable<Void> notificationMutation(String channel, int desired, Runnable operation) {
        String owner = accountKey(), key = owner + "\n" + channel;
        return Observable.defer(() -> {
            synchronized (WRITES) {
                if (WRITES.containsKey(key)) return Observable.error(new IllegalStateException("Subscription update already pending"));
                NotificationWrite running = NOTIFICATION_WRITES.get(key);
                if (running != null) return running.desired == desired ? running.request
                        : Observable.error(new IllegalStateException("Notification update already pending"));
                Observable<Void> request = Observable.<Void>fromRunnable(() -> {
                    if (!owner.equals(accountKey())) throw new IllegalStateException("Account changed");
                    operation.run();
                    STORE.selectNotification(owner, channel, desired);
                }).subscribeOn(Schedulers.io()).doFinally(() -> {
                    synchronized (WRITES) { NOTIFICATION_WRITES.remove(key); }
                }).cache();
                NOTIFICATION_WRITES.put(key, new NotificationWrite(desired, request));
                return request;
            }
        });
    }

}
