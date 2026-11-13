package com.newtube.mobile.notifications;

import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Account-scoped inbox baseline. First synchronization never delivers historical entries. */
final class NotificationSeenStore {
    private static final int MAX_SEEN = 2000;
    private final SharedPreferences prefs;
    NotificationSeenStore(SharedPreferences prefs) { this.prefs = prefs; }

    synchronized List<String> unseen(String account, List<String> current) {
        String key = account + ":seen";
        Set<String> stored = prefs.getStringSet(key, null);
        List<String> result = new ArrayList<>();
        if (stored != null) for (String id : current) if (!stored.contains(id) && !result.contains(id)) result.add(id);
        return result;
    }

    synchronized boolean recentlyChecked(String account, long now) {
        long checked = prefs.getLong(account + ":checked", 0);
        return checked > 0 && checked <= now && now - checked < 60_000;
    }

    synchronized void accept(String account, List<String> current) {
        String key = account + ":seen";
        LinkedHashSet<String> seen = new LinkedHashSet<>(current);
        seen.addAll(prefs.getStringSet(key, new LinkedHashSet<>()));
        LinkedHashSet<String> bounded = new LinkedHashSet<>();
        for (String id : seen) { if (bounded.size() == MAX_SEEN) break; bounded.add(id); }
        if (!prefs.edit().putStringSet(key, bounded).putLong(account + ":checked", System.currentTimeMillis()).commit()) throw new IllegalStateException("Notification baseline could not be saved");
    }
}
