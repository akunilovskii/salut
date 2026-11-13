package com.newtube.mobile.notifications;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.youtubeapi.service.ChannelStateService;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;
import com.liskovsoft.youtubeapi.service.YouTubeSignInService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Polls the actual YouTube inbox; personalized selection stays YouTube's responsibility. */
public final class ChannelNotificationWorker extends Worker {
    private static final Object SYNC = new Object();
    public ChannelNotificationWorker(@NonNull Context context, @NonNull WorkerParameters parameters) { super(context, parameters); }

    @NonNull @Override public Result doWork() {
        synchronized (SYNC) {
            Context context = getApplicationContext();
            if (!ChannelVideoNotifications.allowed(context)) return Result.success();
            com.liskovsoft.youtubeapi.notifications.PhoneNotificationSupport.enable();
            com.liskovsoft.mediaserviceinterfaces.oauth.Account selected = YouTubeSignInService.instance().getSelectedAccount();
            if (selected == null || selected.isEmpty()) return Result.success();
            String account = ChannelStateService.accountKey();
            if (!com.liskovsoft.youtubeapi.notifications.PhoneNotificationSupport.isWebAuthorizationAvailable(account)) {
                Log.d("ChannelNotifications", "web authorization required; no unsupported request sent");
                return Result.success();
            }
            NotificationSeenStore store = new NotificationSeenStore(context.getSharedPreferences("channel_notification_seen", Context.MODE_PRIVATE));
            if (store.recentlyChecked(account, System.currentTimeMillis())) return Result.success();
            try {
                MediaGroup group = YouTubeServiceManager.instance().getNotificationsService().getNotificationItems();
                if (group == null) throw new IllegalStateException("Inbox unavailable");
                if (isStopped() || !account.equals(ChannelStateService.accountKey())) return Result.success();
                Map<String, MediaItem> videos = new LinkedHashMap<>();
                if (group.getMediaItems() != null) for (MediaItem item : group.getMediaItems()) {
                    if (item != null && item.getVideoId() != null && item.getVideoId().matches("[A-Za-z0-9_-]{11}")) videos.putIfAbsent(item.getVideoId(), item);
                }
                List<String> ids = new ArrayList<>(videos.keySet());
                List<String> fresh = store.unseen(account, ids);
                for (String id : fresh) {
                    if (isStopped() || !account.equals(ChannelStateService.accountKey())) return Result.success();
                    MediaItem item = videos.get(id);
                    ChannelVideoNotifications.post(context, account, id, item.getTitle(), item.getSecondTitle());
                }
                if (!isStopped() && account.equals(ChannelStateService.accountKey())) store.accept(account, ids);
                Log.d("ChannelNotifications", "inbox checked items=" + ids.size() + " delivered=" + fresh.size());
                return Result.success();
            } catch (Exception error) {
                Log.w("ChannelNotifications", "inbox check failed: " + error.getClass().getSimpleName());
                return getRunAttemptCount() < 3 ? Result.retry() : Result.failure();
            }
        }
    }
}
