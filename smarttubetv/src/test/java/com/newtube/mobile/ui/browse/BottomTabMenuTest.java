package com.newtube.mobile.ui.browse;

import static org.junit.Assert.*;
import android.app.Activity;
import android.app.Application;
import android.view.View;
import androidx.appcompat.widget.PopupMenu;
import com.liskovsoft.smartyoutubetv2.tv.R;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class BottomTabMenuTest {
    @Test public void feedMenuContainsOnlyHideAndSettingsAndInvokesTheChosenAction() {
        View anchor = anchor();
        AtomicInteger hidden = new AtomicInteger();
        AtomicInteger settings = new AtomicInteger();
        PopupMenu popup = BottomTabMenu.show(anchor, hidden::incrementAndGet, settings::incrementAndGet);
        assertEquals(2, popup.getMenu().size());
        assertEquals(0, hidden.get());
        assertEquals(0, settings.get());
        popup.getMenu().performIdentifierAction(BottomTabMenu.HIDE, 0);
        assertEquals(1, hidden.get());
        assertEquals(0, settings.get());
        popup = BottomTabMenu.show(anchor, hidden::incrementAndGet, settings::incrementAndGet);
        popup.getMenu().performIdentifierAction(BottomTabMenu.SETTINGS, 0);
        assertEquals(1, hidden.get());
        assertEquals(1, settings.get());
    }

    @Test public void accountOffersSettingsWithoutHideAndLongPressDoesNotSwitchAccounts() {
        View anchor = anchor();
        AtomicInteger switches = new AtomicInteger();
        AtomicInteger settings = new AtomicInteger();
        anchor.setOnClickListener(view -> switches.incrementAndGet());
        BottomTabMenu.bind(anchor, null, settings::incrementAndGet);
        assertTrue(anchor.performLongClick());
        assertEquals(0, switches.get());
        assertEquals(0, settings.get());
        PopupMenu popup = BottomTabMenu.show(anchor, null, settings::incrementAndGet);
        assertEquals(1, popup.getMenu().size());
        assertNull(popup.getMenu().findItem(BottomTabMenu.HIDE));
        popup.getMenu().performIdentifierAction(BottomTabMenu.SETTINGS, 0);
        assertEquals(1, settings.get());
    }

    @Test public void hidingRetainsTheCurrentDestinationOrChoosesAVisibleTabThenYou() {
        assertEquals(2, BottomTabMenu.destinationAfterHide(Arrays.asList(1, 2, 3), 1, 2, 99));
        assertEquals(99, BottomTabMenu.destinationAfterHide(Arrays.asList(1, 2, 3), 1, 99, 99));
        assertEquals(1, BottomTabMenu.destinationAfterHide(Arrays.asList(1, 2, 3), 2, 2, 99));
        assertEquals(99, BottomTabMenu.destinationAfterHide(Collections.singletonList(2), 2, 2, 99));
    }

    private View anchor() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.Theme_NewTube);
        View anchor = new View(activity);
        activity.setContentView(anchor);
        anchor.layout(0, 0, 48, 48);
        return anchor;
    }
}
