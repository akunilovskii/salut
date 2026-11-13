package com.newtube.mobile.ui.playback;

import android.app.Activity;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.view.ViewCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.liskovsoft.mediaserviceinterfaces.data.NotificationState;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.newtube.mobile.ui.common.MobileSnackbar;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/** Subscription presentation and the existing provider's channel-notification choices. */
public final class WatchSubscriptionControl {
    public static final class Scope {
        final String video;
        final Object account;

        public Scope(String video, Object account) {
            this.video = video;
            this.account = account;
        }

        boolean matches(Scope other) {
            return other != null && java.util.Objects.equals(account, other.account) && TextUtils.equals(video, other.video);
        }
    }

    private final Activity mActivity;
    private final MaterialButton mSubscribe;
    private final View mNotifications;
    private final ImageView mBell;
    private final Supplier<Scope> mScope;
    private final Function<NotificationState, Observable<Void>> mWriter;
    private final Runnable mUnsubscribe;
    private final Scheduler mWorker;
    private final Scheduler mUi;
    private List<NotificationState> mStates = Collections.emptyList();
    private Scope mBoundScope;
    private Disposable mRequest;
    private Disposable mStateObservation;
    private String mStateChannel, mNotificationVideo;
    private boolean mRefreshNotificationStates;
    private BottomSheetDialog mDialog;
    private boolean mSubscribed;
    private boolean mStatusKnown = true;
    private boolean mPending;
    private int mSelected = -1;
    private int mProviderSelected = -1;
    private long mGeneration;

    public WatchSubscriptionControl(Activity activity, View root, Supplier<Scope> scope,
            Function<NotificationState, Observable<Void>> writer, Runnable unsubscribe) {
        this(activity, root, scope, writer, unsubscribe, Schedulers.io(), AndroidSchedulers.mainThread());
    }

    WatchSubscriptionControl(Activity activity, View root, Supplier<Scope> scope,
            Function<NotificationState, Observable<Void>> writer, Runnable unsubscribe,
            Scheduler worker, Scheduler ui) {
        mActivity = activity;
        mSubscribe = root.findViewById(R.id.mobile_watch_subscribe);
        mNotifications = root.findViewById(R.id.mobile_watch_notifications);
        mBell = root.findViewById(R.id.mobile_watch_notification_icon);
        mScope = scope;
        mWriter = writer;
        mUnsubscribe = unsubscribe;
        mWorker = worker;
        mUi = ui;
        mNotifications.setOnClickListener(view -> showMenu());
        reset();
    }

    public void bind(boolean subscribed, List<NotificationState> states) {
        refreshScope();
        mStatusKnown = true;
        com.liskovsoft.mediaserviceinterfaces.data.ChannelStateStore.Snapshot shared = mStateChannel == null ? null
                : com.liskovsoft.youtubeapi.service.ChannelStateService.current(mStateChannel);
        if (mStateChannel != null && shared == null) { unknown(); return; }
        if (shared != null) { subscribed = shared.subscribed; states = shared.notifications; }
        if (mSubscribed && !subscribed) cancel();
        List<NotificationState> available = states != null ? states : Collections.emptyList();
        int selected = shared != null ? shared.notificationSelection : selectedIndex(available);
        if (!mPending && (available != mStates || selected != mProviderSelected)) mSelected = selected;
        mProviderSelected = selected;
        mStates = available;
        mSubscribed = subscribed;
        render();
    }

    public void unknown() {
        mStatusKnown = false;
        mSubscribe.setVisibility(View.GONE);
        mNotifications.setVisibility(View.GONE);
    }

    public void bindChannel(String channel, String video) {
        refreshScope();
        if (mStateObservation != null) mStateObservation.dispose();
        mStateChannel = channel;
        mNotificationVideo = video;
        if (channel == null) { unknown(); return; }
        com.liskovsoft.mediaserviceinterfaces.data.ChannelStateStore.Snapshot shared =
                com.liskovsoft.youtubeapi.service.ChannelStateService.current(channel);
        if (shared != null) bind(shared.subscribed, shared.notifications); else unknown();
        Scope owner = mScope.get();
        mStateObservation = com.liskovsoft.youtubeapi.service.ChannelStateService.observe(channel)
                .observeOn(AndroidSchedulers.mainThread()).subscribe(value -> {
                    if (owner.matches(mScope.get()) && !mActivity.isDestroyed()) bind(value.subscribed, value.notifications);
                });
    }

    void refreshScope() {
        if (!mBoundScope.matches(mScope.get())) reset();
    }

    public void reset() {
        cancel();
        mBoundScope = mScope.get();
        mStates = Collections.emptyList();
        mSelected = -1;
        mProviderSelected = -1;
        mRefreshNotificationStates = false;
        mSubscribed = false;
        mStatusKnown = true;
        render();
    }

    public void cancel() {
        if (mStateObservation != null) mStateObservation.dispose();
        mGeneration++;
        if (mRequest != null) mRequest.dispose();
        mRequest = null;
        mPending = false;
        if (mDialog != null) mDialog.dismiss();
        mDialog = null;
        render();
    }

    void showMenu() {
        if (mSubscribed && !mPending && (mStates.isEmpty() || mRefreshNotificationStates) && mStateChannel != null && mNotificationVideo != null) {
            Scope owner = mScope.get();
            long generation = mGeneration;
            mPending = true;
            render();
            mRequest = com.liskovsoft.youtubeapi.service.ChannelStateService.load(mStateChannel, mNotificationVideo, true)
                    .observeOn(AndroidSchedulers.mainThread()).subscribe(value -> {
                        if (!current(owner, generation)) return;
                        bindLoadedState(value);
                        showMenuReady();
                    }, error -> {
                        if (!current(owner, generation)) return;
                        mPending = false;
                        render();
                        showMenuReady();
                    });
            return;
        }
        showMenuReady();
    }

