package com.newtube.mobile.subscriptions;

import static org.junit.Assert.*;

import android.app.Application;
import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.oauth.Account;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class SubscriptionSeenStoreTest {
    private SubscriptionSeenStore mStore;

    @Before public void setUp() {
        android.content.SharedPreferences prefs = RuntimeEnvironment.getApplication()
                .getSharedPreferences("seen-test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        mStore = new SubscriptionSeenStore(prefs);
    }

    @Test public void theSameLegacyIntegerIdDoesNotMergeAccounts() {
        String first = SubscriptionSeenStore.accountKey(account("first"));
        String second = SubscriptionSeenStore.accountKey(account("second"));
        assertNotEquals(first, second);
        SubscriptionUpdates updates = mStore.load(first);
        updates.tapped();
        updates.refreshed(Collections.singleton("video"));
        mStore.save(first, updates);
        assertEquals(Collections.singleton("video"), mStore.load(first).seen());
        assertNull(mStore.load(second).seen());
    }

    @Test public void pendingDotSurvivesReloadWithoutAcknowledgingVideos() {
        SubscriptionUpdates updates = mStore.load("account");
        updates.observe(Collections.singleton("new"));
        mStore.save("account", updates);
        assertTrue(mStore.load("account").hasNewVideos());
        assertNull(mStore.load("account").seen());
    }

    @Test public void acknowledgedSnapshotAndClearedDotSurviveReload() {
        SubscriptionUpdates updates = new SubscriptionUpdates(null, true);
        updates.tapped();
        updates.refreshed(Collections.singleton("video"));
        mStore.save("account", updates);
        SubscriptionUpdates loaded = mStore.load("account");
        loaded.observe(Collections.singleton("video"));
        assertFalse(loaded.hasNewVideos());
    }

    @Test public void signedOutHasNoAccountKeyAndKeysContainNoNames() {
        assertNull(SubscriptionSeenStore.accountKey(null));
        String key = SubscriptionSeenStore.accountKey(account("personal-name"));
        assertTrue(key.matches("[a-f0-9]{64}"));
    }

    private static Account account(String name) {
        return new Account() {
            @Override public int getId() { return 0; }
            @Override public String getName() { return name; }
            @Override public String getEmail() { return "same@example.test"; }
            @Override public String getAvatarImageUrl() { return null; }
            @Override public boolean isSelected() { return true; }
            @Override public boolean isEmpty() { return false; }
        };
    }
}
