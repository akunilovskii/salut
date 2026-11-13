package com.newtube.mobile.ui.channel;

import static org.junit.Assert.assertEquals;
import static org.robolectric.Shadows.shadowOf;
import android.app.Activity;
import android.app.Application;
import android.os.Looper;
import android.widget.TextView;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@LooperMode(LooperMode.Mode.PAUSED)
public class ChannelTitleFadeTest {
    @Test public void titleOpacityTracksScrollPositionAndStopsWithTheGesture() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        TextView title = new TextView(activity);
        activity.setContentView(title);
        android.provider.Settings.Global.putFloat(activity.getContentResolver(),
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        ChannelTitleFade fade = new ChannelTitleFade(title);
        fade.onOffset(0, 0, 48);
        assertEquals(0f, title.getAlpha(), 0f);
        fade.onOffset(-352, 400, 48);
        assertEquals(0f, title.getAlpha(), 0f);
        fade.onOffset(-376, 400, 48);
        assertEquals(0.5f, title.getAlpha(), 0.001f);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
        assertEquals("A stationary scroll position cannot finish the fade", 0.5f, title.getAlpha(), 0.001f);
        fade.onOffset(-388, 400, 48);
        assertEquals(0.75f, title.getAlpha(), 0.001f);
        fade.onOffset(-400, 400, 48);
        assertEquals(1f, title.getAlpha(), 0.001f);
        fade.onOffset(-376, 400, 48);
        assertEquals("Reverse scrolling uses the same position", 0.5f, title.getAlpha(), 0.001f);
        fade.onOffset(0, 400, 48);
        assertEquals(0f, title.getAlpha(), 0f);
        fade.onOffset(-500, 400, 48);
        assertEquals(1f, title.getAlpha(), 0f);
        fade.onOffset(-20, 40, 48);
        assertEquals("Short headers use their available scroll range", 0.5f, title.getAlpha(), 0.001f);
        fade.onOffset(-360, 400, 80);
        assertEquals("The interval follows the actual tab height", 0.5f, title.getAlpha(), 0.001f);
        activity.finish();
    }
}
