package com.newtube.mobile.ui.dialog;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Device-wide destination selection, independent of YouTube accounts and message contents. */
final class AiShareTargetStore {
    private final SharedPreferences preferences;

    AiShareTargetStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences("salut_ai_share", Context.MODE_PRIVATE);
    }

    static List<ResolveInfo> available(Context context) {
        List<ResolveInfo> result = new ArrayList<>();
        Set<String> packages = new HashSet<>();
        for (ResolveInfo target : context.getPackageManager().queryIntentActivities(sendIntent(""), 0)) {
            if (target.activityInfo == null || !target.activityInfo.exported
                    || !target.activityInfo.enabled || !target.activityInfo.applicationInfo.enabled
                    || context.getPackageName().equals(target.activityInfo.packageName)) continue;
            if (packages.add(target.activityInfo.packageName)) result.add(target);
        }
        result.sort(Comparator.comparing(target -> label(context, target).toString(),
                String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    ResolveInfo restore(Context context) {
        String saved = preferences.getString("component", null);
        if (saved == null) return null;
        for (ResolveInfo target : available(context)) if (component(target).flattenToString().equals(saved)) return target;
        clear();
        return null;
    }

    void remember(ResolveInfo target) {
        preferences.edit().putString("component", component(target).flattenToString()).apply();
    }

    void clear() { preferences.edit().remove("component").apply(); }

    static CharSequence label(Context context, ResolveInfo target) {
        return target.activityInfo.applicationInfo.loadLabel(context.getPackageManager());
    }

    static ComponentName component(ResolveInfo target) {
        return new ComponentName(target.activityInfo.packageName, target.activityInfo.name);
    }

    static Intent sendIntent(String text) {
        return new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text);
    }
}
