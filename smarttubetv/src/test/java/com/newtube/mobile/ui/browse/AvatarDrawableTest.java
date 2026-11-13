package com.newtube.mobile.ui.browse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.liskovsoft.smartyoutubetv2.tv.R;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AvatarDrawableTest {
    @Test public void navigationTintDoesNotTurnTheProfilePhotoMonochrome() {
        AvatarDrawable avatar = avatar(Color.RED);
        avatar.setTint(Color.WHITE);
        avatar.setTintList(ColorStateList.valueOf(Color.WHITE));
        avatar.setTintMode(PorterDuff.Mode.SRC_IN);
        avatar.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        assertEquals(Color.RED, draw(avatar).getPixel(12, 12));
    }

    @Test public void thePhotoScalesToTheNavigationIconBounds() {
        AvatarDrawable avatar = avatar(Color.BLUE);
        Bitmap rendered = draw(avatar);
        assertEquals(Color.BLUE, rendered.getPixel(1, 1));
        assertEquals(Color.BLUE, rendered.getPixel(22, 22));
    }

    @Test public void viewOpacityStillReachesThePhoto() {
        AvatarDrawable avatar = avatar(Color.RED);
        avatar.setAlpha(128);
        assertEquals(128, Color.alpha(draw(avatar).getPixel(12, 12)));
    }

    private static AvatarDrawable avatar(int color) {
        Bitmap bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        return new AvatarDrawable(new BitmapDrawable(RuntimeEnvironment.getApplication().getResources(), bitmap));
    }

    @Test public void avatarRemainsOnTheRightWhenEveryFeedTabIsHidden() {
        ContextThemeWrapper context = new ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_NewTube);
        BottomNavigationView bar = new BottomNavigationView(context);
        bar.getMenu().add(0, 201, 0, "Account");
        BottomAccountSwitch.alignMenuToEnd(bar);
        bar.measure(View.MeasureSpec.makeMeasureSpec(411, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(56, View.MeasureSpec.EXACTLY));
        bar.layout(0, 0, 411, 56);
        View item = bar.findViewById(201);
        Rect bounds = new Rect();
        item.getDrawingRect(bounds);
        bar.offsetDescendantRectToMyCoords(item, bounds);
        assertTrue(bounds.centerX() > 411 * 3 / 4);
    }

    private static Bitmap draw(AvatarDrawable avatar) {
        Bitmap result = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888);
        avatar.setBounds(0, 0, 24, 24);
        avatar.draw(new Canvas(result));
        return result;
    }
}
