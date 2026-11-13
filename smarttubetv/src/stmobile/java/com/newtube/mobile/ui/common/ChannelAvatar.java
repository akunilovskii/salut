package com.newtube.mobile.ui.common;

import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.LruCache;
import android.widget.ImageView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.liskovsoft.mediaserviceinterfaces.data.ChannelProfile;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.youtubeapi.service.ChannelProfileService;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;
import java.util.Objects;

/** Reuses public channel portraits between the watch page and channel header. */
public final class ChannelAvatar {
    private static final LruCache<String, String> URLS = new LruCache<>(64);
    private final ImageView view;
    private final RequestManager requests;
    private String channel;
    private String requestedUrl;
    private Disposable profileRequest;

    public ChannelAvatar(ImageView view) {
        this.view = view;
        requests = Glide.with(view);
    }

    public void bind(String channelId, String url) {
        if (!Objects.equals(channel, channelId)) {
            cancel();
            channel = channelId;
            requestedUrl = null;
            view.setImageResource(R.drawable.ic_watch_channel_placeholder);
            if (TextUtils.isEmpty(url) && channel != null) url = URLS.get(channel);
        }
        if (TextUtils.isEmpty(url)) {
            ChannelProfile profile = channel != null ? ChannelProfileService.cached(channel) : null;
            if (profile != null) url = profile.avatarUrl;
        }
        // Empty metadata and player-control resets must not erase a known portrait.
        if (TextUtils.isEmpty(url) || url.equals(requestedUrl)) return;
        requestedUrl = url;
        String owner = channel;
        String imageUrl = url;
        Drawable previous = view.getDrawable();
        requests.load(url).override(256, 256).circleCrop().dontAnimate()
                .placeholder(previous).error(previous)
                .listener(new RequestListener<Drawable>() {
                    @Override public boolean onLoadFailed(GlideException error, Object model,
                            Target<Drawable> target, boolean first) {
                        if (Objects.equals(owner, channel) && imageUrl.equals(requestedUrl)) requestedUrl = null;
                        return false;
                    }
                    @Override public boolean onResourceReady(Drawable image, Object model,
                            Target<Drawable> target, DataSource source, boolean first) {
                        if (owner != null && Objects.equals(owner, channel) && imageUrl.equals(requestedUrl)) {
                            URLS.put(owner, imageUrl);
                        }
                        return false;
                    }
                }).into(view);
    }

    public void loadProfileIfMissing() {
        if (TextUtils.isEmpty(channel) || !TextUtils.isEmpty(requestedUrl)
                || (profileRequest != null && !profileRequest.isDisposed())) return;
        String owner = channel;
        profileRequest = ChannelProfileService.load(owner, false)
                .observeOn(AndroidSchedulers.mainThread()).subscribe(profile -> {
                    if (owner.equals(channel)) bind(owner, profile.avatarUrl);
                }, error -> { /* Keep the existing portrait when public profile loading fails. */ });
    }

    public void cancel() {
        if (profileRequest != null) profileRequest.dispose();
        requests.clear(view);
    }
}
