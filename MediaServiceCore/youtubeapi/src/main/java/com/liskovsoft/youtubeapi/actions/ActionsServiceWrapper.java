package com.liskovsoft.youtubeapi.actions;

import com.liskovsoft.youtubeapi.channelgroups.ChannelGroupServiceImpl;
import com.liskovsoft.youtubeapi.notifications.NotificationStorage;

public class ActionsServiceWrapper extends ActionsService {
    private static ActionsServiceWrapper sInstance;

    public static ActionsServiceWrapper instance() {
        if (sInstance == null) {
            sInstance = new ActionsServiceWrapper();
        }

        return sInstance;
    }

    @Override
    public void subscribe(String channelId, String params) {
        String owner = com.liskovsoft.youtubeapi.service.ChannelStateService.accountKey();
        super.subscribe(channelId, params);

        if (owner.equals(com.liskovsoft.youtubeapi.service.ChannelStateService.accountKey())) {
            com.liskovsoft.youtubeapi.service.ChannelStateService.store().confirm(owner, channelId, true);
            com.liskovsoft.mediaserviceinterfaces.data.ChannelProfile profile =
                    com.liskovsoft.youtubeapi.service.ChannelProfileService.cached(channelId);
            try {
                ChannelGroupServiceImpl.subscribe(true, channelId, profile != null ? profile.title : null,
                        profile != null ? profile.avatarUrl : null);
            } catch (RuntimeException error) {
                android.util.Log.w("ChannelState", "Local channel details unavailable after confirmed subscription");
            }
        }
    }

    @Override
    public void unsubscribe(String channelId) {
        String owner = com.liskovsoft.youtubeapi.service.ChannelStateService.accountKey();
        super.unsubscribe(channelId);

        if (owner.equals(com.liskovsoft.youtubeapi.service.ChannelStateService.accountKey())) {
            com.liskovsoft.youtubeapi.service.ChannelStateService.store().confirm(owner, channelId, false);
            ChannelGroupServiceImpl.subscribe(false, channelId, null, null); // save locally
        }
    }

    @Override
    public void setLike(String videoId) {
        super.setLike(videoId);

        NotificationStorage.setLike(true);
    }

    @Override
    public void removeLike(String videoId) {
        super.removeLike(videoId);

        NotificationStorage.setLike(false);
    }

    @Override
    public void setDislike(String videoId) {
        super.setDislike(videoId);

        NotificationStorage.setLike(false);
    }

    @Override
    public void removeDislike(String videoId) {
        super.removeDislike(videoId);

        NotificationStorage.setLike(true);
    }
}
