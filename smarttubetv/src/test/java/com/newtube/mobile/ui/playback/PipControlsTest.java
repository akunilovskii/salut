package com.newtube.mobile.ui.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.app.RemoteAction;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Looper;

import com.liskovsoft.smartyoutubetv2.tv.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class PipControlsTest {
    @Test public void nativeActionsDeliverThreeDistinctPackageScopedCommandsInOrder() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        List<String> received = new ArrayList<>();
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ignored, Intent intent) {
                assertEquals(context.getPackageName(), intent.getPackage());
                received.add(intent.getAction());
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(PipControls.ACTION_PREVIOUS);
        filter.addAction(PipControls.ACTION_TOGGLE);
        filter.addAction(PipControls.ACTION_NEXT);
        context.registerReceiver(receiver, filter);
        try {
            for (RemoteAction action : PipControls.create(context, true, true, true)) {
                action.getActionIntent().send();
            }
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(Arrays.asList(PipControls.ACTION_PREVIOUS, PipControls.ACTION_TOGGLE,
                    PipControls.ACTION_NEXT), received);
        } finally {
            context.unregisterReceiver(receiver);
        }
    }

    @Test public void unavailableNeighborsKeepTheirPositionsAndAreDisabled() {
        Context context = RuntimeEnvironment.getApplication();
        List<RemoteAction> actions = PipControls.create(context, false, false, false);
        assertEquals(3, actions.size());
        assertFalse(actions.get(0).isEnabled());
        assertTrue(actions.get(1).isEnabled());
        assertFalse(actions.get(2).isEnabled());
        assertEquals(context.getString(R.string.mobile_player_previous), actions.get(0).getTitle());
        assertEquals(context.getString(R.string.mobile_player_play), actions.get(1).getTitle());
        assertEquals(context.getString(R.string.mobile_player_next), actions.get(2).getTitle());
    }

    @Test public void playPauseRefreshChangesTheGlyphWithoutChangingItsPendingIntent() {
        Context context = RuntimeEnvironment.getApplication();
        RemoteAction playing = PipControls.create(context, true, true, true).get(1);
        RemoteAction paused = PipControls.create(context, false, true, true).get(1);
        assertEquals(playing.getActionIntent(), paused.getActionIntent());
        assertEquals(R.drawable.ic_player_pause, playing.getIcon().getResId());
        assertEquals(R.drawable.ic_player_play, paused.getIcon().getResId());
        assertEquals(context.getString(R.string.mobile_player_pause), playing.getContentDescription());
        assertEquals(context.getString(R.string.mobile_player_play), paused.getContentDescription());
    }
}
