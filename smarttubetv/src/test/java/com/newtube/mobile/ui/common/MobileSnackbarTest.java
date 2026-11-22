package com.newtube.mobile.ui.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Application;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.liskovsoft.smartyoutubetv2.tv.R;
import com.google.android.material.snackbar.Snackbar;
import com.newtube.mobile.ui.dialog.MobileAppDialogActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowSystemClock;
import org.robolectric.shadows.ShadowToast;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * NEWTUBE(snackbar): a message sent while a menu sheet is in front ("Download started" from the
 * download picker) must reach the screen under the sheet whether or not that screen was paused
 * behind the translucent sheet. The Pixel (Android 17) showed neither a Snackbar nor its View
 * action for the watch page's download, while the API-36 emulator, which pauses the screen and
 * resumes it, did. The lifecycle is driven by hand so both orders can be replayed exactly.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@LooperMode(LooperMode.Mode.PAUSED)
public class MobileSnackbarTest {
    private static final String TEXT = "Download started • 44.7 MB";
    private static final String DONE = "Downloaded • 44.7 MB";

    /** MobileSnackbar installs once per process; keep the callbacks it registered. */
    private static Application.ActivityLifecycleCallbacks sCallbacks;

    private Activity host;
    private MobileAppDialogActivity sheet;

    @Before
    public void setUp() {
        if (sCallbacks == null) {
            MobileSnackbar.install(new Application() {
                @Override
                public void registerActivityLifecycleCallbacks(ActivityLifecycleCallbacks callback) {
                    sCallbacks = callback;
                }
            });
        }
        host = Robolectric.buildActivity(Activity.class).setup().get();
        host.setTheme(R.style.Theme_NewTube);
        sheet = Robolectric.buildActivity(MobileAppDialogActivity.class).get();
        ShadowToast.reset();
    }

    @After
    public void tearDown() {
        sCallbacks.onActivityPaused(host);
        sCallbacks.onActivityStopped(host);
        idle(5_000); // lets any message still waiting end, so the next test starts clean
        host.finish();
    }

    @Test
    public void screenLeftResumedBehindTheSheetStillGetsTheSnackbar() {
        sCallbacks.onActivityResumed(host);
        sCallbacks.onActivityResumed(sheet); // Android 17: the host is NOT paused

        MobileSnackbar.show(host, TEXT, "View", () -> { });
        sCallbacks.onActivityPaused(sheet); // the sheet closes; the host gets no resume
        idle(400);

        assertEquals(1, countShown(TEXT));
        idle(2_000);
        assertNull("no fallback Toast on top of the Snackbar", ShadowToast.getLatestToast());
    }

    @Test
    public void screenPausedBehindTheSheetGetsItOnResumeOnlyOnce() {
        sCallbacks.onActivityResumed(host);
        sCallbacks.onActivityPaused(host); // API 36: paused under the translucent sheet
        sCallbacks.onActivityResumed(sheet);

        MobileSnackbar.show(host, TEXT, "View", () -> { });
        sCallbacks.onActivityPaused(sheet);
        idle(400);
        assertEquals("waits for the host to resume", 0, countShown(TEXT));

        sCallbacks.onActivityResumed(host);
        idle(400);
        assertEquals(1, countShown(TEXT));
        idle(2_000);
        assertEquals(1, countShown(TEXT));
        assertNull(ShadowToast.getLatestToast());
    }

    @Test
    public void sheetStillOpenKeepsTheMessageForLater() {
        sCallbacks.onActivityResumed(host);
        sCallbacks.onActivityResumed(sheet);

        MobileSnackbar.show(host, TEXT, "View", () -> { });
        idle(400);
        assertEquals("not under an open sheet", 0, countShown(TEXT));
        sCallbacks.onActivityPaused(sheet);
        idle(400);
        assertEquals(1, countShown(TEXT));
    }