    void bindLoadedState(com.liskovsoft.mediaserviceinterfaces.data.ChannelStateStore.Snapshot state) {
        mRefreshNotificationStates = false;
        mPending = false;
        mSelected = state.notificationSelection;
        mProviderSelected = state.notificationSelection;
        bind(state.subscribed, state.notifications);
    }

    private void showMenuReady() {
        if (!mSubscribed || mPending || !mBoundScope.matches(mScope.get())) return;
        Scope menuScope = mBoundScope;
        long generation = mGeneration;
        List<NotificationState> menuStates = mStates;
        BottomSheetDialog dialog = new BottomSheetDialog(mActivity,
                R.style.ThemeOverlay_NewTube_BottomSheetDialog_WatchNotifications);
        View content = LayoutInflater.from(mActivity).inflate(R.layout.sheet_watch_notifications, null);
        LinearLayout choices = content.findViewById(R.id.watch_notification_choices);
        for (int index = 0; index < menuStates.size(); index++) {
            final int choice = index;
            View row = LayoutInflater.from(mActivity).inflate(
                    R.layout.item_watch_notification_choice, choices, false);
            ((TextView) row.findViewById(R.id.watch_notification_label))
                    .setText(menuStates.get(index).getTitle());
            ((ImageView) row.findViewById(R.id.watch_notification_icon))
                    .setImageResource(notificationIcon(index));
            row.findViewById(R.id.watch_notification_selected)
                    .setVisibility(index == mSelected ? View.VISIBLE : View.GONE);
            ViewCompat.setStateDescription(row, index == mSelected
                    ? mActivity.getString(R.string.mobile_notification_selected) : null);
            row.setOnClickListener(view -> {
                dialog.dismiss();
                if (current(menuScope, generation) && menuStates == mStates) selectMode(choice);
            });
            choices.addView(row);
        }
        content.findViewById(R.id.watch_notification_unsubscribe).setOnClickListener(view -> {
            dialog.dismiss();
            if (current(menuScope, generation)) mUnsubscribe.run();
        });
        dialog.setContentView(content);
        mDialog = dialog;
        dialog.show();
        View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet != null) {
            int margin = Math.round(8 * mActivity.getResources().getDisplayMetrics().density);
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) sheet.getLayoutParams();
            params.setMargins(margin, 0, margin, 0);
            sheet.setLayoutParams(params);
            sheet.setBackgroundResource(R.drawable.bg_watch_notification_sheet);
            sheet.setClipToOutline(true);
            sheet.setTranslationY(-margin);
        }
    }

    private static int notificationIcon(int index) {
        if (index == 0) return R.drawable.ic_watch_bell_all;
        if (index == 2) return R.drawable.ic_watch_bell_off;
        return R.drawable.ic_watch_bell;
    }

    void selectMode(int index) {
        if (mPending || !mSubscribed || index < 0 || index >= mStates.size()
                || !mBoundScope.matches(mScope.get())) return;
        Scope scope = mScope.get();
        if (scope.account == null) return;
        long generation = ++mGeneration;
        NotificationState state = mStates.get(index);
        mPending = true;
        render();
        mRequest = Observable.defer(() -> current(scope, generation)
                        ? mWriter.apply(state) : Observable.<Void>empty())
                .subscribeOn(mWorker).observeOn(mUi).subscribe(ignored -> { }, error -> {
                    if (!current(scope, generation)) return;
                    mProviderSelected = selectedIndex(mStates);
                    mRefreshNotificationStates = !(error instanceof com.liskovsoft.youtubeapi.notifications.NotificationWebAuthorizationRequired);
                    mPending = false;
                    render();
                    MobileSnackbar.show(mActivity, error instanceof com.liskovsoft.youtubeapi.notifications.NotificationWebAuthorizationRequired
                            ? R.string.mobile_notification_web_authorization_required : R.string.mobile_watch_notifications_failed);
                }, () -> {
                    if (!current(scope, generation)) return;
                    mSelected = index;
                    if (mStateChannel != null) com.liskovsoft.youtubeapi.service.ChannelStateService.store().selectNotification(
                            com.liskovsoft.youtubeapi.service.ChannelStateService.accountKey(), mStateChannel, index);
                    mProviderSelected = selectedIndex(mStates);
                    mPending = false;
                    render();
                });
    }

    private boolean current(Scope scope, long generation) {
        return generation == mGeneration && scope.matches(mScope.get());
    }

    private void render() {
        if (!mStatusKnown) {
            mSubscribe.setVisibility(View.GONE);
            mNotifications.setVisibility(View.GONE);
            return;
        }
        mSubscribe.setVisibility(mSubscribed ? View.GONE : View.VISIBLE);
        mNotifications.setVisibility(mSubscribed ? View.VISIBLE : View.GONE);
        mNotifications.setEnabled(!mPending);
        // The provider defines its three positions as All, Personalized, None.
        int icon = mStates.size() == 3 && mSelected == 0 ? R.drawable.ic_watch_bell_all
                : mStates.size() == 3 && mSelected == 2 ? R.drawable.ic_watch_bell_off
                : R.drawable.ic_watch_bell;
        mBell.setImageResource(icon);
        ViewCompat.setStateDescription(mNotifications,
                mSelected >= 0 && mSelected < mStates.size() ? mStates.get(mSelected).getTitle() : null);
    }

    static int selectedIndex(List<NotificationState> states) {
        for (int i = 0; i < states.size(); i++) if (states.get(i).isSelected()) return i;
        return -1;
    }
}
