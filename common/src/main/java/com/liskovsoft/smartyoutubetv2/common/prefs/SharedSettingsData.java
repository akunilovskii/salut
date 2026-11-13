package com.liskovsoft.smartyoutubetv2.common.prefs;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Separates account content from the upstream blobs that also contain app settings. */
final class SharedSettingsData {
    static final String GENERAL_DATA = "general_data";
    // Old pinned content, last playlist, playlist order, scheduled videos and feed positions.
    private static final int[] ACCOUNT_GENERAL_FIELDS = {6, 13, 28, 29, 30, 63};

    private SharedSettingsData() {
    }

    static String generalSettings(String data) {
        if (data == null) {
            return null;
        }
        String[] values = generalValues(data);
        for (int index : ACCOUNT_GENERAL_FIELDS) {
            values[index] = null;
        }
        return Helpers.mergeData((Object[]) values);
    }

    static String generalAccountState(String data) {
        String[] source = generalValues(data);
        String[] values = new String[source.length];
        for (int index : ACCOUNT_GENERAL_FIELDS) {
            values[index] = source[index];
        }
        return Helpers.mergeData((Object[]) values);
    }

    static String generalWithAccountState(String settings, String account) {
        String[] values = generalValues(settings);
        String[] accountValues = generalValues(account);
        for (int index : ACCOUNT_GENERAL_FIELDS) {
            values[index] = accountValues[index];
        }
        return Helpers.mergeData((Object[]) values);
    }

    static String sidebarSettings(String data) {
        if (data == null) {
            return null;
        }
        return sidebarWithItems(data, sidebarItems(data, true));
    }

    static String sidebarWithAccountContent(String settings, String account) {
        if (settings == null) {
            return account;
        }
        List<Video> items = sidebarItems(settings, true);
        items.addAll(sidebarItems(account, false));
        return sidebarWithItems(settings, items);
    }

    private static String[] generalValues(String data) {
        String[] values = Helpers.splitData(data);
        return values == null ? new String[64] : Arrays.copyOf(values, Math.max(64, values.length));
    }

    private static List<Video> sidebarItems(String data, boolean sections) {
        List<Video> items = new ArrayList<>();
        for (Video item : Helpers.parseList(Helpers.splitData(data), 0, Video::fromString)) {
            if (item != null && (item.sectionId != -1) == sections) {
                items.add(item);
            }
        }
        return items;
    }

    private static String sidebarWithItems(String data, List<Video> items) {
        String[] source = Helpers.splitData(data);
        Object[] values = source == null ? new Object[4] : Arrays.copyOf(source, Math.max(4, source.length), Object[].class);
        values[0] = items;
        return Helpers.mergeData(values);
    }
}
