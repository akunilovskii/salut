package com.newtube.mobile.ui.playback;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatTextView;

/** Fades overflowing text itself, preserving the surface behind it. */
public final class FadingWatchTextView extends AppCompatTextView {
    private final Paint mFade = new Paint();

    public FadingWatchTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setEllipsize(null);
        mFade.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
    }

    @Override protected void onDraw(Canvas canvas) {
        boolean overflow = getMaxLines() == 1 && getLayout() != null
                && getLayout().getLineWidth(0) > getWidth() - getCompoundPaddingLeft() - getCompoundPaddingRight();
        if (!overflow) {
            super.onDraw(canvas);
            return;
        }
        int layer = canvas.saveLayer(0, 0, getWidth(), getHeight(), null);
        super.onDraw(canvas);
        float fadeWidth = 24 * getResources().getDisplayMetrics().density;
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        float edge = rtl ? getCompoundPaddingLeft() : getWidth() - getCompoundPaddingRight();
        float start = rtl ? edge : edge - fadeWidth;
        float end = rtl ? edge + fadeWidth : edge;
        mFade.setShader(new LinearGradient(start, 0, end, 0,
                rtl ? Color.BLACK : Color.TRANSPARENT, rtl ? Color.TRANSPARENT : Color.BLACK, Shader.TileMode.CLAMP));
        canvas.drawRect(start, 0, end, getHeight(), mFade);
        canvas.restoreToCount(layer);
    }
}