    @Test
    public void nothingOnScreenFallsBackToTheToast() {
        sCallbacks.onActivityResumed(host);
        sCallbacks.onActivityResumed(sheet);
        MobileSnackbar.show(host, TEXT, "View", () -> { });
        sCallbacks.onActivityPaused(sheet);
        sCallbacks.onActivityPaused(host);
        sCallbacks.onActivityStopped(host); // the app went to the background
        idle(2_000);

        assertEquals(0, countShown(TEXT));
        assertEquals(TEXT, ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void resumeRunningLateBehindABusyMainThreadStillEndsInTheToast() {
        sCallbacks.onActivityResumed(host);
        sCallbacks.onActivityPaused(host);
        sCallbacks.onActivityResumed(sheet);
        MobileSnackbar.show(host, TEXT, "View", () -> { });
        sCallbacks.onActivityPaused(sheet);

        // The main thread is blocked past the message's age; the host's resume, queued before the
        // fallback Toast's time, runs first when it frees up.
        ShadowSystemClock.advanceBy(Duration.ofMillis(3_500));
        sCallbacks.onActivityResumed(host);
        idle(0);

        assertEquals(TEXT, ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void aMessageRewordedWhileWaitingStillReachesTheUnpausedScreen() {
        sCallbacks.onActivityResumed(host);
        sCallbacks.onActivityResumed(sheet);
        MobileSnackbar.show(host, TEXT, "View", () -> { });
        sCallbacks.onActivityPaused(sheet);
        idle(100);
        MobileSnackbar.replaceText(TEXT, DONE); // a small file finished inside the 250 ms
        idle(400);

        assertEquals(1, countShown(DONE));
        assertEquals(0, countShown(TEXT));
    }

    @Test
    public void aMessageRewordedWhileWaitingStillEndsInItsToast() {
        sCallbacks.onActivityResumed(host);
        sCallbacks.onActivityResumed(sheet);
        MobileSnackbar.show(host, TEXT, "View", () -> { });
        MobileSnackbar.replaceText(TEXT, DONE);
        sCallbacks.onActivityPaused(sheet);
        sCallbacks.onActivityPaused(host);
        sCallbacks.onActivityStopped(host);
        idle(2_000);

        assertEquals(DONE, ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void aMessageRewordedBeforeItsFrameShowsTheNewWords() {
        sCallbacks.onActivityResumed(host);
        sCallbacks.onActivityPaused(host);
        sCallbacks.onActivityResumed(sheet);
        MobileSnackbar.show(host, TEXT, "View", () -> { });
        sCallbacks.onActivityPaused(sheet);
        sCallbacks.onActivityResumed(host); // handed over; drawn on the next frame
        MobileSnackbar.replaceText(TEXT, DONE);
        idle(400);

        assertEquals(1, countShown(DONE));
    }

    @Test
    public void quickNoticeUsesThreeSecondsAndOnlyItsBackgroundIsTranslucent() {
        sCallbacks.onActivityResumed(host);
        Snackbar notice = MobileSnackbar.showTransient(host, "Added to Watch later", "Undo", () -> { });
        assertEquals(3_000, notice.getDuration());
        assertEquals(230, notice.getView().getBackground().getAlpha());
        assertEquals(1f, notice.getView().getAlpha(), 0f);
        notice.dismiss();
    }

    @Test
    public void quickNoticeOffersUndoWithoutRequiringIt() {
        sCallbacks.onActivityResumed(host);
        AtomicInteger undos = new AtomicInteger();
        Snackbar notice = MobileSnackbar.showTransient(host, "Added to Watch later", "Undo", undos::incrementAndGet);
        idle(400);
        assertEquals(0, undos.get());
        assertTrue(notice.getView().findViewById(com.google.android.material.R.id.snackbar_action).performClick());
        assertEquals(1, undos.get());
        notice.dismiss();
    }

    @Test
    public void quickNoticeDoesNotBecomeAnUnwantedSystemToastInTheBackground() {
        sCallbacks.onActivityResumed(host);
        sCallbacks.onActivityPaused(host);
        assertNull(MobileSnackbar.showTransient(host, "Added to Watch later", "Undo", () -> { }));
        idle(5_000);
        assertNull(ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void quickNoticeIgnoresAppNavigationAndMiniPlayer() {
        ViewGroup content = host.findViewById(android.R.id.content);
        View player = new View(host);
        player.setId(R.id.mobile_mini_player);
        content.addView(player, new ViewGroup.LayoutParams(180, 102));
        View navigation = new View(host);
        navigation.setId(R.id.mobile_bottom_nav);
        content.addView(navigation, new ViewGroup.LayoutParams(400, 56));
        sCallbacks.onActivityResumed(host);
        Snackbar notice = MobileSnackbar.showTransient(host, "Added to Watch later", "Undo", () -> { });
        assertNull(notice.getAnchorView());
        notice.dismiss();
    }

    @Test
    public void sharedMessagesUseTheSameBottomAnchorAsQuickNotices() {
        ViewGroup content = host.findViewById(android.R.id.content);
        View player = new View(host);
        player.setId(R.id.mobile_mini_player);
        content.addView(player, new ViewGroup.LayoutParams(180, 102));
        View navigation = new View(host);
        navigation.setId(R.id.mobile_bottom_nav);
        content.addView(navigation, new ViewGroup.LayoutParams(400, 56));
        sCallbacks.onActivityResumed(host);
        com.liskovsoft.sharedutils.helpers.MessageHelpers.showMessage(host, TEXT);
        idle(400);
        assertEquals(1, countShown(TEXT));
        assertNull(ShadowToast.getTextOfLatestToast());
        try {
            java.lang.reflect.Field field = MobileSnackbar.class.getDeclaredField("sLast");
            field.setAccessible(true);
            Snackbar notice = (Snackbar) ((java.lang.ref.WeakReference<?>) field.get(null)).get();
            assertNull(notice.getAnchorView());
            notice.dismiss();
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    @Test
    public void sharedMessagesRetainSystemFallbackWithoutAForegroundScreen() {
        com.liskovsoft.sharedutils.helpers.MessageHelpers.showMessage(host, TEXT);
        idle(400);
        assertEquals(TEXT, ShadowToast.getTextOfLatestToast());
        assertEquals(0, countShown(TEXT));
    }

    @Test
    public void systemInsetsDoNotAccumulateOnTheAlreadyInsetPhoneHost() {
        ViewGroup content = host.findViewById(android.R.id.content);
        content.setPadding(0, 24, 0, 32);
        sCallbacks.onActivityResumed(host);
        Snackbar notice = MobileSnackbar.showTransient(host, TEXT, null, null);
        androidx.core.view.WindowInsetsCompat insets = new androidx.core.view.WindowInsetsCompat.Builder()
                .setInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars(),
                        androidx.core.graphics.Insets.of(0, 24, 0, 32)).build();
        androidx.core.view.ViewCompat.dispatchApplyWindowInsets(notice.getView(), insets);
        idle(400);
        ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) notice.getView().getLayoutParams();
        float density = host.getResources().getDisplayMetrics().density;
        assertEquals(Math.round(12 * density), margins.bottomMargin);
        int available = content.getWidth() - content.getPaddingLeft() - content.getPaddingRight();
        assertEquals(Math.round(available * 0.8f), margins.width);
        assertEquals((available - margins.width) / 2, margins.leftMargin);
        assertEquals((available - margins.width + 1) / 2, margins.rightMargin);
        notice.dismiss();
    }

    @Test
    public void widthTracksHostResizeAndLongTextRetainsItsAction() {
        ViewGroup content = host.findViewById(android.R.id.content);
        content.layout(0, 0, 1000, 800);
        sCallbacks.onActivityResumed(host);
        AtomicInteger actions = new AtomicInteger();
        Snackbar notice = MobileSnackbar.showTransient(host,
                "A long notification that must wrap rather than hiding its action or truncating text",
                "Sign in", actions::incrementAndGet);
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) notice.getView().getLayoutParams();
        assertEquals(800, params.width);
        assertEquals(100, params.leftMargin);
        content.layout(0, 0, 600, 800);
        params = (ViewGroup.MarginLayoutParams) notice.getView().getLayoutParams();
        assertEquals(480, params.width);
        assertEquals(60, params.leftMargin);
        TextView text = notice.getView().findViewById(com.google.android.material.R.id.snackbar_text);
        assertEquals(Integer.MAX_VALUE, text.getMaxLines());
        assertNull(text.getEllipsize());
        assertTrue(notice.getView().findViewById(com.google.android.material.R.id.snackbar_action).performClick());
        assertEquals(1, actions.get());
        notice.dismiss();
    }

    private void idle(long ms) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));
    }

    private int countShown(String text) {
        return count(host.findViewById(android.R.id.content), text);
    }

    private static int count(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())
                && view.isShown()) {
            return 1;
        }
        int n = 0;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                n += count(group.getChildAt(i), text);
            }
        }
        return n;
    }
}
