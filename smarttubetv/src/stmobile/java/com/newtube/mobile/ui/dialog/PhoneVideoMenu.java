package com.newtube.mobile.ui.dialog;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.List;

/** Keeps enabled actions and their callbacks, with uncommon actions one native level deeper. */
public final class PhoneVideoMenu {
    public static final long MORE = -2;
    static final long DOWNLOAD_COVER = -3;
    static final long[] PRIMARY = {MainUIData.MENU_ITEM_PLAY_NEXT, MainUIData.MENU_ITEM_ADD_TO_PLAYLIST,
            MainUIData.MENU_ITEM_AI_SUMMARY, DOWNLOAD_COVER, MainUIData.MENU_ITEM_SHARE_LINK,
            MainUIData.MENU_ITEM_OPEN_CHANNEL, MainUIData.MENU_ITEM_NOT_INTERESTED};

    private PhoneVideoMenu() { }

    public static void prepare(Context context, Video video, AppDialogPresenter dialog) {
        if (video == null) return;
        if (!video.hasVideo()) {
            if (video.hasChannel() && !video.hasPlaylist()) {
                dialog.replacePendingCategories(channelActions(dialog.getPendingCategories()));
            }
            return;
        }
        List<OptionCategory> all = dialog.getPendingCategories();
        if (MainUIData.instance(context).isMenuItemEnabled(MainUIData.MENU_ITEM_AI_SUMMARY)) {
            OptionCategory ai = OptionCategory.singleButton(UiOptionItem.from(
                    context.getString(R.string.mobile_ai_summary), item -> {
                        dialog.closeDialog();
                        AiVideoShare.show(context, video);
                    }));
            ai.menuItemId = MainUIData.MENU_ITEM_AI_SUMMARY;
            all.add(ai);
        }
        if (com.newtube.mobile.downloads.CoverDownload.available(video)) {
            OptionCategory cover = OptionCategory.singleButton(UiOptionItem.from(
                    context.getString(R.string.mobile_download_cover), item -> {
                        dialog.closeDialog();
                        com.newtube.mobile.downloads.CoverDownload.save(context, video);
                    }));
            cover.menuItemId = DOWNLOAD_COVER;
            all.add(cover);
        }
        List<OptionCategory> main = primary(all);
        List<OptionCategory> rest = secondary(all, main);
        if (!rest.isEmpty()) {
            OptionCategory more = OptionCategory.singleButton(UiOptionItem.from(
                    context.getString(R.string.mobile_menu_more), item -> {
                        for (OptionCategory row : rest) dialog.appendCategory(row);
                        dialog.showDialog(context.getString(R.string.mobile_menu_more));
                    }));
            more.menuItemId = MORE;
            main.add(more);
        }
        dialog.replacePendingCategories(main);
    }

    static List<OptionCategory> channelActions(List<OptionCategory> all) {
        List<OptionCategory> ordered = new ArrayList<>(all);
        for (OptionCategory row : all) {
            if (row.menuItemId == MainUIData.MENU_ITEM_PIN_TO_SIDEBAR) {
                ordered.remove(row);
                ordered.add(0, row);
                break;
            }
        }
        return ordered;
    }

    static List<OptionCategory> primary(List<OptionCategory> all) {
        List<OptionCategory> result = new ArrayList<>();
        for (long id : PRIMARY) for (OptionCategory row : all) if (row.menuItemId == id) result.add(row);
        return result;
    }

    static List<OptionCategory> secondary(List<OptionCategory> all, List<OptionCategory> main) {
        List<OptionCategory> rest = new ArrayList<>(all);
        rest.removeAll(main);
        if (main.stream().anyMatch(row -> row.menuItemId == MainUIData.MENU_ITEM_ADD_TO_PLAYLIST)) {
            rest.removeIf(row -> row.menuItemId == MainUIData.MENU_ITEM_ADD_TO_NEW_PLAYLIST);
        }
        return rest;
    }

    static int icon(long id) {
        if (id == 0) return 0;
        if (id == DOWNLOAD_COVER) return R.drawable.ic_menu_download_cover;
        if (id == MORE) return R.drawable.ic_menu_more;
        if (id == MainUIData.MENU_ITEM_PLAY_NEXT || id == MainUIData.MENU_ITEM_ADD_TO_QUEUE
                || id == MainUIData.MENU_ITEM_SHOW_QUEUE) return R.drawable.ic_menu_play_next;
        if (id == MainUIData.MENU_ITEM_ADD_TO_PLAYLIST || id == MainUIData.MENU_ITEM_ADD_TO_NEW_PLAYLIST
                || id == MainUIData.MENU_ITEM_CREATE_PLAYLIST) return R.drawable.ic_menu_save;
        if (id == MainUIData.MENU_ITEM_SHARE_LINK || id == MainUIData.MENU_ITEM_SHARE_EMBED_LINK) return R.drawable.ic_menu_share;
        if (id == MainUIData.MENU_ITEM_AI_SUMMARY) return R.drawable.ic_menu_ai;
        if (id == MainUIData.MENU_ITEM_OPEN_CHANNEL || id == MainUIData.MENU_ITEM_SUBSCRIBE) return R.drawable.ic_menu_channel;
        if (id == MainUIData.MENU_ITEM_PIN_TO_SIDEBAR) return R.drawable.ic_menu_pin;
        if (id == MainUIData.MENU_ITEM_NOT_INTERESTED || id == MainUIData.MENU_ITEM_BLOCK_CHANNEL
                || id == MainUIData.MENU_ITEM_NOT_RECOMMEND_CHANNEL) return R.drawable.ic_menu_block;
        return R.drawable.ic_menu_more;
    }
}
