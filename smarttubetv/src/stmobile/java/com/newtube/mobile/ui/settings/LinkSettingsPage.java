package com.newtube.mobile.ui.settings;

import android.content.Context;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.newtube.mobile.ui.common.YouTubeLinkSetup;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Instructions and read-only checks; link ownership is changed only in Android's own UI. */
final class LinkSettingsPage {
    private LinkSettingsPage() { }

    static SettingsPages.Page build(Context context) {
        Map<String, Boolean> status = YouTubeLinkSetup.linkStatus(context);
        int ready = 0;
        for (boolean selected : status.values()) if (selected) ready++;
        List<SettingsRow> rows = new ArrayList<>();
        rows.add(SettingsRow.note(context.getString(R.string.mobile_link_guide_progress, ready, status.size())));
        rows.add(SettingsRow.header(context.getString(R.string.mobile_link_guide_release)));
        if (YouTubeLinkSetup.isYouTubeInstalled(context)) {
            rows.add(SettingsRow.action(context.getString(R.string.mobile_link_guide_youtube),
                    context.getString(R.string.mobile_link_guide_youtube_help),
                    page -> YouTubeLinkSetup.openYouTubeSettings(page.requireActivity())));
        } else {
            rows.add(SettingsRow.note(context.getString(R.string.mobile_link_guide_no_youtube)));
        }
        rows.add(SettingsRow.header(context.getString(R.string.mobile_link_guide_assign)));
        rows.add(SettingsRow.action(context.getString(R.string.mobile_link_guide_salut),
                context.getString(R.string.mobile_link_guide_salut_help),
                page -> YouTubeLinkSetup.openSettings(page.requireActivity())));
        rows.add(SettingsRow.note(context.getString(R.string.mobile_link_guide_grey_help)));
        rows.add(SettingsRow.action(context.getString(R.string.mobile_link_guide_check), null, page -> {
            StringBuilder message = new StringBuilder();
            for (Map.Entry<String, Boolean> entry : YouTubeLinkSetup.linkStatus(page.requireContext()).entrySet()) {
                if (message.length() > 0) message.append("\n\n");
                message.append(entry.getKey()).append(": ").append(context.getString(entry.getValue()
                        ? R.string.mobile_link_guide_ready : R.string.mobile_link_guide_missing));
            }
            new MaterialAlertDialogBuilder(page.requireContext(), R.style.MobileAlertDialogTextActions)
                    .setTitle(R.string.mobile_link_guide_check).setMessage(message)
                    .setPositiveButton(android.R.string.ok, null).show();
            page.rebuild();
        }));
        return new SettingsPages.Page(context.getString(R.string.mobile_settings_youtube_links), rows);
    }
}
