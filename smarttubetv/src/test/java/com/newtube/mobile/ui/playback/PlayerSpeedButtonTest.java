package com.newtube.mobile.ui.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.widget.ImageButton;

import androidx.core.content.ContextCompat;

import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.tv.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PlayerSpeedButtonTest {
    private ImageButton button;
    private PlayerData data;
    private float speed;
    private int pickerOpens;

    @Before public void setUp() {
        button = new ImageButton(RuntimeEnvironment.getApplication());
        data = PlayerData.instance(button.getContext());
        data.setAllSpeedEnabled(true);
        speed = 1f;
        PlayerSpeedButton.bind(button, data, () -> speed, value -> {
            speed = value;
            data.setSpeed(value);
            PlayerSpeedButton.refresh(button, value);
        }, () -> pickerOpens++);
    }

    @Test public void tapTogglesTheGlobalLastSpeedWithoutChannelMemory() {
        data.setSpeed(1.75f);
        data.setSpeed(1f);
        button.performClick();
        assertEquals(1.75f, speed, 0f);
        assertTrue(button.isSelected());
        assertEquals(1.75f, data.getSpeed("another-channel"), 0f);
        assertFalse(data.isSpeedPerChannelEnabled());
        assertFalse(data.isSpeedPerVideoEnabled());

        button.performClick();
        assertEquals(1f, speed, 0f);
        assertFalse(button.isSelected());
        assertEquals(1.75f, data.getLastSpeed(), 0f);
        button.performClick();
        assertEquals(1.75f, speed, 0f);
        assertEquals(0, pickerOpens);
    }

    @Test public void longPressOpensTheExistingPickerWithoutChangingSpeed() {
        assertTrue(button.performLongClick());
        assertEquals(1, pickerOpens);
        assertEquals(1f, speed, 0f);
    }

    @Test public void firstTapOpensThePickerWhenNoCustomSpeedWasUsed() {
        button.performClick();
        assertEquals(1, pickerOpens);
        assertEquals(1f, speed, 0f);
    }

    @Test public void completeGlyphTurnsRedForCustomSpeedAndReturnsToWhiteAtNormalSpeed() {
        button.setImageResource(R.drawable.ic_player_speed);
        PlayerSpeedButton.refresh(button, 1.75f);
        assertTrue(button.getDrawable().isStateful());
        assertTrue(java.util.Arrays.toString(button.getDrawable().getState()),
                java.util.Arrays.stream(button.getDrawable().getState())
                        .anyMatch(state -> state == android.R.attr.state_selected));
        Bitmap active = render(button.getDrawable());
        assertTrue("Contour pixel: " + Integer.toHexString(active.getPixel(120, 200)),
                countColor(active, Color.rgb(255, 0, 51)) > 0);
        assertEquals(0, countColor(active, Color.WHITE));

        PlayerSpeedButton.refresh(button, 1f);
        Bitmap normal = render(button.getDrawable());
        assertEquals(0, countColor(normal, Color.rgb(255, 0, 51)));
        assertTrue(countColor(normal, Color.WHITE) > 0);
    }

    @Test public void speedContourMatchesCastVisibleHeight() {
        Bitmap speedIcon = render(ContextCompat.getDrawable(button.getContext(), R.drawable.ic_player_speed));
        Bitmap castIcon = render(ContextCompat.getDrawable(button.getContext(), R.drawable.ic_mobile_cast));
        Rect speedBounds = paintedBounds(speedIcon);
        Rect castBounds = paintedBounds(castIcon);
        assertTrue("Speed " + speedBounds + ", Cast " + castBounds,
                Math.abs(speedBounds.height() - castBounds.height()) <= 1);
        assertTrue(Math.abs(speedBounds.centerY() - castBounds.centerY()) <= 1);
    }

    private static Bitmap render(Drawable drawable) {
        Bitmap bitmap = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888);
        drawable.setBounds(0, 0, 240, 240);
        drawable.draw(new Canvas(bitmap));
        return bitmap;
    }

    private static int countColor(Bitmap bitmap, int color) {
        int count = 0;
        for (int y = 0; y < bitmap.getHeight(); y++) {
            for (int x = 0; x < bitmap.getWidth(); x++) {
                if (bitmap.getPixel(x, y) == color) {
                    count++;
                }
            }
        }
        return count;
    }

    private static Rect paintedBounds(Bitmap bitmap) {
        Rect bounds = new Rect();
        for (int y = 0; y < bitmap.getHeight(); y++) {
            for (int x = 0; x < bitmap.getWidth(); x++) {
                if (Color.alpha(bitmap.getPixel(x, y)) >= 128) {
                    bounds.union(x, y, x + 1, y + 1);
                }
            }
        }
        return bounds;
    }
}
