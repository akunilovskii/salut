package com.newtube.mobile.notifications;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.tv.R;
import java.util.concurrent.TimeUnit;

/** Android delivery is independent of the foreground Subscriptions acknowledgment dot. */
public final class ChannelVideoNotifications {
    static final String CHANNEL = "channel_videos";
    private static final Object DELIVERY_LOCK = new Object();
    private static final String PERIODIC = "channel-video-notifications";
    private static final String IMMEDIATE = "channel-video-notifications-now";
    // MediaServiceManager keeps account listeners weakly.
    private static MediaServiceManager.AccountChangeListener accountListener;
    private ChannelVideoNotifications() { }

    public static void install(Context context) {
        Context app = context.getApplicationContext();
        createChannel(app);
        Constraints network = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        PeriodicWorkRequest periodic = new PeriodicWorkRequest.Builder(ChannelNotificationWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(network).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build();
        WorkManager.getInstance(app).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, periodic);
        accountListener = account -> {
            synchronized (DELIVERY_LOCK) {
                NotificationManager manager = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
                for (android.service.notification.StatusBarNotification item : manager.getActiveNotifications()) {
                    if (item.getTag() != null && item.getTag().startsWith(CHANNEL + ":")) manager.cancel(item.getTag(), item.getId());
                }
            }
            checkNow(app);
        };
        MediaServiceManager.instance().addAccountListener(accountListener);
        androidx.lifecycle.ProcessLifecycleOwner.get().getLifecycle().addObserver(new androidx.lifecycle.DefaultLifecycleObserver() {
            @Override public void onStart(androidx.lifecycle.LifecycleOwner owner) { checkNow(app); }
        });
    }

    public static void checkNow(Context context) {
        WorkManager.getInstance(context).enqueueUniqueWork(IMMEDIATE, ExistingWorkPolicy.REPLACE,
                new OneTimeWorkRequest.Builder(ChannelNotificationWorker.class)
                        .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build());
    }

    static boolean allowed(Context context) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE)).getNotificationChannel(CHANNEL);
            return channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
        }
        return true;
    }

    static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, context.getString(R.string.mobile_channel_video_notifications),
                    NotificationManager.IMPORTANCE_DEFAULT);
            ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
    }

    static void post(Context context, String account, String videoId, String title, CharSequence detail) {
        createChannel(context);
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=" + videoId))
                .setClassName(context, "com.liskovsoft.smartyoutubetv2.tv.ui.main.IntentRouterActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent open = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_nav_subscriptions_outline).setContentTitle(title).setContentText(detail)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(detail)).setContentIntent(open)
                .setAutoCancel(true).setOnlyAlertOnce(true).setCategory(NotificationCompat.CATEGORY_RECOMMENDATION);
        synchronized (DELIVERY_LOCK) {
            if (!account.equals(com.liskovsoft.youtubeapi.service.ChannelStateService.accountKey()) || !allowed(context)) return;
            NotificationManagerCompat.from(context).notify(CHANNEL + ":" + account + ":" + videoId, 0, builder.build());
        }
    }
}
