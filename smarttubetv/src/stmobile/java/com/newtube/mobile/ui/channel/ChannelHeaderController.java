package com.newtube.mobile.ui.channel;

import android.app.Activity;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.liskovsoft.mediaserviceinterfaces.data.ChannelProfile;
import com.liskovsoft.mediaserviceinterfaces.data.ChannelStateStore.Snapshot;
import com.liskovsoft.youtubeapi.service.ChannelStateService;
import com.liskovsoft.youtubeapi.service.ChannelProfileService;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AccountSelectionPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.VideoMenuPresenter;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;
import com.liskovsoft.youtubeapi.service.YouTubeSignInService;
import com.newtube.mobile.ui.common.MobileSnackbar;
import com.newtube.mobile.ui.playback.WatchSubscriptionControl;

import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/** Public channel details and account-scoped subscription actions, without duplicating providers. */
final class ChannelHeaderController {
    private final Activity activity;
    private final View root;
    private final WatchSubscriptionControl subscription;
    private final MaterialButton subscribe;
    private final com.newtube.mobile.ui.common.ChannelAvatar avatar;
    private Video channel;
    private Video reference;
    private String account;
    private ChannelProfile profile;
    private boolean subscribed, statusKnown;
    private ChannelDetailsPage details;
    private long generation;
    private Disposable profileRequest, statusRequest, writeRequest;

    ChannelHeaderController(Activity activity, View root) {
        this.activity = activity;
        this.root = root;
        avatar = new com.newtube.mobile.ui.common.ChannelAvatar(root.findViewById(R.id.channel_header_avatar));
        account = currentAccount();
        subscribe = root.findViewById(R.id.mobile_watch_subscribe);
        subscription = new WatchSubscriptionControl(activity, root,
                () -> new WatchSubscriptionControl.Scope(channel != null ? channel.channelId : null, currentAccount()),
                state -> YouTubeServiceManager.instance().getNotificationsService().setNotificationStateObserve(state),
                () -> changeSubscription(false));
        subscribe.setOnClickListener(view -> changeSubscription(true));
        root.findViewById(R.id.channel_header_more).setOnClickListener(view -> {
            if (channel != null) VideoMenuPresenter.instance(activity).showMenu(channel);
        });
        root.findViewById(R.id.channel_header_description).setOnClickListener(view -> showAbout());
        root.findViewById(R.id.channel_header_links).setOnClickListener(view -> showLinks());
        subscription.unknown();
        subscribe.setEnabled(false);
    }

    void bindChannel(Video video, String channelId, CharSequence fallbackTitle) {
        reference = video != null && video.hasVideo() ? video : null;
        channel = video != null ? Video.from(video) : new Video();
        if (channel.channelId == null) channel.channelId = channelId;
        if (channel != null) {
            channel.videoId = channel.playlistId = channel.playlistParams = null;
            channel.setGroup(null);
        }
        channel.title = fallbackTitle != null ? fallbackTitle.toString() : channel.author;
        ((TextView) root.findViewById(R.id.channel_header_name)).setText(fallbackTitle);
        avatar.bind(channel.channelId, null);
        loadProfile(false);
        loadStatus(false);
    }

    private void loadProfile(boolean force) {
        if (channel == null || channel.channelId == null) return;
        if (profileRequest != null) profileRequest.dispose();
        String id = channel.channelId;
        profileRequest = ChannelProfileService.load(id, force).observeOn(AndroidSchedulers.mainThread()).subscribe(value -> {
                    if (activity.isDestroyed() || channel == null || !id.equals(channel.channelId)) return;
                    profile = value;
                    ((TextView) root.findViewById(R.id.channel_header_name)).setText(value.title);
                    ((TextView) activity.findViewById(R.id.mobile_channel_title)).setText(value.title);
                    channel.title = channel.author = value.title;
                    setText(R.id.channel_header_handle, value.handle);
                    setText(R.id.channel_header_counts, join(value.subscribers, value.videos));
                    setText(R.id.channel_header_description, value.description);
                    root.findViewById(R.id.channel_header_verified).setVisibility(value.verified ? View.VISIBLE : View.GONE);
                    TextView links = root.findViewById(R.id.channel_header_links);
                    links.setText(linkSummary(activity, value.links));
                    links.setVisibility(value.links.isEmpty() ? View.GONE : View.VISIBLE);
                    avatar.bind(id, value.avatarUrl);
                    image(R.id.channel_header_banner, value.bannerUrl, false);
                }, error -> {
                    if (!activity.isDestroyed() && profile == null) setText(R.id.channel_header_description,
                            activity.getString(R.string.mobile_channel_profile_error));
                });
    }

    void bindReference(List<Video> videos) {
        if (reference != null || videos == null) return;
        for (Video video : videos) {
            if (video == null || !video.hasVideo() || video.isUpcoming) continue;
            if (channel.channelId != null && video.channelId != null && !channel.channelId.equals(video.channelId)) continue;
            reference = video;
            loadStatus(false);
            break;
        }
    }

