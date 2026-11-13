package com.newtube.mobile.ui.browse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.view.ContextThemeWrapper;
import android.view.Menu;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.liskovsoft.smartyoutubetv2.tv.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class BrowseNavigationTest {
    private static final int HOME = 101;
    private static final int YOU = 200;
    private static final int SUBSCRIPTIONS = 102;
    private static final int WATCH_LATER = 103;
    private MaterialButton mYou;
    private BottomNavigationView mBottom;
    private BrowseNavigation mNavigation;
    private final List<Integer> mSelections = new ArrayList<>();
    private final List<Integer> mReselections = new ArrayList<>();

    @Before
    public void setUp() {
        ContextThemeWrapper context = new ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_NewTube);
        mYou = new MaterialButton(context);
        mYou.setCheckable(true);
        mBottom = new BottomNavigationView(context);
        addBottomTabs();
        mNavigation = new BrowseNavigation(mYou, mBottom, HOME, YOU,
                id -> {
                    mSelections.add(id);
                    mNavigation.sync(id);
                }, mReselections::add);
        mNavigation.sync(HOME);
    }

    @Test
    public void homeDoesNotLeaveAnUnrelatedBottomTabChecked() {
        assertTrue(mBottom.getMenu().findItem(HOME).isChecked());
        assertFalse(mYou.isChecked());
        assertFalse(mBottom.getMenu().findItem(SUBSCRIPTIONS).isChecked());
        assertFalse(mBottom.getMenu().findItem(WATCH_LATER).isChecked());
    }

    @Test
    public void youSelectionClearsBothHomeAndThePreviousBottomTab() {
        mBottom.setSelectedItemId(WATCH_LATER);
        mYou.performClick();
        assertEquals(Arrays.asList(WATCH_LATER, YOU), mSelections);
        assertTrue(mYou.isChecked());
        assertFalse(mBottom.getMenu().findItem(HOME).isChecked());
        assertNoBottomSelection();
    }

    @Test
    public void tappingTheLastBottomTabAfterYouNavigatesInsteadOfRetappingYou() {
        mBottom.setSelectedItemId(WATCH_LATER);
        mYou.performClick();
        mBottom.setSelectedItemId(WATCH_LATER);
        assertEquals(Arrays.asList(WATCH_LATER, YOU, WATCH_LATER), mSelections);
        assertTrue(mReselections.isEmpty());
        assertTrue(mBottom.getMenu().findItem(WATCH_LATER).isChecked());
        assertFalse(mBottom.getMenu().findItem(HOME).isChecked());
    }

    @Test
    public void homeRetapKeepsItsSelectionWhileRequestingScrollOrRefresh() {
        mBottom.setSelectedItemId(HOME);
        assertEquals(Arrays.asList(HOME), mReselections);
        assertTrue(mSelections.isEmpty());
        assertTrue(mBottom.getMenu().findItem(HOME).isChecked());
        assertFalse(mYou.isChecked());
    }

    @Test
    public void bottomRetapUsesTheReselectionPathOnlyWhenActuallyActive() {
        mBottom.setSelectedItemId(SUBSCRIPTIONS);
        mBottom.setSelectedItemId(SUBSCRIPTIONS);
        assertEquals(Arrays.asList(SUBSCRIPTIONS), mSelections);
        assertEquals(Arrays.asList(SUBSCRIPTIONS), mReselections);
    }

    @Test
    public void rebuildingBottomTabsPreservesTheYouSelectionWithoutNavigation() {
        mNavigation.sync(YOU);
        mBottom.getMenu().clear();
        addBottomTabs();
        mNavigation.sync(YOU);
        assertTrue(mYou.isChecked());
        assertNoBottomSelection();
        assertTrue(mSelections.isEmpty());
        assertTrue(mReselections.isEmpty());
    }

    @Test
    public void programmaticRestorationSelectsOnlyTheActualDestination() {
        mNavigation.sync(WATCH_LATER);
        assertFalse(mBottom.getMenu().findItem(HOME).isChecked());
        assertFalse(mYou.isChecked());
        assertTrue(mBottom.getMenu().findItem(WATCH_LATER).isChecked());
        assertFalse(mBottom.getMenu().findItem(SUBSCRIPTIONS).isChecked());
        assertTrue(mSelections.isEmpty());
    }

    private void addBottomTabs() {
        mBottom.getMenu().add(Menu.NONE, 104, 0, "History");
        mBottom.getMenu().add(Menu.NONE, HOME, 1, "Home");
        mBottom.getMenu().add(Menu.NONE, WATCH_LATER, 2, "Watch later");
        mBottom.getMenu().add(Menu.NONE, SUBSCRIPTIONS, 3, "Subscriptions");
    }

    @Test public void accountActionKeepsTheCurrentFeedSelected() {
        int account = 201;
        AtomicInteger switches = new AtomicInteger();
        mBottom.getMenu().add(Menu.NONE, account, 2, "");
        mNavigation.setActionItem(account, switches::incrementAndGet);
        mNavigation.sync(WATCH_LATER);
        mBottom.setSelectedItemId(account);
        assertEquals(1, switches.get());
        assertTrue(mBottom.getMenu().findItem(WATCH_LATER).isChecked());
        assertFalse(mBottom.getMenu().findItem(account).isChecked());
        assertTrue(mSelections.isEmpty());
    }

    @Test public void accountActionStillWorksWhenItIsTheOnlyBottomItem() {
        int account = 201;
        AtomicInteger switches = new AtomicInteger();
        mBottom.getMenu().clear();
        mBottom.getMenu().add(Menu.NONE, account, 0, "");
        mNavigation.setActionItem(account, switches::incrementAndGet);
        mNavigation.sync(HOME);
        mBottom.setSelectedItemId(account);
        assertEquals(1, switches.get());
        assertFalse(mYou.isChecked());
        assertNoBottomSelection();
        assertTrue(mSelections.isEmpty());
    }

    private void assertNoBottomSelection() {
        for (int i = 0; i < mBottom.getMenu().size(); i++) {
            assertFalse(mBottom.getMenu().getItem(i).isChecked());
        }
    }
}
