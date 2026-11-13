package com.newtube.mobile.ui.browse;

import android.view.Menu;
import android.view.View;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.view.ViewCompat;
import com.liskovsoft.smartyoutubetv2.tv.R;
import java.util.List;

/** Phone navigation actions, independent of the TV section-management menu. */
final class BottomTabMenu {
    static final int HIDE = 1;
    static final int SETTINGS = 2;

    private BottomTabMenu() { }

    static void bind(View item, Runnable hide, Runnable settings) {
        ViewCompat.setTooltipText(item, null);
        item.setOnLongClickListener(view -> {
            show(view, hide, settings);
            return true;
        });
    }

    static PopupMenu show(View anchor, Runnable hide, Runnable settings) {
        PopupMenu popup = new PopupMenu(anchor.getContext(), anchor);
        if (hide != null) popup.getMenu().add(Menu.NONE, HIDE, 0, R.string.mobile_tab_hide)
                .setIcon(R.drawable.ic_menu_block);
        popup.getMenu().add(Menu.NONE, SETTINGS, 1, R.string.header_settings)
                .setIcon(R.drawable.ic_mobile_settings);
        popup.setForceShowIcon(true);
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == HIDE && hide != null) hide.run();
            else if (item.getItemId() == SETTINGS) settings.run();
            else return false;
            return true;
        });
        popup.show();
        return popup;
    }

    static int destinationAfterHide(List<Integer> visible, int hidden, int current, int you) {
        if (current != hidden) return current;
        for (int id : visible) if (id != hidden) return id;
        return you;
    }
}