    void onResume() {
        if (!account.equals(currentAccount())) {
            account = currentAccount();
            statusKnown = false;
            subscription.reset();
            subscription.unknown();
        }
        loadStatus(false);
    }

    void refresh() {
        loadProfile(true);
        loadStatus(true);
    }

    private void loadStatus(boolean force) {
        if (channel == null || channel.channelId == null) return;
        Snapshot existing = ChannelStateService.current(channel.channelId);
        if (existing != null) applyState(existing); else subscription.unknown();
        subscription.bindChannel(channel.channelId, reference != null ? reference.videoId : null);
        if (statusRequest != null && !statusRequest.isDisposed()) return;
        long token = generation;
        String owner = currentAccount();
        statusRequest = ChannelStateService.load(channel.channelId, reference != null ? reference.videoId : null, force)
                .observeOn(AndroidSchedulers.mainThread()).subscribe(value -> {
                    if (isCurrent(token, owner)) applyState(value);
                }, error -> { if (isCurrent(token, owner)) subscribe.setEnabled(statusKnown); });
    }

    private void applyState(Snapshot state) {
        subscribed = state.subscribed;
        statusKnown = true;
        channel.isSubscribed = state.subscribed;
        channel.subscriptionAccountKey = state.account;
        channel.notificationStates = state.notifications;
        subscription.bind(state.subscribed, state.notifications);
        subscribe.setEnabled(true);
    }

    private void changeSubscription(boolean enable) {
        if (YouTubeSignInService.instance().getSelectedAccount() == null) {
            AccountSelectionPresenter.instance(activity).show();
            return;
        }
        if (!statusKnown || channel == null || channel.channelId == null
                || (writeRequest != null && !writeRequest.isDisposed())) return;
        long requestGeneration = generation;
        String requestAccount = currentAccount();
        subscribe.setEnabled(false);
        Observable<Void> action = enable ? YouTubeServiceManager.instance().getMediaItemService().subscribeObserve(channel.channelId)
                : YouTubeServiceManager.instance().getMediaItemService().unsubscribeObserve(channel.channelId);
        writeRequest = action.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(
                ignored -> { }, error -> {
                    if (isCurrent(requestGeneration, requestAccount)) {
                        subscribe.setEnabled(true);
                        MobileSnackbar.show(activity, enable ? R.string.subscribe_failed : R.string.unsubscribe_failed);
                    }
                }, () -> {
                    if (!isCurrent(requestGeneration, requestAccount)) return;
                    Snapshot confirmed = ChannelStateService.current(channel.channelId);
                    if (confirmed != null) applyState(confirmed);
                });
    }

    void cancel() {
        generation++;
        if (statusRequest != null) statusRequest.dispose();
        if (writeRequest != null) writeRequest.dispose();
        subscription.cancel();
    }

    void destroy() {
        avatar.cancel();
        cancel();
        if (profileRequest != null) profileRequest.dispose();
        closeAbout();
    }

    private boolean isCurrent(long token, String owner) {
        return token == generation && owner.equals(currentAccount()) && !activity.isFinishing() && !activity.isDestroyed();
    }

    private String currentAccount() { return ChannelStateService.accountKey(); }

    private void setText(int id, String text) {
        TextView view = root.findViewById(id);
        view.setText(text);
        boolean empty = text == null || text.trim().isEmpty();
        int emptyVisibility = id == R.id.channel_header_counts ? View.INVISIBLE : View.GONE;
        view.setVisibility(empty ? emptyVisibility : View.VISIBLE);
    }

    private void image(int id, String url, boolean circle) {
        ImageView image = root.findViewById(id);
        if (url == null) { if (!circle) image.setVisibility(View.GONE); return; }
        image.setVisibility(View.VISIBLE);
        if (circle) Glide.with(image).load(url).circleCrop().into(image);
        else Glide.with(image).load(url).centerCrop().into(image);
    }

    private void showAbout() {
        if (profile == null) return;
        if (details == null) details = new ChannelDetailsPage(activity);
        details.show(profile, channel.channelId);
    }

    boolean closeAbout() { return details != null && details.close(); }

    private void showLinks() {
        if (profile != null) ChannelDetailsPage.showLinks(activity, profile.links);
    }

    static String linkSummary(android.content.Context context, List<ChannelProfile.Link> links) {
        if (links.isEmpty()) return "";
        String first = ChannelDetailsPage.displayUrl(links.get(0).url);
        int more = links.size() - 1;
        return more == 0 ? first : context.getResources().getQuantityString(
                R.plurals.mobile_channel_more_links, more, first, more);
    }

    static String join(String first, String second) {
        return first == null ? second : second == null ? first : first + " · " + second;
    }
}
