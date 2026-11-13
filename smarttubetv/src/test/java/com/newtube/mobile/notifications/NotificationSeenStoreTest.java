package com.newtube.mobile.notifications;

import static org.junit.Assert.*;
import android.app.Application;
import android.content.Context;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class NotificationSeenStoreTest {
    @Test public void initialSyncIsSilentAndReloadsDeduplicateNewVideos() {
        Context context = RuntimeEnvironment.getApplication();
        NotificationSeenStore store = new NotificationSeenStore(context.getSharedPreferences("seen-test", 0));
        assertTrue(store.unseen("a", Arrays.asList("old")).isEmpty());
        store.accept("a", Arrays.asList("old"));
        assertEquals(Collections.singletonList("new"), store.unseen("a", Arrays.asList("old", "new", "new")));
        store.accept("a", Arrays.asList("new", "old"));
        store = new NotificationSeenStore(context.getSharedPreferences("seen-test", 0));
        assertTrue(store.unseen("a", Arrays.asList("old", "new")).isEmpty());
    }
    @Test public void immediateAndPeriodicChecksShareARecentSuccessfulBaseline() {
        NotificationSeenStore store = new NotificationSeenStore(RuntimeEnvironment.getApplication().getSharedPreferences("seen-cooldown", 0));
        store.accept("a", Collections.emptyList());
        long now = System.currentTimeMillis();
        assertTrue(store.recentlyChecked("a", now));
        assertFalse(store.recentlyChecked("b", now));
        assertFalse(store.recentlyChecked("a", now + 60_000));
        assertFalse(store.recentlyChecked("a", now - 60_000));
    }
    @Test public void accountsAndEmptyBaselinesRemainIndependent() {
        NotificationSeenStore store = new NotificationSeenStore(RuntimeEnvironment.getApplication().getSharedPreferences("seen-accounts", 0));
        store.accept("a", Collections.emptyList());
        assertEquals(Collections.singletonList("video"), store.unseen("a", Collections.singletonList("video")));
        assertTrue(store.unseen("b", Collections.singletonList("video")).isEmpty());
        store.accept("a", Collections.singletonList("video"));
        store.accept("a", Collections.emptyList());
        assertTrue(store.unseen("a", Collections.singletonList("video")).isEmpty());
    }
}
