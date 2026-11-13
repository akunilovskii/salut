package com.newtube.mobile.ui.browse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.view.Menu;
import android.view.View;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.liskovsoft.smartyoutubetv2.tv.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class, qualifiers = "mdpi")
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SubscriptionsDotTest {
    private static final int SUBSCRIPTIONS = 102;
    private Activity mActivity;
    private BottomNavigationView mBar;
    private SubscriptionsDot mDot;

    @Before public void setUp() {
        mActivity = Robolectric.buildActivity(Activity.class).setup().get();
        mActivity.setTheme(R.style.Theme_NewTube);
        mBar = new BottomNavigationView(mActivity);
        mBar.getMenu().add(Menu.NONE, 101, 0, "Home").setIcon(R.drawable.ic_nav_home);
        mBar.getMenu().add(Menu.NONE, SUBSCRIPTIONS, 1, "Subscriptions").setIcon(R.drawable.ic_nav_subscriptions);
        mActivity.setContentView(mBar);
        mBar.measure(View.MeasureSpec.makeMeasureSpec(411, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(64, View.MeasureSpec.EXACTLY));
        mBar.layout(0, 0, 411, 64);
        mDot = new SubscriptionsDot();
    }

    @After public void tearDown() {
        mDot.clear();
        mActivity.finish();
    }

    @Test public void acknowledgingRemovesThePaintedDot() {
        mDot.bind(mBar, SUBSCRIPTIONS, true);
        assertTrue(redPixels() > 0);
        mDot.bind(mBar, SUBSCRIPTIONS, false);
        assertEquals(0, redPixels());
        mDot.bind(mBar, SUBSCRIPTIONS, true);
        assertTrue(redPixels() > 0);
    }

    @Test public void acknowledgedAccountsDoNotPaintADotOnInitialBinding() {
        mDot.bind(mBar, SUBSCRIPTIONS, false);
        assertEquals(0, redPixels());
    }

    private int redPixels() {
        Bitmap bitmap = Bitmap.createBitmap(411, 64, Bitmap.Config.ARGB_8888);
        mBar.draw(new Canvas(bitmap));
        View item = mBar.findViewById(SUBSCRIPTIONS);
        Rect bounds = new Rect(0, 0, item.getWidth(), item.getHeight());
        mBar.offsetDescendantRectToMyCoords(item, bounds);
        int count = 0;
        for (int y = Math.max(0, bounds.top); y < Math.min(bitmap.getHeight(), bounds.bottom); y++)
                for (int x = Math.max(0, bounds.left); x < Math.min(bitmap.getWidth(), bounds.right); x++) {
            int color = bitmap.getPixel(x, y);
            if (Color.red(color) > 210 && Color.green(color) < 90 && Color.blue(color) < 140) count++;
        }
        bitmap.recycle();
        return count;
    }
}
