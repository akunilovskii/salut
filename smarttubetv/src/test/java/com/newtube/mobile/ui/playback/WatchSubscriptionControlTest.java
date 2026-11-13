package com.newtube.mobile.ui.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import androidx.core.view.ViewCompat;

import com.google.android.material.button.MaterialButton;
import com.liskovsoft.mediaserviceinterfaces.data.NotificationState;
import com.liskovsoft.smartyoutubetv2.tv.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.schedulers.TestScheduler;
import io.reactivex.rxjava3.subjects.PublishSubject;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class, qualifiers = "mdpi")
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WatchSubscriptionControlTest {
    private Activity mActivity;
    private View mRoot;
    private WatchSubscriptionControl mControl;
    private WatchSubscriptionControl.Scope mScope;
    private PublishSubject<Void> mResponse;
    private int mWrites;
    private int mUnsubscribes;
    private List<NotificationState> mStates;
    private TestScheduler mWorker;

    @Before public void setUp() {
        mActivity = Robolectric.buildActivity(Activity.class).setup().get();
        mActivity.setTheme(R.style.Theme_NewTube);
        mScope = new WatchSubscriptionControl.Scope("video", new Object());
        mResponse = PublishSubject.create();
        mStates = Arrays.asList(state("All", false), state("Personalized", true), state("None", false));
        inflate(1f, false);
    }

    @After public void tearDown() {
        mControl.cancel();
        mActivity.finish();
    }

    @Test public void stableAccountKeysMatchByValueRatherThanStringIdentity() {
        assertTrue(new WatchSubscriptionControl.Scope("channel", new String("account"))
                .matches(new WatchSubscriptionControl.Scope("channel", new String("account"))));
        assertFalse(new WatchSubscriptionControl.Scope("channel", "account A")
                .matches(new WatchSubscriptionControl.Scope("channel", "account B")));
    }

    @Test public void firstNotificationLoadAdoptsTheProvidersSelectedMode() {
        com.liskovsoft.mediaserviceinterfaces.data.ChannelStateStore store =
                new com.liskovsoft.mediaserviceinterfaces.data.ChannelStateStore();
        store.accept("account", "channel", true, mStates, store.revision());
        mControl.bind(true, java.util.Collections.emptyList());
        mControl.bindLoadedState(store.get("account", "channel"));
        assertEquals("Personalized", androidx.core.view.ViewCompat.getStateDescription(
                mRoot.findViewById(R.id.mobile_watch_notifications)).toString());
    }

    @Test public void unknownStatusDoesNotBecomeSubscribeWhenLeavingTheScreen() {
        mControl.unknown();
        mControl.cancel();
        assertEquals(View.GONE, mRoot.findViewById(R.id.mobile_watch_subscribe).getVisibility());
        assertEquals(View.GONE, mRoot.findViewById(R.id.mobile_watch_notifications).getVisibility());
        mControl.bind(true, mStates);
        assertEquals(View.GONE, mRoot.findViewById(R.id.mobile_watch_subscribe).getVisibility());
        assertEquals(View.VISIBLE, mRoot.findViewById(R.id.mobile_watch_notifications).getVisibility());
    }

    @Test public void subscribedChannelsUseTheNotificationControlWithoutAWordLabel() {
        mControl.bind(false, mStates);
        assertEquals(View.VISIBLE, mRoot.findViewById(R.id.mobile_watch_subscribe).getVisibility());
        assertEquals(View.GONE, notifications().getVisibility());
        mControl.bind(true, mStates);
        assertEquals(View.GONE, mRoot.findViewById(R.id.mobile_watch_subscribe).getVisibility());
        assertEquals(View.VISIBLE, notifications().getVisibility());
        assertEquals("Personalized", ViewCompat.getStateDescription(notifications()));
    }

    @Test public void aSuccessfulChoiceUpdatesTheBellAndCannotBeSubmittedTwice() {
        mControl.bind(true, mStates);
        mControl.selectMode(0);
        mControl.selectMode(2);
        assertEquals(1, mWrites);
        assertFalse(notifications().isEnabled());
        mResponse.onComplete();
        assertTrue(notifications().isEnabled());
        assertEquals("All", ViewCompat.getStateDescription(notifications()));
        mControl.bind(true, mStates);
        assertEquals("All", ViewCompat.getStateDescription(notifications()));
    }

    @Test public void aFailedChoiceKeepsThePreviouslySelectedMode() {
        mControl.bind(true, mStates);
        mControl.selectMode(0);
        mResponse.onError(new IOException("offline"));
        assertTrue(notifications().isEnabled());
        assertEquals("Personalized", ViewCompat.getStateDescription(notifications()));
    }

    @Test public void modeChangesFromAnotherAppControlAreReflectedOnRebind() {
        int[] selected = {1};
        List<NotificationState> states = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) {
            int index = i;
            states.add(new NotificationState() {
                @Override public boolean isSelected() { return selected[0] == index; }
                @Override public String getTitle() { return mStates.get(index).getTitle(); }
            });
        }
        mControl.bind(true, states);
        selected[0] = 2;
        mControl.bind(true, states);
        assertEquals("None", ViewCompat.getStateDescription(notifications()));
    }

    @Test public void aNewAccountCannotReceiveAQueuedChoiceFromTheOldOne() {
        inflate(1f, true);
        mControl.bind(true, mStates);
        mControl.selectMode(0);
        mScope = new WatchSubscriptionControl.Scope("video", new Object());
        mWorker.triggerActions();
        assertEquals(0, mWrites);
        mControl.bind(false, null);
        assertEquals(View.GONE, notifications().getVisibility());
    }

    @Test public void resettingForAnotherVideoDisposesAnOutstandingChoice() {
        mControl.bind(true, mStates);
        mControl.selectMode(0);
        assertTrue(mResponse.hasObservers());
        mScope = new WatchSubscriptionControl.Scope("next", mScope.account);
        mControl.reset();
        assertFalse(mResponse.hasObservers());
        mResponse.onComplete();
        assertEquals(View.GONE, notifications().getVisibility());
        assertNull(ViewCompat.getStateDescription(notifications()));
    }

    @Test public void resumingWithAnotherAccountClearsTheOldNotificationPresentation() {
        mControl.bind(true, mStates);
        mScope = new WatchSubscriptionControl.Scope("video", new Object());
        mControl.refreshScope();
        assertEquals(View.GONE, notifications().getVisibility());
        assertEquals(View.VISIBLE, mRoot.findViewById(R.id.mobile_watch_subscribe).getVisibility());
        assertEquals(0, mWrites);
    }

    @Test public void menuKeepsUnsubscribeAndRejectsItsStaleCallback() {
        mControl.bind(true, mStates);
        mControl.showMenu();
        BottomSheetDialog dialog = (BottomSheetDialog) ShadowDialog.getLatestDialog();
        assertEquals(3, ((LinearLayout) dialog.findViewById(R.id.watch_notification_choices)).getChildCount());
        View unsubscribe = dialog.findViewById(R.id.watch_notification_unsubscribe);
        unsubscribe.performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, mUnsubscribes);
        mControl.showMenu();
        dialog = (BottomSheetDialog) ShadowDialog.getLatestDialog();
        unsubscribe = dialog.findViewById(R.id.watch_notification_unsubscribe);
        mControl.reset();
        unsubscribe.performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, mUnsubscribes);
    }

    @Test public void menuMarksTheSelectedModeAndAppliesAChoice() {
        mControl.bind(true, mStates);
        mControl.showMenu();
        BottomSheetDialog dialog = (BottomSheetDialog) ShadowDialog.getLatestDialog();
        LinearLayout choices = dialog.findViewById(R.id.watch_notification_choices);
        assertEquals(View.GONE, choices.getChildAt(0)
                .findViewById(R.id.watch_notification_selected).getVisibility());
        assertEquals(View.VISIBLE, choices.getChildAt(1)
                .findViewById(R.id.watch_notification_selected).getVisibility());

        choices.getChildAt(0).performClick();
        assertFalse(dialog.isShowing());
        assertEquals(1, mWrites);
        mResponse.onComplete();
        assertEquals("All", ViewCompat.getStateDescription(notifications()));
    }

    @Test public void absentMetadataDoesNotInventNotificationModes() {
        mControl.bind(true, null);
        mControl.selectMode(0);
        assertEquals(0, mWrites);
        assertNull(ViewCompat.getStateDescription(notifications()));
        mControl.showMenu();
        BottomSheetDialog dialog = (BottomSheetDialog) ShadowDialog.getLatestDialog();
        assertEquals(0, ((LinearLayout) dialog.findViewById(R.id.watch_notification_choices)).getChildCount());
        assertTrue(dialog.findViewById(R.id.watch_notification_unsubscribe).isEnabled());
    }

    @Test public void losingSubscriptionInvalidatesAnAlreadyOpenMenu() {
        mControl.bind(true, mStates);
        mControl.showMenu();
        BottomSheetDialog dialog = (BottomSheetDialog) ShadowDialog.getLatestDialog();
        View unsubscribe = dialog.findViewById(R.id.watch_notification_unsubscribe);
        mControl.bind(false, mStates);
        assertFalse(dialog.isShowing());
        unsubscribe.performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(0, mUnsubscribes);
        assertEquals(View.GONE, notifications().getVisibility());
    }

    @Test
    @Config(qualifiers = "night-420dpi")
    public void subscribeTextAndVisiblePillMatchThePixelReference() {
        inflate(1.15f, false);
        mRoot.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(126, View.MeasureSpec.EXACTLY));
        mRoot.layout(0, 0, mRoot.getMeasuredWidth(), 126);
        MaterialButton button = mRoot.findViewById(R.id.mobile_watch_subscribe);
        Bitmap bitmap = Bitmap.createBitmap(button.getWidth(), button.getHeight(), Bitmap.Config.ARGB_8888);
        button.draw(new Canvas(bitmap));
        Rect ink = bounds(bitmap, false);
        Rect pill = bounds(bitmap, true);
        assertTrue("Reference Subscribe ink is 156px wide: " + ink, Math.abs(ink.width() - 156) <= 3);
        assertTrue("Reference Subscribe ink is 27px high: " + ink, Math.abs(ink.height() - 27) <= 1);
        assertEquals(84, pill.height());
        assertEquals(126, button.getHeight());
        assertEquals(36f, button.getTextSize(), 0.01f);
    }

    @Test public void compactBellMatchesWatchSizeWithoutShrinkingTheTouchTarget() {
        mControl.bind(true, mStates);
        View bell = mRoot.findViewById(R.id.mobile_watch_notification_icon);
        float density = mActivity.getResources().getDisplayMetrics().density;
        assertEquals(Math.round(18 * density), bell.getLayoutParams().width);
        assertEquals(Math.round(18 * density), bell.getLayoutParams().height);
        assertEquals(Math.round(48 * density), notifications().getLayoutParams().height);
        assertEquals(Math.round(56 * density), notifications().getLayoutParams().width);
    }

    private void inflate(float fontScale, boolean delayed) {
        if (mControl != null) mControl.cancel();
        Configuration config = new Configuration(mActivity.getResources().getConfiguration());
        config.fontScale = fontScale;
        Context context = new ContextThemeWrapper(mActivity.createConfigurationContext(config), R.style.Theme_NewTube);
        FrameLayout host = new FrameLayout(mActivity);
        mRoot = LayoutInflater.from(context).inflate(R.layout.mobile_watch_channel_controls, host, false);
        host.addView(mRoot);
        mActivity.setContentView(host);
        mWorker = new TestScheduler();
        mControl = new WatchSubscriptionControl(mActivity, mRoot, () -> mScope, state -> {
            mWrites++;
            return mResponse;
        }, () -> mUnsubscribes++, delayed ? mWorker : Schedulers.trampoline(), Schedulers.trampoline());
    }

    private View notifications() { return mRoot.findViewById(R.id.mobile_watch_notifications); }

    private NotificationState state(String title, boolean selected) {
        return new NotificationState() {
            @Override public boolean isSelected() { return selected; }
            @Override public String getTitle() { return title; }
        };
    }

    private Rect bounds(Bitmap image, boolean white) {
        Rect rect = new Rect(image.getWidth(), image.getHeight(), 0, 0);
        Rect area = white ? new Rect(0, 0, image.getWidth(), image.getHeight()) : bounds(image, true);
        // Exclude the pill's border and shadow when measuring the lettering.
        if (!white) area.inset(8, 8);
        for (int y = area.top; y < area.bottom; y++) for (int x = area.left; x < area.right; x++) {
            int color = image.getPixel(x, y);
            if (Color.alpha(color) > 200 && (white ? Color.red(color) > 220 : Color.red(color) < 100)) {
                rect.left = Math.min(rect.left, x);
                rect.top = Math.min(rect.top, y);
                rect.right = Math.max(rect.right, x + 1);
                rect.bottom = Math.max(rect.bottom, y + 1);
            }
        }
        return rect;
    }
}
