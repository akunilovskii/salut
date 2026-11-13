package com.newtube.mobile.ui.common;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.verify.domain.DomainVerificationManager;
import android.content.pm.verify.domain.DomainVerificationUserState;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.liskovsoft.smartyoutubetv2.tv.R;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import com.newtube.mobile.ui.settings.MobileSettingsActivity;
import com.newtube.mobile.ui.settings.SettingsPages;

/** Offers Android's existing domain selection once per installation/update, without changing it. */
public final class YouTubeLinkSetup {
    static final List<String> HOSTS = Arrays.asList("youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be");
    private static final String PREFERENCES = "salut_link_setup";
    private static final String REVIEWED_UPDATE = "reviewed_update";

    private YouTubeLinkSetup() { }

    public static void maybeShow(Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed() || activity.isInPictureInPictureMode()
                || !activity.hasWindowFocus()) return;
        try {
            long update = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).lastUpdateTime;
            SharedPreferences preferences = activity.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
            if (preferences.getLong(REVIEWED_UPDATE, -1) == update) return;
            if (allLinksApproved(activity)) {
                preferences.edit().putLong(REVIEWED_UPDATE, update).apply();
                return;
            }
            new MaterialAlertDialogBuilder(activity, R.style.MobileAlertDialogTextActions)
                    .setTitle(R.string.mobile_link_setup_title)
                    .setMessage(R.string.mobile_link_setup_message)
                    .setNegativeButton(R.string.mobile_link_setup_later, null)
                    .setPositiveButton(R.string.mobile_link_setup_settings, (dialog, which) -> openGuide(activity))
                    .show();
            preferences.edit().putLong(REVIEWED_UPDATE, update).apply();
        } catch (PackageManager.NameNotFoundException | android.view.WindowManager.BadTokenException ignored) {
            // Leave the review pending if the screen closes before the dialog can be shown.
        }
    }

    static boolean approved(boolean allowed, Map<String, Integer> states) {
        if (!allowed || states == null) return false;
        for (String host : HOSTS) {
            Integer state = states.get(host);
            if (state == null || (state != DomainVerificationUserState.DOMAIN_STATE_VERIFIED
                    && state != DomainVerificationUserState.DOMAIN_STATE_SELECTED)) return false;
        }
        return true;
    }

    static boolean allLinksApproved(Context context) {
        return !linkStatus(context).containsValue(false);
    }

    public static Map<String, Boolean> linkStatus(Context context) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                DomainVerificationManager manager = context.getSystemService(DomainVerificationManager.class);
                DomainVerificationUserState state = manager != null
                        ? manager.getDomainVerificationUserState(context.getPackageName()) : null;
                if (state != null) {
                    for (String host : HOSTS) {
                        Integer value = state.getHostToStateMap().get(host);
                        result.put(host, state.isLinkHandlingAllowed() && value != null
                                && (value == DomainVerificationUserState.DOMAIN_STATE_VERIFIED
                                || value == DomainVerificationUserState.DOMAIN_STATE_SELECTED));
                    }
                    return result;
                }
            } catch (PackageManager.NameNotFoundException | SecurityException ignored) {
                // Vendor builds without domain state can still report the chosen URL handler.
            }
        }
        for (String host : HOSTS) {
            boolean selected = true;
            for (String scheme : new String[] {"https", "http"}) {
                ResolveInfo match = context.getPackageManager().resolveActivity(
                        new Intent(Intent.ACTION_VIEW, Uri.parse(scheme + "://" + host + "/watch?v=dQw4w9WgXcQ"))
                                .addCategory(Intent.CATEGORY_BROWSABLE), PackageManager.MATCH_DEFAULT_ONLY);
                selected &= match != null && match.activityInfo != null
                        && context.getPackageName().equals(match.activityInfo.packageName);
            }
            result.put(host, selected);
        }
        return result;
    }

    public static void openGuide(Activity activity) {
        activity.startActivity(MobileSettingsActivity.intent(activity, SettingsPages.YOUTUBE_LINKS));
    }

    static Intent settingsIntent(Context context) {
        return packageSettingsIntent(context.getPackageName());
    }

    static Intent packageSettingsIntent(String packageName) {
        return new Intent(Build.VERSION.SDK_INT >= 31 ? Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS
                : Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + packageName));
    }

    public static boolean isYouTubeInstalled(Context context) {
        try {
            context.getPackageManager().getApplicationInfo("com.google.android.youtube", 0);
            return true;
        } catch (PackageManager.NameNotFoundException missing) {
            return false;
        }
    }

    public static void openSettings(Activity activity) { openPackageSettings(activity, activity.getPackageName()); }

    public static void openYouTubeSettings(Activity activity) { openPackageSettings(activity, "com.google.android.youtube"); }

    private static void openPackageSettings(Activity activity, String packageName) {
        try {
            activity.startActivity(packageSettingsIntent(packageName));
        } catch (ActivityNotFoundException ignored) {
            try {
                activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + packageName)));
            } catch (ActivityNotFoundException unavailable) {
                MobileSnackbar.show(activity, R.string.mobile_link_setup_unavailable);
            }
        }
    }
}
