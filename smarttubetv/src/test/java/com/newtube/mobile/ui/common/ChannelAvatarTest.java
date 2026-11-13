package com.newtube.mobile.ui.common;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import android.app.Activity;
import android.app.Application;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.widget.ImageView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class ChannelAvatarTest {
    @Test public void missingMetadataDoesNotEraseTheSameChannelsPortrait() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ImageView view = new ImageView(activity);
        activity.setContentView(view);
        ChannelAvatar avatar = new ChannelAvatar(view);
        avatar.bind("first-channel", null);
        ColorDrawable portrait = new ColorDrawable(Color.RED);
        view.setImageDrawable(portrait);
        avatar.bind("first-channel", null);
        assertSame(portrait, view.getDrawable());
        avatar.cancel();
        activity.finish();
    }

    @Test public void switchingChannelsCannotShowThePreviousChannelsPortrait() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ImageView view = new ImageView(activity);
        activity.setContentView(view);
        ChannelAvatar avatar = new ChannelAvatar(view);
        avatar.bind("first-channel", null);
        ColorDrawable portrait = new ColorDrawable(Color.RED);
        view.setImageDrawable(portrait);
        avatar.bind("second-channel", null);
        assertNotSame(portrait, view.getDrawable());
        avatar.cancel();
        activity.finish();
    }
    @Test public void cleanupRemainsSafeAfterTheActivityIsDestroyed() {
        org.robolectric.android.controller.ActivityController<Activity> controller =
                Robolectric.buildActivity(Activity.class).setup();
        ImageView view = new ImageView(controller.get());
        controller.get().setContentView(view);
        ChannelAvatar avatar = new ChannelAvatar(view);
        avatar.bind("channel", null);
        controller.pause().stop().destroy();
        avatar.cancel();
    }
}
