package com.liskovsoft.youtubeapi.service;

import com.liskovsoft.mediaserviceinterfaces.data.ChannelProfile;
import com.liskovsoft.mediaserviceinterfaces.data.ChannelStateStore;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/** Public profile cache, independent of subscription/account state and shared by header and About. */
public final class ChannelProfileService {
    private static final class Entry {
        final ChannelProfile profile;
        final long time = System.nanoTime() / 1_000_000L;
        Entry(ChannelProfile profile) { this.profile = profile; }
    }
    private static final Map<String, Entry> CACHE = new LinkedHashMap<>();
    private static final com.liskovsoft.mediaserviceinterfaces.data.SharedRequest<ChannelProfile> READS =
            new com.liskovsoft.mediaserviceinterfaces.data.SharedRequest<>();
    private ChannelProfileService() { }
    public static synchronized ChannelProfile cached(String channel) {
        Entry entry = CACHE.get(Locale.getDefault().toLanguageTag() + ":" + channel);
        return entry != null ? entry.profile : null;
    }

    public static synchronized Observable<ChannelProfile> load(String channel, boolean force) {
        String key = Locale.getDefault().toLanguageTag() + ":" + channel;
        Entry cached = CACHE.get(key);
        if (!force && cached != null && System.nanoTime() / 1_000_000L - cached.time < ChannelStateStore.MAX_AGE_MS)
            return Observable.just(cached.profile);
        return READS.get(key, () -> Observable.fromCallable(() -> {
                android.util.Log.d("ChannelState", "profile-read channel=" + channel);
                return YouTubeServiceManager.instance().getContentService().getChannelProfile(channel);
            })
                .subscribeOn(Schedulers.io()).doOnNext(profile -> {
                    synchronized (ChannelProfileService.class) {
                        CACHE.put(key, new Entry(profile));
                        while (CACHE.size() > 64) CACHE.remove(CACHE.keySet().iterator().next());
                    }
                }));
    }
}
