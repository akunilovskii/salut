package com.newtube.mobile.ui.dialog;

import static org.junit.Assert.*;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import org.junit.Test;
import java.util.Arrays;
import java.util.List;

public class PhoneVideoMenuTest {
    @Test public void frequentActionsUseTheApprovedOrderWithoutEnablingMissingActions() {
        OptionCategory share = row(MainUIData.MENU_ITEM_SHARE_LINK);
        OptionCategory next = row(MainUIData.MENU_ITEM_PLAY_NEXT);
        OptionCategory channel = row(MainUIData.MENU_ITEM_OPEN_CHANNEL);
        assertEquals(Arrays.asList(next, share, channel), PhoneVideoMenu.primary(Arrays.asList(channel, share, next)));
    }
    @Test public void uncommonAndExtensionActionsRemainReachableInMore() {
        OptionCategory pin = row(MainUIData.MENU_ITEM_PIN_TO_SIDEBAR);
        OptionCategory plugin = row(1L << 55);
        List<OptionCategory> all = Arrays.asList(pin, row(MainUIData.MENU_ITEM_PLAY_NEXT), plugin);
        assertEquals(Arrays.asList(pin, plugin), PhoneVideoMenu.secondary(all, PhoneVideoMenu.primary(all)));
    }
    @Test public void newPlaylistIsMergedIntoSaveButRemainsWhenSaveIsDisabled() {
        OptionCategory create = row(MainUIData.MENU_ITEM_ADD_TO_NEW_PLAYLIST);
        List<OptionCategory> all = Arrays.asList(create, row(MainUIData.MENU_ITEM_ADD_TO_PLAYLIST));
        assertTrue(PhoneVideoMenu.secondary(all, PhoneVideoMenu.primary(all)).isEmpty());
        assertEquals(Arrays.asList(create), PhoneVideoMenu.secondary(Arrays.asList(create), Arrays.asList()));
    }
    @Test public void everyPrimaryActionHasAnIconAndGenericDialogsRemainUnchanged() {
        for (long id : PhoneVideoMenu.PRIMARY) assertNotEquals(0, PhoneVideoMenu.icon(id));
        assertEquals(0, PhoneVideoMenu.icon(0));
    }
    @Test public void channelPinComesFirstWithoutReplacingItsStateOrCallback() {
        OptionCategory pin = row(MainUIData.MENU_ITEM_PIN_TO_SIDEBAR);
        OptionCategory share = row(MainUIData.MENU_ITEM_SHARE_LINK);
        OptionCategory subscribe = row(MainUIData.MENU_ITEM_SUBSCRIBE);
        assertEquals(Arrays.asList(pin, share, subscribe),
                PhoneVideoMenu.channelActions(Arrays.asList(share, subscribe, pin)));
        assertEquals(Arrays.asList(share), PhoneVideoMenu.channelActions(Arrays.asList(share)));
    }
    @Test public void aiAndCoverOccupyThirdAndFourthPositionsWithoutDuplicatingCoverInMore() {
        OptionCategory next = row(MainUIData.MENU_ITEM_PLAY_NEXT);
        OptionCategory save = row(MainUIData.MENU_ITEM_ADD_TO_PLAYLIST);
        OptionCategory ai = row(MainUIData.MENU_ITEM_AI_SUMMARY);
        OptionCategory cover = row(PhoneVideoMenu.DOWNLOAD_COVER);
        OptionCategory share = row(MainUIData.MENU_ITEM_SHARE_LINK);
        List<OptionCategory> all = Arrays.asList(cover, share, ai, save, next);
        assertEquals(Arrays.asList(next, save, ai, cover, share), PhoneVideoMenu.primary(all));
        assertTrue(PhoneVideoMenu.secondary(all, PhoneVideoMenu.primary(all)).isEmpty());
        assertNotEquals(0, PhoneVideoMenu.icon(PhoneVideoMenu.DOWNLOAD_COVER));
    }
    private static OptionCategory row(long id) {
        OptionCategory row = OptionCategory.singleButton(UiOptionItem.from("Action"));
        row.menuItemId = id;
        return row;
    }
}
