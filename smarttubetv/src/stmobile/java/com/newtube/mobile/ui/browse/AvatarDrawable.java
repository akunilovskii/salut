package com.newtube.mobile.ui.browse;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/** Keeps a circular avatar's original colors when BottomNavigationView applies its icon tint. */
final class AvatarDrawable extends Drawable {
    private final Drawable mImage;

    AvatarDrawable(Drawable image) {
        mImage = image;
    }

    @Override public void draw(Canvas canvas) { mImage.draw(canvas); }
    @Override protected void onBoundsChange(Rect bounds) { mImage.setBounds(bounds); }
    @Override public int getIntrinsicWidth() { return mImage.getIntrinsicWidth(); }
    @Override public int getIntrinsicHeight() { return mImage.getIntrinsicHeight(); }
    @Override public void setAlpha(int alpha) { mImage.setAlpha(alpha); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    @Override public void setColorFilter(ColorFilter colorFilter) { }
    @Override public void setTint(int tint) { }
    @Override public void setTintList(ColorStateList tint) { }
    @Override public void setTintMode(PorterDuff.Mode mode) { }
}
