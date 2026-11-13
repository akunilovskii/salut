package com.newtube.mobile.subscriptions;

import android.content.SharedPreferences;

import com.liskovsoft.googlecommon.service.oauth.YouTubeAccount;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Account-scoped viewing state, separate from shared application preferences. */
public final class SubscriptionSeenStore {
    private final SharedPreferences mPrefs;

    public SubscriptionSeenStore(SharedPreferences prefs) { mPrefs = prefs; }

    public SubscriptionUpdates load(String key) {
        return new SubscriptionUpdates(mPrefs.getStringSet(key + ":seen", null),
                mPrefs.getBoolean(key + ":new", false));
    }

    public void save(String key, SubscriptionUpdates updates) {
        mPrefs.edit().putStringSet(key + ":seen", updates.seen())
                .putBoolean(key + ":new", updates.hasNewVideos()).apply();
    }

    public static String accountKey(Account account) {
        if (account == null || account.isEmpty()) return null;
        String channel = account instanceof YouTubeAccount ? ((YouTubeAccount) account).getPageIdToken() : null;
        String identity = String.valueOf(account.getEmail()) + "\n" + account.getName() + "\n" + channel;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8));
            StringBuilder key = new StringBuilder();
            for (byte part : digest) key.append(String.format(java.util.Locale.ROOT, "%02x", part & 255));
            return key.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
